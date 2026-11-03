package com.hiddify.hiddify.nativeconnection

import com.hiddify.hiddify.nativecore.NativeOutboundStatus

/** Pure display policy: a core that is started does not guarantee that a chosen route works. */
enum class NativeConnectionWarning {
    NONE,
    SELECTED_SERVER_TIMED_OUT,
    INTERNET_PROBE_FAILED,
    INTERNET_PROBE_INCONCLUSIVE,
}

object NativeConnectionPathPolicy {
    /** Never display a route failure before a real measurement or when VPN is stopped. */
    fun warning(
        vpnStarted: Boolean,
        selectedServerDelayMs: Int?,
        health: NativeInternetHealth,
        selectedServerStatus: NativeOutboundStatus? = NativeOutboundStatus.READY,
    ): NativeConnectionWarning {
        if (!vpnStarted) return NativeConnectionWarning.NONE
        if (selectedServerStatus == NativeOutboundStatus.FAILED ||
            (selectedServerStatus == NativeOutboundStatus.READY && selectedServerDelayMs != null &&
                (selectedServerDelayMs > 65_000 || selectedServerDelayMs < 0))) {
            return NativeConnectionWarning.SELECTED_SERVER_TIMED_OUT
        }
        if (health == NativeInternetHealth.UNAVAILABLE) {
            return NativeConnectionWarning.INTERNET_PROBE_FAILED
        }
        if (health == NativeInternetHealth.PROBE_FAILED) {
            return NativeConnectionWarning.INTERNET_PROBE_INCONCLUSIVE
        }
        return NativeConnectionWarning.NONE
    }
}
