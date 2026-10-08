package com.hiddify.hiddify.nativecore

import android.content.Context
import org.json.JSONObject

class NativeDnsOptionsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(): NativeDnsOptions {
        val values = preferences.all
        val root = root(values)
        fun text(key: String, fallback: String): String = (root.opt(key) as? String)
            ?: (values["flutter.$key"] as? String) ?: fallback
        fun strategy(key: String) = text(key, "").takeIf { it in NativeConfigChoices.domainStrategyChoices } ?: ""
        return NativeDnsOptions(
            remoteAddress = text("remote-dns-address", "tcp://8.8.8.8"),
            remoteStrategy = strategy("remote-dns-domain-strategy"),
            fakeDns = (root.opt("enable-fake-dns") as? Boolean) ?: (values["flutter.enable-fake-dns"] as? Boolean) ?: false,
            directAddress = text("direct-dns-address", "udp://1.1.1.1"),
            directStrategy = strategy("direct-dns-domain-strategy"),
        )
    }

    /** IO, under the native service lifecycle barrier. Preserve every unrelated option. */
    fun saveField(field: NativeDnsOptionField, input: String): NativeDnsOptions {
        val updated = field.applyTo(load(), input)
        val item = field.value(updated)
        val root = root(preferences.all)
        root.put(field.storageKey, item)
        val editor = preferences.edit()
        val key = "flutter.${field.storageKey}"
        when (item) {
            is String -> editor.putString(key, item)
            is Boolean -> editor.putBoolean(key, item)
        }
        editor.putString("config_options_json", root.toString())
        check(editor.commit()) { "Could not save DNS settings" }
        return load()
    }

    private fun root(values: Map<String, *>): JSONObject {
        val text = values["config_options_json"] as? String ?: ""
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
