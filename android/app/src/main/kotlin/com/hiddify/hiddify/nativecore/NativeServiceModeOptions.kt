package com.hiddify.hiddify.nativecore

import com.google.gson.JsonParser

/**
 * Synchronizes the core's TUN inbound with the Android service that is actually started.
 * The core defaults enable-tun to false, regardless of Android's VPN service mode.
 */
internal object NativeServiceModeOptions {
    fun apply(settingsJson: String, vpnMode: Boolean): String {
        val root = JsonParser.parseString(settingsJson.ifBlank { "{}" }).asJsonObject.deepCopy()
        root.addProperty("enable-tun", vpnMode)
        return root.toString()
    }
}
