package com.hiddify.hiddify.nativecore

import android.content.Context
import org.json.JSONObject

data class NativeTunnelOptions(val ipv6Mode: String = "ipv4_only", val mtu: Int = 9000) {
    fun validated(): NativeTunnelOptions {
        require(ipv6Mode in modes) { "Invalid IPv6 mode" }
        // Preserve legacy IPv4 MTUs supported by Dart and VPNService; IPv6 requires at least 1280.
        require(mtu in (if (ipv6Mode == "ipv4_only") 576 else 1280)..65535) { "Invalid tunnel MTU" }
        return this
    }

    companion object { val modes = listOf("ipv4_only", "prefer_ipv4", "prefer_ipv6", "ipv6_only") }
}

class NativeTunnelOptionsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)

    fun load(): NativeTunnelOptions {
        val values = preferences.all
        val root = root(values)
        val stored = (root.opt("ipv6-mode") as? String) ?: (values["flutter.ipv6-mode"] as? String) ?: "ipv4_only"
        // The previous native two-state switch wrote 'auto' when enabling IPv6.
        val mode = if (stored == "auto") "prefer_ipv4" else stored.takeIf { it in NativeTunnelOptions.modes } ?: "ipv4_only"
        val mtu = ((root.opt("mtu") as? Number) ?: (values["flutter.mtu"] as? Number))?.toLong()
            ?.takeIf { it in 576L..65535L }?.toInt() ?: 9000
        return NativeTunnelOptions(mode, mtu.coerceAtLeast(if (mode == "ipv4_only") 576 else 1280))
    }

    fun save(input: NativeTunnelOptions): NativeTunnelOptions {
        val value = input.validated()
        val root = root(preferences.all)
        root.put("ipv6-mode", value.ipv6Mode)
        root.put("mtu", value.mtu)
        check(preferences.edit().putString("config_options_json", root.toString())
            .putString("flutter.ipv6-mode", value.ipv6Mode).putLong("flutter.mtu", value.mtu.toLong()).commit()) {
            "Could not save tunnel settings"
        }
        return load()
    }

    private fun root(values: Map<String, *>): JSONObject {
        val text = values["config_options_json"] as? String ?: ""
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }
}
