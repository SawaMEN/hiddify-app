package com.hiddify.hiddify.nativeprofile

/** Ready choices, not invented provider credentials or certificate names. */
internal object NativeProfileNetworkPresets {
    data class Client(val agent: String, val title: String)
    val clients = listOf(
        Client("", "Automatic"), Client("VetrOFF-Android", "VetrOFF"), Client("Hiddify", "Hiddify"),
        Client("sing-box", "sing-box"), Client("clash.meta", "Clash Meta"), Client("mihomo", "Mihomo"),
        Client("Shadowrocket", "Shadowrocket"), Client("v2rayN", "v2rayN"), Client("v2rayNG", "v2rayNG"),
        Client("NekoBox", "NekoBox"),
        Client("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36", "Chrome"),
        Client("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0", "Firefox"),
        Client("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0", "Edge"),
        Client("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15", "Safari"),
    )
    // Browser strings are compatibility templates, not claims about installed/current versions.
    val browserNames = mapOf("chrome" to "Chrome", "firefox" to "Firefox", "edge" to "Edge", "safari" to "Safari",
        "ios" to "iOS / Safari", "android" to "Android / OkHttp", "360" to "360 Browser", "qq" to "QQ Browser")
    val alpn = listOf("", "h2,http/1.1", "http/1.1", "h2")
    val delays = listOf("", "50", "100", "200", "500", "1000")

    fun withAccept(request: NativeSubscriptionRequestOptions, accept: String): NativeSubscriptionRequestOptions {
        require(accept in setOf("", "application/json", "text/plain"))
        val headers = request.parsedHeaders().toMutableMap()
        if (accept.isEmpty()) headers.remove("accept") else headers["accept"] = accept
        return request.copy(headers = encode(headers)).validated()
    }
    fun withMetadata(request: NativeSubscriptionRequestOptions, enabled: Boolean, version: String): NativeSubscriptionRequestOptions {
        val headers = request.parsedHeaders().toMutableMap()
        if (enabled) { headers["x-device-os"] = "Android"; headers["x-app-version"] = version }
        else { headers.remove("x-device-os"); headers.remove("x-app-version") }
        return request.copy(headers = encode(headers)).validated()
    }
    fun automatic(options: NativeProfileNetworkOptions) = NativeProfileNetworkOptions(
        request = withMetadata(withAccept(options.request, ""), false, "").copy(userAgent = ""),
        tls = NativeProfileTlsOptions(),
    )
    private fun encode(headers: Map<String, String>) = headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }

    fun withTarget(tls: NativeProfileTlsOptions, tag: String, targets: List<NativeProfileTlsTarget>): NativeProfileTlsOptions {
        val matching = targets.filter { tag.isEmpty() || it.tag == tag }
        val fingerprint = matching.any { it.fingerprint }
        val fragment = matching.any { it.fragment }
        val plainTcpOnly = matching.isNotEmpty() && matching.all { it.fragment }
        return tls.copy(targetTag = tag, serverName = "",
            fingerprint = tls.fingerprint.takeIf { fingerprint && (it != "off" || plainTcpOnly) }.orEmpty(),
            fragmentMode = tls.fragmentMode.takeIf { fragment }.orEmpty(),
            fallbackDelayMs = tls.fallbackDelayMs.takeIf { fragment }.orEmpty(),
            alpn = tls.alpn.takeIf { plainTcpOnly }.orEmpty(),
            minVersion = tls.minVersion.takeIf { plainTcpOnly }.orEmpty(),
            maxVersion = tls.maxVersion.takeIf { plainTcpOnly }.orEmpty(),
            sniMode = tls.sniMode.takeIf { plainTcpOnly }.orEmpty())
    }
}
