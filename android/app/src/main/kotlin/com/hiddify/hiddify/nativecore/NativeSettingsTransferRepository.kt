package com.hiddify.hiddify.nativecore

import com.hiddify.hiddify.Settings
import android.content.Context
import com.hiddify.hiddify.nativeprofile.NativeJsonDocument
import org.json.JSONObject

class NativeSettingsTransferRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    companion object {
        private val supportedTopLevelKeys =
            setOf(
                "region",
                "balancer-strategy",
                "use-xray-core-when-possible",
                "execute-config-as-is",
                "log-level",
                "resolve-destination",
                "ipv6-mode",
                "remote-dns-address",
                "remote-dns-domain-strategy",
                "direct-dns-address",
                "direct-dns-domain-strategy",
                "mixed-port",
                "tproxy-port",
                "direct-port",
                "redirect-port",
                "enable-mixed-port",
                "enable-tproxy-port",
                "enable-direct-port",
                "enable-redirect-port",
                "tun-implementation",
                "mtu",
                "strict-route",
                "connection-test-url",
                "url-test-interval",
                "enable-clash-api",
                "clash-api-port",
                "enable-tun",
                "set-system-proxy",
                "allow-connection-from-lan",
                "lan-sharing-password",
                "enable-fake-dns",
                "independent-dns-cache",
                "tls-tricks",
                "chain-status",
                "extra-security",
                "unblocker",
            )
    }

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

        val supplied = incoming.keys().asSequence().filter(supportedTopLevelKeys::contains).toList()
        require(supplied.isNotEmpty()) { "No supported settings found" }

        val merged = currentRoot()
        supplied.forEach { key ->
            if (key in setOf("tls-tricks", "extra-security", "unblocker")) {
                require(incoming.optJSONObject(key) != null) { "Invalid settings object: $key" }
            }
            merged.put(key, incoming.get(key))
        }
        Settings.configOptions = merged.toString()
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

    private fun currentRoot(): JSONObject =
        runCatching {
            Settings.configOptions.trim()
                .takeIf { it.isNotEmpty() }
                ?.let(::JSONObject)
                ?: JSONObject()
        }.getOrElse { JSONObject() }
}
