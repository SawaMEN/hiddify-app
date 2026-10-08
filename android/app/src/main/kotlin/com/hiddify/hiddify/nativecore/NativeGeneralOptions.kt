package com.hiddify.hiddify.nativecore

import java.net.URI

data class NativeGeneralOptions(
    val balancer: String = "round-robin",
    val resolveDestination: Boolean = false,
    val logLevel: String = "warn",
    val testUrl: String = "http://captive.apple.com/hotspot-detect.html",
    val intervalSeconds: Int = 600,
    val clashPort: Int = 16756,
    val useXray: Boolean = false,
) {
    fun validated(): NativeGeneralOptions {
        require(balancer in NativeConfigChoices.balancerChoices) { "Invalid balancing strategy" }
        require(logLevel in listOf("trace", "debug", "info", "warn", "error", "fatal", "panic")) { "Invalid log level" }
        require(intervalSeconds in 1..86400 && clashPort in 1..65535) { "Invalid interval or API port" }
        val url = testUrl.trim()
        require(url.length in 1..2048 && url.none(Char::isISOControl)) { "Invalid connection test URL" }
        val uri = URI.create(url)
        require(uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() &&
            (uri.port == -1 || uri.port in 1..65535)) { "Invalid connection test URL" }
        return copy(testUrl = url)
    }
}

enum class NativeGeneralOptionField(val storageKey: String) {
    BALANCER("balancer-strategy"), RESOLVE_DESTINATION("resolve-destination"),
    LOG_LEVEL("log-level"), TEST_URL("connection-test-url"), INTERVAL("url-test-interval"),
    CLASH_PORT("clash-api-port"), USE_XRAY("use-xray-core-when-possible");

    fun value(options: NativeGeneralOptions): Any = when (this) {
        BALANCER -> options.balancer
        RESOLVE_DESTINATION -> options.resolveDestination
        LOG_LEVEL -> options.logLevel
        TEST_URL -> options.testUrl
        INTERVAL -> options.intervalSeconds
        CLASH_PORT -> options.clashPort
        USE_XRAY -> options.useXray
    }

    fun applyTo(options: NativeGeneralOptions, input: String): NativeGeneralOptions = when (this) {
        BALANCER -> options.copy(balancer = input).also { require(input in NativeConfigChoices.balancerChoices) }
        RESOLVE_DESTINATION -> options.copy(resolveDestination = input.toBooleanStrict())
        LOG_LEVEL -> options.copy(logLevel = input).also { require(input in listOf("trace", "debug", "info", "warn", "error", "fatal", "panic")) }
        TEST_URL -> options.copy(testUrl = NativeGeneralOptions(testUrl = input).validated().testUrl)
        INTERVAL -> options.copy(intervalSeconds = input.toInt().also { require(it in 1..86400) })
        CLASH_PORT -> options.copy(clashPort = input.toInt().also { require(it in 1..65535) })
        USE_XRAY -> options.copy(useXray = input.toBooleanStrict())
    }
}
