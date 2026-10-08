package com.hiddify.hiddify.nativecore

import android.content.Context
import org.json.JSONObject

class NativeGeneralOptionsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(): NativeGeneralOptions {
        val values = preferences.all
        val root = root(values)
        fun text(key: String, fallback: String) = (root.opt(key) as? String) ?: (values["flutter.$key"] as? String) ?: fallback
        fun flag(key: String) = (root.opt(key) as? Boolean) ?: (values["flutter.$key"] as? Boolean) ?: false
        fun number(key: String, fallback: Int, range: IntRange) =
            NativeStoredNumbers.int(root.opt(key) ?: values["flutter.$key"], range) ?: fallback
        val url = text("connection-test-url", "http://captive.apple.com/hotspot-detect.html")
        require(url.length <= 2048) { "Connection test URL is too large" }
        return NativeGeneralOptions(
            balancer = text("balancer-strategy", "round-robin").takeIf { it in NativeConfigChoices.balancerChoices } ?: "round-robin",
            resolveDestination = flag("resolve-destination"),
            logLevel = text("log-level", "warn").takeIf { it in listOf("trace", "debug", "info", "warn", "error", "fatal", "panic") } ?: "warn",
            testUrl = url, intervalSeconds = number("url-test-interval", 600, 1..86400),
            clashPort = number("clash-api-port", 16756, 1..65535), useXray = flag("use-xray-core-when-possible"),
        )
    }

    fun saveField(field: NativeGeneralOptionField, input: String): NativeGeneralOptions {
        // Merge the edited field into a fresh snapshot, preserving imported and unknown options.
        val updated = field.applyTo(load(), input)
        val item = field.value(updated)
        val root = root(preferences.all)
        root.put(field.storageKey, item)
        val editor = preferences.edit()
        val key = "flutter.${field.storageKey}"
        when (item) {
            is String -> editor.putString(key, item)
            is Boolean -> editor.putBoolean(key, item)
            is Int -> editor.putLong(key, item.toLong())
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
