package com.hiddify.hiddify.privacy

/** Local-device choices shared by Compose, Flutter and the core startup path. */
data class NativeProxyPrivacy(
    val hideLocalProxy: Boolean = true,
    val hideClashApi: Boolean = true,
    val disableSystemProxy: Boolean = true,
) {
    fun preferenceValues(): Map<String, Boolean> = mapOf(
        HIDE_LOCAL_PROXY_KEY to hideLocalProxy,
        HIDE_CLASH_API_KEY to hideClashApi,
        DISABLE_SYSTEM_PROXY_KEY to disableSystemProxy,
    )

    fun effectiveHideLocalProxy(wifiSharing: Boolean, rootMode: Boolean): Boolean =
        hideLocalProxyForCore(hideLocalProxy, wifiSharing, rootMode)

    companion object {
        const val HIDE_LOCAL_PROXY_KEY = "flutter.privacy-hide-local-proxy"
        const val HIDE_CLASH_API_KEY = "flutter.privacy-hide-clash-api"
        const val DISABLE_SYSTEM_PROXY_KEY = "flutter.privacy-disable-system-proxy"

        fun fromPreferences(values: Map<String, *>): NativeProxyPrivacy = NativeProxyPrivacy(
            hideLocalProxy = values[HIDE_LOCAL_PROXY_KEY] as? Boolean ?: true,
            hideClashApi = values[HIDE_CLASH_API_KEY] as? Boolean ?: true,
            disableSystemProxy = values[DISABLE_SYSTEM_PROXY_KEY] as? Boolean ?: true,
        )

        // Non-root hotspot sharing needs the local listener. Keep the saved choice intact.
        fun hideLocalProxyForCore(requested: Boolean, wifiSharing: Boolean, rootMode: Boolean): Boolean =
            requested && (!wifiSharing || rootMode)
    }
}
