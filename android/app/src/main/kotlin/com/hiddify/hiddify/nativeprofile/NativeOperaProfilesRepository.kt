package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject
import okhttp3.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** The registration flow follows routerich/opera-proxy. No remote scripts or shared credentials. */
internal class NativeOperaProfilesRepository(private val transport: Interceptor? = null, private val russian: Boolean = false,
    private val bootstrapDns: Dns? = null) {
    fun generate(region: String = "ALL", cancellation: NativeProfileImportCancellation? = null): String {
        require(region == "ALL" || region in NativeOperaProfiles.regions)
        cancellation?.ensureActive()
        val storedCookies = mutableListOf<Cookie>()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var stage = "register_subscriber"
        val http = NativeOperaApiTransport.builder().apply { bootstrapDns?.let { dns(it) } }
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookies.forEach { cookie ->
                        storedCookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                        storedCookies.add(cookie)
                    }
                }
                override fun loadForRequest(url: HttpUrl) = storedCookies.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
            })
            .authenticator { _, response ->
                var previous: Response? = response
                var attempts = 0
                while (previous != null) { attempts++; previous = previous.priorResponse }
                if (attempts > 2) null
                else OperaDigest.authorization(response.headers.values("WWW-Authenticate")
                    .firstOrNull { it.startsWith("Digest ", true) }.orEmpty(), response.request)?.let {
                    response.request.newBuilder().header("Authorization", it).build()
                }
            }.apply { transport?.let { addInterceptor(it) } }.build()
        fun rpc(endpoint: String, params: Map<String, String>): JsonObject {
            cancellation?.ensureActive()
            stage = endpoint
            val remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            if (remaining <= 0) throw java.net.SocketTimeoutException("Opera bootstrap deadline")
            val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder().url("https://${NativeOperaApiTransport.HOST}/v4/$endpoint")
                .header("SE-Client-Version", "Stable 114.0.5282.21").header("SE-Operating-System", "Windows")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 OPR/114.0.0.0")
                .header("Accept", "application/json").post(body).build()
            val call = http.newCall(request)
            call.timeout().timeout(minOf(remaining, 10000), TimeUnit.MILLISECONDS)
            cancellation?.attachRequest(call::cancel)
            try {
                return call.execute().use { response ->
                    if (!response.isSuccessful) throw NativeOperaUnavailableException(endpoint, "http", response.code, russian)
                    val text = response.body!!.byteStream().use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            cancellation?.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= 128 * 1024)
                            output.write(buffer, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                    val root = NativeJsonDocument.parse(text).asJsonObject
                    val status = root.getAsJsonObject("return_code")
                    require(status.size() == 1)
                    val code = status.keySet().single().toIntOrNull() ?: error("Invalid Opera status")
                    if (code != 0) throw NativeOperaUnavailableException(endpoint, "api", code, russian)
                    root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                }
            } finally { cancellation?.detachRequest() }
        }
        try {
            val anonymous = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val email = "${java.util.Base64.getEncoder().encodeToString(anonymous)}@se0316.best.vpn"
            rpc("register_subscriber", mapOf("email" to email, "password" to OperaDigest.hash("SHA-1", email).uppercase(Locale.ROOT)))
            val device = rpc("register_device", mapOf("client_type" to "se0316", "device_hash" to OperaDigest.randomHex(20), "device_name" to "Opera-Browser-Client"))
            val login = OperaDigest.hash("SHA-1", device.get("device_id").asString).uppercase(Locale.ROOT)
            val password = device.get("device_password").asString
            val discoveries = linkedMapOf<String, com.google.gson.JsonArray>()
            for (code in NativeOperaProfiles.regions.keys) {
                cancellation?.ensureActive()
                discoveries[code] = try {
                    val discovery = rpc("discover", mapOf("serial_no" to login, "requested_geo" to "\"$code\",,"))
                    discovery.getAsJsonArray("ips").also { require(it.size() > 0) }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) {
                    cancellation?.ensureActive()
                    // Registration succeeded: discovery 801/timeouts need not invalidate credentials.
                    // Use a known Opera region hostname, whose certificate the VPN core verifies.
                    NativeOperaProfiles.fallback(code)
                }
            }
            cancellation?.ensureActive()
            return NativeOperaProfiles.content(discoveries, login, password)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            cancellation?.ensureActive()
            if (error is NativeOperaUnavailableException) throw error
            val reason = when (error) {
                is java.net.UnknownHostException -> "dns"
                is java.net.SocketTimeoutException, is java.io.InterruptedIOException -> "timeout"
                is javax.net.ssl.SSLException -> "tls"
                is java.io.IOException -> "network"
                else -> "response"
            }
            // No response text, tokens, request headers or chained exceptions enter logs/UI.
            throw NativeOperaUnavailableException(stage, reason, russian = russian)
        } finally { http.connectionPool.evictAll() }
    }
}

