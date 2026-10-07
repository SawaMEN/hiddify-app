package com.hiddify.hiddify.nativecore

import android.content.Context
import org.json.JSONObject
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
        require(balancer in NativeCoreOptionsRepository.balancerChoices) { "Invalid balancing strategy" }
        require(logLevel in listOf("trace", "debug", "info", "warn", "error", "fatal", "panic")) { "Invalid log level" }
        require(intervalSeconds in 1..86400 && clashPort in 1..65535) { "Invalid interval or API port" }
        val url = testUrl.trim()
        require(url.length in 1..2048 && url.none(Char::isISOControl)) { "Invalid connection test URL" }
        val uri = URI(url)
        require(uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank() &&
            (uri.port == -1 || uri.port in 1..65535)) { "Invalid connection test URL" }
        return copy(testUrl = url)
    }
}

class NativeGeneralOptionsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(): NativeGeneralOptions {
        val values = preferences.all
        val root = root(values)
        fun text(key: String, fallback: String) = (root.opt(key) as? String) ?: (values["flutter.$key"] as? String) ?: fallback
        fun flag(key: String) = (root.opt(key) as? Boolean) ?: (values["flutter.$key"] as? Boolean) ?: false
        fun number(key: String, fallback: Int, range: IntRange) =
            ((root.opt(key) as? Number) ?: (values["flutter.$key"] as? Number))?.toLong()
                ?.takeIf { it in range.first.toLong()..range.last.toLong() }?.toInt() ?: fallback
        val url = text("connection-test-url", "http://captive.apple.com/hotspot-detect.html")
        require(url.length <= 2048) { "Connection test URL is too large" }
        return NativeGeneralOptions(
            balancer = text("balancer-strategy", "round-robin").takeIf { it in NativeCoreOptionsRepository.balancerChoices } ?: "round-robin",
            resolveDestination = flag("resolve-destination"),
            logLevel = text("log-level", "warn").takeIf { it in listOf("trace", "debug", "info", "warn", "error", "fatal", "panic") } ?: "warn",
            testUrl = url, intervalSeconds = number("url-test-interval", 600, 1..86400),
            clashPort = number("clash-api-port", 16756, 1..65535), useXray = flag("use-xray-core-when-possible"),
        )
    }

    fun save(input: NativeGeneralOptions): NativeGeneralOptions {
        val value = input.validated()
        val root = root(preferences.all)
        val fields = mapOf<String, Any>(
            "balancer-strategy" to value.balancer, "resolve-destination" to value.resolveDestination,
            "log-level" to value.logLevel, "connection-test-url" to value.testUrl,
            "url-test-interval" to value.intervalSeconds, "clash-api-port" to value.clashPort,
            "use-xray-core-when-possible" to value.useXray,
        )
        val editor = preferences.edit()
        fields.forEach { (key, item) ->
            root.put(key, item)
            when (item) {
                is String -> editor.putString("flutter.$key", item)
                is Boolean -> editor.putBoolean("flutter.$key", item)
                is Int -> editor.putLong("flutter.$key", item.toLong())
            }
        }
        editor.putString("config_options_json", root.toString())
        check(editor.commit()) { "Could not save connection settings" }
        return load()
    }

    private fun root(values: Map<String, *>): JSONObject {
        val text = values["config_options_json"] as? String ?: ""
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
