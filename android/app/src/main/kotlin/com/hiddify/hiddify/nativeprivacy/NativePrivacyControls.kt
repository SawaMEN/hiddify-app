package com.hiddify.hiddify.nativeprivacy

/** State transitions shared by the privacy controls and persisted device choices. */
object NativePrivacyControls {
    fun canEdit(transitioning: Boolean, saving: Boolean, reconnecting: Boolean,
        recovering: Boolean, startPending: Boolean): Boolean =
        !transitioning && !saving && !reconnecting && !recovering && !startPending

    fun canToggleRoot(busy: Boolean, stopped: Boolean, requested: Boolean,
        detected: Boolean, helperAvailable: Boolean): Boolean =
        !busy && stopped && (requested || (detected && helperAvailable))

    fun selectedRoutingMode(mode: com.hiddify.hiddify.privacy.NativeRegionalMode,
        community: Boolean, fullTunnel: Boolean): com.hiddify.hiddify.privacy.NativeRegionalMode? = when {
        fullTunnel -> null
        community -> com.hiddify.hiddify.privacy.NativeRegionalMode.OFF
        mode == com.hiddify.hiddify.privacy.NativeRegionalMode.OFF -> null
        else -> mode
    }

    // Enabling an Android VPN-only policy must not leave it masked by proxy-only mode.
    // Disabling it preserves the user's current service mode.
    fun rootPreferences(enabled: Boolean): Map<String, Any> = vpnPreference("flutter.privacy-use-root", enabled)
    fun fullTunnelPreferences(enabled: Boolean): Map<String, Any> = vpnPreference("flutter.privacy-full-tunnel", enabled)
    private fun vpnPreference(key: String, enabled: Boolean): Map<String, Any> =
        mapOf<String, Any>(key to enabled) + if (enabled) mapOf("flutter.service-mode" to "vpn") else emptyMap()
}
