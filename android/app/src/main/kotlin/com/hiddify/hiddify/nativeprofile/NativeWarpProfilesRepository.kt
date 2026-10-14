package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** Registration is completed before import/VPN startup, outside the core's TUN bootstrap. */
internal class NativeWarpProfilesRepository(
    private val http: OkHttpClient = defaultHttp,
    private val requests: List<Request> = defaultRequests,
    private val registrationUrl: String = "https://api.cloudflareclient.com/v0i1909051800/reg",
) {
    fun <T> generate(cancellation: NativeProfileImportCancellation, mode: NativeWarpMode = NativeWarpMode.GENERATED_AWG,
        allowBackup: Boolean = false, prepare: (String) -> T): T {
        val failures = mutableListOf<String>()
        if (mode != NativeWarpMode.GENERATED_AWG) {
            val key = ByteArray(32).also { X25519.generatePrivateKey(SecureRandom(), it) }
            val publicKey = ByteArray(32).also { X25519.generatePublicKey(key, 0, it, 0) }
            val body = JsonObject().apply {
                addProperty("key", Base64.getEncoder().encodeToString(publicKey))
                addProperty("install_id", ""); addProperty("fcm_token", "")
                addProperty("tos", Instant.now().toString()); addProperty("type", "Android")
                addProperty("model", "PC"); addProperty("locale", "en_US"); addProperty("warp_enabled", true)
            }
            val request = Request.Builder().url(registrationUrl)
                .header("User-Agent", "okhttp/3.12.1").header("Accept", "application/json")
                .post(body.toString().toRequestBody(json)).build()
            try {
                val response = try { download(request, cancellation) }
                catch (error: java.io.IOException) {
                    cancellation.ensureActive()
                    // Retry the same local account key over verified IPv4/no-SNI TLS.
                    // Certificate errors are final, and non-Cloudflare/custom test URLs are untouched.
                    if (request.url.host != NativeWarpApiTransport.HOST ||
                        error is javax.net.ssl.SSLPeerUnverifiedException) throw error
                    download(request, cancellation, NativeWarpApiTransport.fallback(http))
                }
                cancellation.ensureActive()
                val account = NativeWarpAccount.parse(response, Base64.getEncoder().encodeToString(key))
                return prepare(account.content(mode))
            } catch (error: CancellationException) { throw error }
              catch (error: Exception) {
                cancellation.ensureActive()
                failures += "Cloudflare: " + reason(error)
                if (!allowBackup) throw NativeWarpGenerationException(failures.joinToString("; "))
            } finally { key.fill(0); publicKey.fill(0) }
        }
        for (request in requests) {
            cancellation.ensureActive()
            try {
                val account = NativeWarpAccount.parse(download(request, cancellation))
                cancellation.ensureActive()
                // A failed core validation also moves to the next source before persisting anything.
                return prepare(account.content(mode))
            } catch (error: CancellationException) { throw error }
              catch (error: Exception) {
                cancellation.ensureActive()
                failures += request.url.host + ": " + reason(error)
            }
        }
        throw NativeWarpGenerationException(failures.joinToString("; "))
    }

    private fun download(request: Request, cancellation: NativeProfileImportCancellation, client: OkHttpClient = http): String {
        cancellation.ensureActive()
        val call = client.newCall(request)
        cancellation.attachRequest(call::cancel)
        return try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw GeneratorHttpException(response.code)
                val body = response.body ?: error("Empty WARP response")
                body.byteStream().use(NativeProfileTransfer::readText)
            }
        } finally { cancellation.detachRequest() }
    }

    private fun reason(error: Exception): String = when (error) {
        is GeneratorHttpException -> "HTTP ${error.code}"
        is java.net.SocketTimeoutException -> "timeout"
        is java.net.UnknownHostException -> "DNS"
        is javax.net.ssl.SSLPeerUnverifiedException -> "TLS certificate"
        is javax.net.ssl.SSLHandshakeException -> "TLS handshake"
        is java.net.ConnectException -> "connection refused or blocked"
        is java.net.SocketException -> "connection reset or blocked"
        is java.io.IOException -> "network"
        else -> "invalid configuration"
    }
    private class GeneratorHttpException(val code: Int) : Exception()

    companion object {
        private val defaultHttp = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
        private val json = "application/json; charset=utf-8".toMediaType()
        // Verified live endpoints; the historical Routerich URLs return errors or HTML now.
        internal val defaultRequests = listOf(
            "https://warp-config-generator-theta.vercel.app/api/warp",
            "https://dulcet-fox-556b08.netlify.app/api/warp",
        ).map { url ->
            val origin = url.substringBefore("/api/")
            Request.Builder().url(url).header("Origin", origin).header("Referer", "$origin/")
                .header("Accept", "application/json").header("User-Agent", "Mozilla/5.0")
                .post("""{"selectedServices":[],"siteMode":"all","deviceType":"computer","endpoint":"162.159.195.1:500"}""".toRequestBody(json)).build()
        } + Request.Builder().url("https://santa-atmo.ru/warp/warp.php")
            .header("Accept", "application/json").header("User-Agent", "Mozilla/5.0").build()
    }
}

/** Sanitized provider/status summary only; never attach response bodies or credential-bearing causes. */
internal class NativeWarpGenerationException(val detail: String) : Exception("WARP registration failed: $detail")
