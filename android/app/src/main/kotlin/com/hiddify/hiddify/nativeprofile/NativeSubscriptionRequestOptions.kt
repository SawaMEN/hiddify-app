package com.hiddify.hiddify.nativeprofile

import com.google.gson.JsonObject
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/** Local overrides belong to one subscription, never to the remote response headers. */
data class NativeSubscriptionRequestOptions(val userAgent: String = "", val headers: String = "") {
    fun parsedHeaders(): Map<String, String> {
        require(headers.length <= 16384) { "Subscription headers exceed 16 KiB" }
        val result = linkedMapOf<String, String>()
        for (line in headers.lines().filter { it.isNotBlank() }) {
            val separator = line.indexOf(':')
            require(separator > 0) { "Use Header-Name: value, one header per line" }
            val name = line.substring(0, separator).trim().lowercase(Locale.ROOT)
            val value = line.substring(separator + 1).trim()
            require(name.length <= 128 && name.matches(Regex("[!#$%&'*+.^_`|~0-9a-z-]+"))) { "Invalid subscription header name" }
            require(name !in reserved && name !in result) { "Duplicate or reserved subscription header" }
            require(value.length <= 4096 && value.all { it == '\t' || it.code in 32..126 }) { "Invalid subscription header value" }
            result[name] = value
        }
        require(result.size <= 32) { "Too many subscription headers" }
        return result
    }

    fun validated(): NativeSubscriptionRequestOptions {
        require(userAgent.length <= 1024 && userAgent.all { it.code in 32..126 }) { "Invalid subscription User-Agent" }
        parsedHeaders()
        return copy(userAgent = userAgent.trim())
    }

    fun request(url: String): Request {
        val value = validated()
        return Request.Builder().url(url).header("User-Agent", value.userAgent.ifEmpty { DEFAULT_USER_AGENT })
            .header("Accept", "*/*").apply { value.parsedHeaders().forEach { (name, item) -> header(name, item) } }
            .get().build()
    }

    /** OkHttp follows redirects internally: strip provider identity and credentials on every other origin. */
    fun client(base: OkHttpClient, initial: Request): OkHttpClient {
        if (userAgent.isBlank() && headers.isBlank()) return base
        val names = parsedHeaders().keys + "user-agent"
        return base.newBuilder().addNetworkInterceptor(Interceptor { chain ->
            val request = chain.request()
            val sameOrigin = request.url.scheme == initial.url.scheme && request.url.host == initial.url.host && request.url.port == initial.url.port
            val outgoing = if (sameOrigin) request else request.newBuilder().apply {
                names.forEach(::removeHeader)
                header("User-Agent", DEFAULT_USER_AGENT)
                header("Accept", "*/*")
            }.build()
            chain.proceed(outgoing)
        }).build()
    }

    companion object {
        const val DEFAULT_USER_AGENT = "VetrOFF-Android"
        private val reserved = setOf("user-agent", "host", "content-length", "transfer-encoding", "connection", "proxy-authorization", "proxy-connection", "te", "trailer", "upgrade", "keep-alive")
    }
}

data class NativeProfileNetworkOptions(
    val request: NativeSubscriptionRequestOptions = NativeSubscriptionRequestOptions(),
    val tls: NativeProfileTlsOptions = NativeProfileTlsOptions(),
) {
    fun validated() = copy(request = request.validated(), tls = tls.validated())

    fun mergeOverride(previous: String?): String {
        val value = validated()
        val root = previous?.takeIf { it.isNotBlank() }?.let { NativeJsonDocument.parse(it).asJsonObject } ?: JsonObject()
        if (value.request == NativeSubscriptionRequestOptions()) root.remove("subscription-request")
        else root.add("subscription-request", JsonObject().apply {
            addProperty("user-agent", value.request.userAgent)
            addProperty("headers", value.request.headers)
        })
        if (value.tls == NativeProfileTlsOptions()) root.remove("profile-tls")
        else root.add("profile-tls", value.tls.toJson())
        return root.toString()
    }

    companion object {
        fun fromOverride(previous: String?): NativeProfileNetworkOptions {
            // Older clients ignored malformed legacy overrides; keep that default for existing profiles.
            val root = runCatching { previous?.takeIf { it.isNotBlank() }?.let { NativeJsonDocument.parse(it).asJsonObject } }.getOrNull() ?: JsonObject()
            val request = root.getAsJsonObject("subscription-request") ?: JsonObject()
            return NativeProfileNetworkOptions(
                NativeSubscriptionRequestOptions(request.get("user-agent")?.asString.orEmpty(), request.get("headers")?.asString.orEmpty()),
                NativeProfileTlsOptions.fromJson(root.getAsJsonObject("profile-tls")),
            ).validated()
        }
    }
}
