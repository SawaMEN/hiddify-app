package com.hiddify.hiddify.nativecore

import android.content.Context
import org.json.JSONObject

data class NativeTlsOptions(
    val fragment: Boolean = false,
    val fragmentSize: String = "10-30",
    val fragmentSleep: String = "2-8",
    val mixedSniCase: Boolean = false,
    val padding: Boolean = false,
    val paddingSize: String = "1-1500",
) {
    fun validated(): NativeTlsOptions = copy(
        fragmentSize = normalizeRange(fragmentSize, allowEmpty = true),
        fragmentSleep = normalizeRange(fragmentSleep, allowEmpty = true),
        paddingSize = normalizeRange(paddingSize, allowEmpty = true),
    )

    companion object {
        /** Dart OptionalRange: a nonnegative integer or an ordered pair; stored empty ranges are valid. */
        fun normalizeRange(input: String, allowEmpty: Boolean = false): String {
            val text = input.trim()
            if (allowEmpty && text.isEmpty()) return ""
            require(text.length <= 21 && Regex("[0-9]+(?:-[0-9]+)?").matches(text)) { "Invalid TLS range" }
            val parts = text.split('-').map { it.toIntOrNull() ?: error("TLS range is too large") }
            require(parts.size == 1 || parts[0] <= parts[1]) { "Reversed TLS range" }
            return parts.joinToString("-")
        }
    }
}

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

    /** IO, under the stopped native service lifecycle barrier. */
    fun save(input: NativeTlsOptions): NativeTlsOptions {
        val value = input.validated()
        val root = root(preferences.all)
        val tls = tls(root)
        val editor = preferences.edit()
        fun flag(core: String, flutter: String, item: Boolean) { tls.put(core, item); editor.putBoolean("flutter.$flutter", item) }
        fun range(core: String, flutter: String, item: String) { tls.put(core, item); editor.putString("flutter.$flutter", item) }
        flag("enable-fragment", "enable-tls-fragment", value.fragment)
        range("fragment-size", "tls-fragment-size", value.fragmentSize)
        range("fragment-sleep", "tls-fragment-sleep", value.fragmentSleep)
        flag("mixed-sni-case", "enable-tls-mixed-sni-case", value.mixedSniCase)
        flag("enable-padding", "enable-tls-padding", value.padding)
        range("padding-size", "tls-padding-size", value.paddingSize)
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
