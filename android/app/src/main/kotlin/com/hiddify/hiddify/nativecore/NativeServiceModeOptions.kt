package com.hiddify.hiddify.nativecore

import com.google.gson.JsonObject
import com.hiddify.hiddify.nativeprofile.NativeJsonDocument

/**
 * Keep Android's VpnService.Builder address policy in sync with the native sing-box
 * TUN inbound. Otherwise sing-box tries to bind TCP6 to an IPv6 address Android
 * did not assign, and VPN startup fails with EADDRNOTAVAIL.
 */
internal object NativeServiceModeOptions {
    private fun root(settingsJson: String): JsonObject =
        NativeJsonDocument.parse(settingsJson.ifBlank { "{}" }).asJsonObject

    fun isIpv4Only(settingsJson: String, legacyMode: String): Boolean {
        val configured = root(settingsJson).get("ipv6-mode")
        val mode = if (configured != null && !configured.isJsonNull) configured.asString else legacyMode
        return mode == "ipv4_only"
    }

    fun withIpv6Disabled(settingsJson: String, disabled: Boolean): String = root(settingsJson).apply {
        addProperty("ipv6-mode", if (disabled) "ipv4_only" else "prefer_ipv4")
    }.toString()

    fun apply(settingsJson: String, vpnMode: Boolean, ipv4Only: Boolean = false): String {
        val effective = root(settingsJson).deepCopy()
        // Earlier native switches stored "auto", which sing-box's DomainStrategy rejects.
        if (effective.get("ipv6-mode")?.let { it.isJsonPrimitive && it.asString == "auto" } == true) {
            effective.addProperty("ipv6-mode", "prefer_ipv4")
        }
        effective.addProperty("enable-tun", vpnMode)
        // An Android VPN commonly defaults to IPv4-only. Make that effective for
        // the Go builder too, including when config_options_json omits ipv6-mode.
        if (vpnMode && ipv4Only) effective.addProperty("ipv6-mode", "ipv4_only")
        return effective.toString()
    }
}
