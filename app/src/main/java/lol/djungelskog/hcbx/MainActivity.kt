package lol.djungelskog.hcbx

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import lol.djungelskog.hcbx.data.Auth
import lol.djungelskog.hcbx.data.Settings
import lol.djungelskog.hcbx.ui.AppRoot
import lol.djungelskog.hcbx.ui.theme.HcbTheme

class MainActivity : AppCompatActivity() {
    private var locked by mutableStateOf(false)
    private var stoppedAt = 0L
    private var sharedUri by mutableStateOf<Uri?>(null)
    private var deepLink by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Settings.biometricLock) locked = true
        handle(intent)
        setContent {
            HcbTheme {
                LaunchedEffect(Settings.secureScreen) {
                    if (Settings.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                AppRoot(
                    locked = locked && Settings.biometricLock,
                    onUnlock = ::prompt,
                    sharedUri = sharedUri,
                    onShareConsumed = { sharedUri = null },
                    deepLink = deepLink,
                    onDeepLinkConsumed = { deepLink = null },
                )
            }
        }
        if (locked) prompt()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        i ?: return
        when {
            i.action == Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                sharedUri = i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
            i.data?.scheme == BuildConfig.HCB_REDIRECT_SCHEME -> lifecycleScope.launch { Auth.handleRedirect(i) }
            i.data?.host == "hcb.hackclub.com" -> deepLink = i.data
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (Settings.biometricLock && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 30_000 && !locked) {
            locked = true
            prompt()
        }
    }

    private fun prompt() {
        val auth = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) { locked = false; return }
        val p = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        p.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("Unlock HCB Expressive").setAllowedAuthenticators(auth).build()
        )
    }
}
