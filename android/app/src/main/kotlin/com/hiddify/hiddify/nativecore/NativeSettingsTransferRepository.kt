package com.hiddify.hiddify.nativecore

import android.content.Context
import com.hiddify.hiddify.nativeprofile.NativeJsonDocument
import org.json.JSONObject

class NativeSettingsTransferRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    private val supportedTopLevelKeys = NativeSettingsDocument.supportedKeys

    fun exportJson(includePrivate: Boolean): String {
        val root = currentRoot()
        if (!includePrivate) {
            root.remove("lan-sharing-password")
            root.optJSONObject("extra-security")
                ?.optJSONObject("warp")
                ?.remove("license-key")
            root.optJSONObject("unblocker")
                ?.optJSONObject("warp")
                ?.remove("license-key")
            for (stage in listOf("extra-security", "unblocker")) {
                root.optJSONObject(stage)?.optJSONObject("psiphon")?.remove("conduit-pairing-id")
            }
        }
        return root.toString(2)
    }

    fun importJson(input: String) {
        val incoming =
            runCatching {
                val parsed = NativeJsonDocument.parse(input)
                require(parsed.isJsonObject) { "Settings must be a JSON object" }
                JSONObject(parsed.toString())
            }
                .getOrElse { throw IllegalArgumentException("Settings must be a JSON object", it) }

        val merged = NativeSettingsDocument.merge(
            NativeJsonDocument.parse(currentRoot().toString()).asJsonObject,
            NativeJsonDocument.parse(incoming.toString()).asJsonObject,
        )
        check(preferences.edit().putString("config_options_json", merged.toString()).commit()) {
            "Could not import core settings"
        }
    }

    fun resetCoreSettings() {
        // Scoped editors fall back to legacy preferences when a JSON key is missing.
        // Clear those aliases in the same transaction so reset cannot resurrect old settings.
        val editor = preferences.edit().putString("config_options_json", "{}")
        supportedTopLevelKeys.forEach { editor.remove("flutter.$it") }
        NativeTlsOptionField.entries.forEach { editor.remove("flutter.${it.legacyKey}") }
        editor.remove("flutter.lan_sharing_password")
        check(editor.commit()) { "Could not reset core settings" }
    }

    private fun currentRoot(): JSONObject {
        val values = preferences.all
        val stored = values["config_options_json"] as? String ?: ""
        val root = JSONObject(NativeJsonDocument.parse(stored.ifBlank { "{}" }).toString())
        supportedTopLevelKeys.forEach { key ->
            if (!root.has(key)) values["flutter.$key"]?.let { root.put(key, it) }
        }
        if (!root.has("lan-sharing-password")) values["flutter.lan_sharing_password"]?.let {
            root.put("lan-sharing-password", it)
        }
        val tls = root.optJSONObject("tls-tricks") ?: JSONObject()
        NativeTlsOptionField.entries.forEach { field ->
            if (!tls.has(field.coreKey)) values["flutter.${field.legacyKey}"]?.let { tls.put(field.coreKey, it) }
        }
        if (tls.length() > 0) root.put("tls-tricks", tls)
        return root
    }
}
