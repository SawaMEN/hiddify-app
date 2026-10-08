package com.hiddify.hiddify.nativecore

import android.content.Context
import org.json.JSONObject

class NativeTlsOptionsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(): NativeTlsOptions {
        val values = preferences.all
        val tls = tls(root(values))
        fun flag(core: String, flutter: String) = (tls.opt(core) as? Boolean) ?: (values["flutter.$flutter"] as? Boolean) ?: false
        fun range(core: String, flutter: String, fallback: String): String {
            val text = (tls.opt(core) as? String) ?: (values["flutter.$flutter"] as? String) ?: fallback
            require(text.length <= 21) { "TLS range is too large" }
            return text
        }
        return NativeTlsOptions(
            fragment = flag("enable-fragment", "enable-tls-fragment"),
            fragmentSize = range("fragment-size", "tls-fragment-size", "10-30"),
            fragmentSleep = range("fragment-sleep", "tls-fragment-sleep", "2-8"),
            mixedSniCase = flag("mixed-sni-case", "enable-tls-mixed-sni-case"),
            padding = flag("enable-padding", "enable-tls-padding"),
            paddingSize = range("padding-size", "tls-padding-size", "1-1500"),
        )
    }

    /** IO, under the native lifecycle barrier. Merge only the edited nested TLS key. */
    fun saveField(field: NativeTlsOptionField, input: String): NativeTlsOptions {
        val updated = field.applyTo(load(), input)
        val item = field.value(updated)
        val root = root(preferences.all)
        val tls = tls(root)
        tls.put(field.coreKey, item)
        val editor = preferences.edit()
        val key = "flutter.${field.legacyKey}"
        when (item) {
            is String -> editor.putString(key, item)
            is Boolean -> editor.putBoolean(key, item)
        }
        root.put("tls-tricks", tls)
        editor.putString("config_options_json", root.toString())
        check(editor.commit()) { "Could not save TLS settings" }
        return load()
    }

    private fun root(values: Map<String, *>): JSONObject {
        val text = values["config_options_json"] as? String ?: ""
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun tls(root: JSONObject): JSONObject {
        if (!root.has("tls-tricks")) return JSONObject()
        return root.optJSONObject("tls-tricks") ?: error("Invalid TLS options object")
    }
}
