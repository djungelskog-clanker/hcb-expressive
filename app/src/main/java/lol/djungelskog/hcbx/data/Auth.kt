package lol.djungelskog.hcbx.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import lol.djungelskog.hcbx.BuildConfig
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/** OAuth 2 authorization-code + PKCE against HCB. Tokens are kept in the Keystore-backed store. */
object Auth {
    private lateinit var store: SecureStore
    private val mutex = Mutex()
    private val http = OkHttpClient()

    var signedIn by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var expiresAt = 0L

    val redirectUri get() = "${BuildConfig.HCB_REDIRECT_SCHEME}://"
    private val tokenUrl get() = "${BuildConfig.HCB_BASE_URL}/api/v4/oauth/token"

    fun init(c: Context) {
        store = SecureStore(c)
        accessToken = store.get("access")
        refreshToken = store.get("refresh")
        expiresAt = store.get("exp")?.toLongOrNull() ?: 0L
        signedIn = refreshToken != null || accessToken != null
    }

    private fun rand(n: Int): String {
        val b = ByteArray(n); SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Opens the HCB consent screen in a Custom Tab. */
    fun start(context: Context, dark: Boolean) {
        val verifier = rand(48)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val state = rand(16)
        store.put("verifier", verifier)
        store.put("state", state)
        val uri = Uri.parse("${BuildConfig.HCB_BASE_URL}/api/v4/oauth/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.HCB_CLIENT_ID)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", "read write")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("no_app_shell", "true")
            .appendQueryParameter("theme", if (dark) "dark" else "light")
            .build()
        val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
        tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tab.launchUrl(context, uri)
    }

    /** Handles the `hcb://?code=…` redirect. Returns true if the intent was an auth callback. */
    suspend fun handleRedirect(intent: Intent?): Boolean {
        val data = intent?.data ?: return false
        if (data.scheme != BuildConfig.HCB_REDIRECT_SCHEME) return false
        val code = data.getQueryParameter("code")
        val err = data.getQueryParameter("error_description") ?: data.getQueryParameter("error")
        if (code == null) { error = err ?: "sign-in was cancelled"; return true }
        if (data.getQueryParameter("state") != store.get("state")) { error = "state mismatch, try again"; return true }
        val verifier = store.get("verifier") ?: run { error = "missing verifier, try again"; return true }
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", BuildConfig.HCB_CLIENT_ID)
            .add("code_verifier", verifier)
            .build()
        error = null
        runCatching { token(form) }.onFailure { error = it.message ?: "token exchange failed" }
        return true
    }

    private suspend fun token(form: FormBody) = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(tokenUrl).post(form).build()).execute().use { r ->
            val j = J.parse(r.body?.string().orEmpty())
            if (!r.isSuccessful) throw AuthRejected(j["error_description"].s ?: j["error"].s ?: "http ${r.code}")
            accessToken = j["access_token"].str
            refreshToken = j["refresh_token"].s ?: refreshToken
            val created = j["created_at"].long ?: (System.currentTimeMillis() / 1000)
            expiresAt = (created + (j["expires_in"].long ?: 7200L)) * 1000
            store.put("access", accessToken); store.put("refresh", refreshToken); store.put("exp", expiresAt.toString())
            store.put("verifier", null); store.put("state", null)
            signedIn = true
        }
    }

    /** Returns a token that is valid for at least the next minute, refreshing if needed. */
    suspend fun validToken(force: Boolean = false): String? = mutex.withLock {
        val t = accessToken
        if (!force && t != null && System.currentTimeMillis() < expiresAt - 60_000) return t
        val rt = refreshToken ?: return t
        try {
            token(FormBody.Builder().add("grant_type", "refresh_token").add("refresh_token", rt).add("client_id", BuildConfig.HCB_CLIENT_ID).build())
        } catch (e: AuthRejected) {
            signOutLocal(); return null
        } catch (_: Exception) { /* offline: keep using the old token */ }
        accessToken
    }

    suspend fun signOut() {
        val t = accessToken
        if (t != null) withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url("${BuildConfig.HCB_BASE_URL}/api/v4/oauth/revoke")
                    .post(FormBody.Builder().add("token", t).add("client_id", BuildConfig.HCB_CLIENT_ID).build()).build()).execute().close()
            }
        }
        signOutLocal()
    }

    private fun signOutLocal() {
        accessToken = null; refreshToken = null; expiresAt = 0
        store.clear(); signedIn = false
        Api.clearCache()
    }

    class AuthRejected(msg: String) : Exception(msg)
}
