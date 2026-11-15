package lol.djungelskog.hcbx.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import lol.djungelskog.hcbx.BuildConfig
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ApiError(val code: Int, message: String) : Exception(message)

/** Thin HCB v4 client: bearer auth with refresh, JSON bodies, multipart uploads and an offline GET cache. */
object Api {
    private lateinit var app: Context
    private lateinit var cacheDir: File
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json".toMediaType()
    private val base get() = "${BuildConfig.HCB_BASE_URL}/api/v4/"

    /** True while the last GET had to be served from the offline cache. */
    var offline by mutableStateOf(false); private set

    fun init(c: Context) {
        app = c.applicationContext
        cacheDir = File(c.cacheDir, "api").apply { mkdirs() }
    }

    fun clearCache() { cacheDir.listFiles()?.forEach { it.delete() } }

    private fun url(path: String, query: Map<String, Any?>): HttpUrl {
        val b = (if (path.startsWith("http")) path else base + path.trimStart('/')).toHttpUrl().newBuilder()
        query.forEach { (k, v) -> if (v != null && v.toString().isNotEmpty()) b.addQueryParameter(k, v.toString()) }
        return b.build()
    }

    private fun cacheFile(u: HttpUrl): File {
        val h = MessageDigest.getInstance("SHA-1").digest(u.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        return File(cacheDir, h)
    }

    private suspend fun call(build: (Request.Builder) -> Request.Builder, u: HttpUrl): Pair<Int, String> = withContext(Dispatchers.IO) {
        var token = Auth.validToken() ?: throw ApiError(401, "signed out")
        for (attempt in 0..1) {
            val req = build(Request.Builder().url(u).header("Authorization", "Bearer $token").header("Accept", "application/json").header("User-Agent", "HCB-Expressive/${BuildConfig.VERSION_NAME} (Android)")).build()
            val (code, body) = http.newCall(req).execute().use { r -> r.code to r.body?.string().orEmpty() }
            if (code == 401 && attempt == 0) {
                token = Auth.validToken(force = true) ?: throw ApiError(401, "signed out")
            } else return@withContext code to body
        }
        throw ApiError(401, "unauthorized")
    }

    private fun check(code: Int, body: String): J {
        val j = J.parse(body)
        if (code in 200..299) return j
        val msg = j["messages"].list.joinToString(". ") { it.str }.ifBlank { j["error"].s ?: j["message"].s ?: "request failed ($code)" }
        throw ApiError(code, msg.replace('_', ' '))
    }

    suspend fun get(path: String, query: Map<String, Any?> = emptyMap()): J {
        val u = url(path, query)
        return try {
            val (code, body) = call({ it.get() }, u)
            if (code == 200) { runCatching { cacheFile(u).writeText(body) }; offline = false }
            check(code, body)
        } catch (e: IOException) {
            val f = cacheFile(u)
            if (f.exists()) { offline = true; J.parse(f.readText()) } else throw ApiError(0, "you appear to be offline")
        }
    }

    private suspend fun send(method: String, path: String, body: Any?, query: Map<String, Any?>): J {
        val u = url(path, query)
        val rb = (if (body == null) "{}" else toJson(body).toString()).toRequestBody(JSON)
        return try {
            val (code, text) = call({ it.method(method, rb) }, u)
            check(code, text)
        } catch (e: IOException) { throw ApiError(0, "network error: ${e.message}") }
    }

    suspend fun post(path: String, body: Any? = null, query: Map<String, Any?> = emptyMap()) = send("POST", path, body, query)
    suspend fun patch(path: String, body: Any? = null, query: Map<String, Any?> = emptyMap()) = send("PATCH", path, body, query)
    suspend fun delete(path: String, query: Map<String, Any?> = emptyMap()) = send("DELETE", path, null, query)

    /** Multipart upload. [files] maps a form field name to a content Uri. */
    suspend fun upload(path: String, files: Map<String, Uri>, fields: Map<String, String?> = emptyMap()): J {
        val u = url(path, emptyMap())
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        fields.forEach { (k, v) -> if (v != null) mb.addFormDataPart(k, v) }
        val cr = app.contentResolver
        files.forEach { (field, uri) ->
            val mime = (cr.getType(uri) ?: "application/octet-stream").toMediaType()
            val name = cr.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            } ?: "upload"
            val rb = object : RequestBody() {
                override fun contentType() = mime
                override fun writeTo(sink: BufferedSink) { cr.openInputStream(uri)!!.source().use { sink.writeAll(it) } }
            }
            mb.addFormDataPart(field, name, rb)
        }
        val body = mb.build()
        return try {
            val (code, text) = call({ it.post(body) }, u)
            check(code, text)
        } catch (e: IOException) { throw ApiError(0, "network error: ${e.message}") }
    }
}