internal class NativeOperaUnavailableException(
    val stage: String, val reason: String, val status: Int? = null, russian: Boolean = false,
) : Exception(operaFailureMessage(stage, reason, status, russian))

private fun operaFailureMessage(stage: String, reason: String, status: Int?, russian: Boolean): String {
    val step = when (stage) {
        "register_subscriber" -> if (russian) "регистрация" else "registration"
        "register_device" -> if (russian) "получение доступа" else "device access"
        else -> if (russian) "получение серверов" else "server discovery"
    }
    val detail = when (reason) {
        "http" -> "HTTP $status"
        "api" -> if (russian) "код API $status" else "API code $status"
        "dns" -> if (russian) "не удалось найти API в DNS" else "API DNS lookup failed"
        "timeout" -> if (russian) "сервер не ответил вовремя" else "request timed out"
        "tls" -> if (russian) "ошибка TLS" else "TLS error"
        "network" -> if (russian) "API недоступен в этой сети" else "API unreachable on this network"
        else -> if (russian) "некорректный ответ API" else "invalid API response"
    }
    return "Opera Proxy: $step — $detail."
}

/** Bounded Digest challenge handling; API credentials are public client identifiers from upstream. */
internal object OperaDigest {
    fun randomHex(size: Int): String = ByteArray(size).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02X".format(it) }
    fun hash(algorithm: String, text: String): String = MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun authorization(challenge: String, request: Request, cnonce: String = randomHex(16)): String? {
        if (!challenge.startsWith("Digest ", true) || challenge.length > 8192) return null
        val fields = Regex("(\\w+)\\s*=\\s*(?:\"([^\"]*)\"|([^,\\s]+))").findAll(challenge.substring(7))
            .associate { it.groupValues[1].lowercase(Locale.ROOT) to (it.groups[2]?.value ?: it.groupValues[3]) }
        val realm = fields["realm"] ?: return null
        val nonce = fields["nonce"] ?: return null
        val algorithm = fields["algorithm"] ?: "MD5"
        val session = algorithm.endsWith("-sess", true)
        val hashAlgorithm = when (algorithm.uppercase(Locale.ROOT).removeSuffix("-SESS")) {
            "MD5" -> "MD5"
            "SHA-256" -> "SHA-256"
            else -> return null
        }
        val qop = fields["qop"]?.let { if (it.split(',').any { v -> v.trim().equals("auth", true) }) "auth" else return null }
        if ((fields.values + cnonce).any { it.any { c -> c == '"' || c == '\\' || c.code < 32 || c.code == 127 } }) return null
        val uri = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
        var ha1 = hash(hashAlgorithm, "se0316:$realm:SILrMEPBmJuhomxWkfm3JalqHX2Eheg1YhlEZiMh8II")
        if (session) ha1 = hash(hashAlgorithm, "$ha1:$nonce:$cnonce")
        val ha2 = hash(hashAlgorithm, "${request.method}:$uri")
        val result = hash(hashAlgorithm, if (qop == null) "$ha1:$nonce:$ha2" else "$ha1:$nonce:00000001:$cnonce:auth:$ha2")
        return buildString {
            append("Digest username=\"se0316\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$result\", algorithm=$algorithm")
            fields["opaque"]?.let { append(", opaque=\"$it\"") }
            if (qop != null) append(", qop=auth, nc=00000001, cnonce=\"$cnonce\"")
            else if (session) append(", cnonce=\"$cnonce\"")
        }
    }
}
