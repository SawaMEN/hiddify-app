package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject
import okhttp3.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/** The registration flow follows routerich/opera-proxy. No remote scripts or shared credentials. */
internal class NativeOperaProfilesRepository(private val transport: Interceptor? = null) {
    fun generate(region: String, cancellation: NativeProfileImportCancellation? = null): String {
        require(NativeOperaProfiles.region("builtin:opera-$region") != null)
        cancellation?.ensureActive()
        val storedCookies = mutableListOf<Cookie>()
        val http = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false)
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
                if (response.request.header("Authorization") != null) null
                else OperaDigest.authorization(response.header("WWW-Authenticate").orEmpty(), response.request)?.let {
                    response.request.newBuilder().header("Authorization", it).build()
                }
            }.apply { transport?.let { addInterceptor(it) } }.build()
        fun rpc(endpoint: String, params: Map<String, String>): JsonObject {
            cancellation?.ensureActive()
            val body = FormBody.Builder().apply { params.forEach { (k, v) -> add(k, v) } }.build()
            val request = Request.Builder().url("https://api2.sec-tunnel.com/v4/$endpoint")
                .header("SE-Client-Version", "Stable 114.0.5282.21").header("SE-Operating-System", "Windows")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 OPR/114.0.0.0")
                .header("Accept", "application/json").post(body).build()
            val call = http.newCall(request)
            cancellation?.attachRequest(call::cancel)
            try {
                return call.execute().use { response ->
                    check(response.isSuccessful)
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
                    check(status.size() == 1 && status.has("0"))
                    root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
                }
            } finally { cancellation?.detachRequest() }
        }
        try {
            val email = "${OperaDigest.randomHex(32).lowercase(Locale.ROOT)}@se0316.best.vpn"
            rpc("register_subscriber", mapOf("email" to email, "password" to OperaDigest.hash("SHA-1", email).uppercase(Locale.ROOT)))
            val device = rpc("register_device", mapOf("client_type" to "se0316", "device_hash" to OperaDigest.randomHex(20), "device_name" to "Opera-Browser-Client"))
            val login = OperaDigest.hash("SHA-1", device.get("device_id").asString).uppercase(Locale.ROOT)
            val password = device.get("device_password").asString
            val discovery = rpc("discover", mapOf("serial_no" to login, "requested_geo" to "\"$region\",,"))
            cancellation?.ensureActive()
            return NativeOperaProfiles.content(region, login, password, discovery.getAsJsonArray("ips"))
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            cancellation?.ensureActive()
            // Do not include responses, device IDs, tokens or HTTP request details in logs/UI.
            throw NativeOperaUnavailableException()
        } finally { http.connectionPool.evictAll() }
    }
}

internal class NativeOperaUnavailableException : Exception("Opera Proxy unavailable: API blocked, service changed or invalid response. Try another profile / Opera Proxy недоступен: API заблокирован, сервис изменён или ответ некорректен. Выберите другой профиль.")

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
        if (!algorithm.equals("MD5", true) && !algorithm.equals("MD5-sess", true)) return null
        val qop = fields["qop"]?.let { if (it.split(',').any { v -> v.trim().equals("auth", true) }) "auth" else return null }
        if ((fields.values + cnonce).any { it.any { c -> c == '"' || c == '\\' || c.code < 32 || c.code == 127 } }) return null
        val uri = request.url.encodedPath + (request.url.encodedQuery?.let { "?$it" } ?: "")
        var ha1 = hash("MD5", "se0316:$realm:SILrMEPBmJuhomxWkfm3JalqHX2Eheg1YhlEZiMh8II")
        if (algorithm.equals("MD5-sess", true)) ha1 = hash("MD5", "$ha1:$nonce:$cnonce")
        val ha2 = hash("MD5", "${request.method}:$uri")
        val result = hash("MD5", if (qop == null) "$ha1:$nonce:$ha2" else "$ha1:$nonce:00000001:$cnonce:auth:$ha2")
        return buildString {
            append("Digest username=\"se0316\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$uri\", response=\"$result\", algorithm=$algorithm")
            fields["opaque"]?.let { append(", opaque=\"$it\"") }
            if (qop != null) append(", qop=auth, nc=00000001, cnonce=\"$cnonce\"")
            else if (algorithm.equals("MD5-sess", true)) append(", cnonce=\"$cnonce\"")
        }
    }
}
