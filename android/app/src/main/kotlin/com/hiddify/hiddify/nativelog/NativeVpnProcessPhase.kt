package com.hiddify.hiddify.nativelog

/** Small, allowlisted exit-summary payload; never include profiles, addresses or errors. */
internal enum class NativeVpnProcessPhase(val token: String) {
    LOADING_CORE("loading_core"), PREPARING_PROFILE("preparing_profile"),
    SETTING_UP_CORE("setting_up_core"), APPLYING_SETTINGS("applying_settings"),
    STARTING_CORE("starting_core"), PROBING_WARP("probing_warp"),
    CONNECTED("connected"), STOPPING_CORE("stopping_core"), STOPPED("stopped");

    fun summary(): ByteArray = "vpn:$token".toByteArray(Charsets.UTF_8)

    companion object {
        fun fromSummary(bytes: ByteArray?): NativeVpnProcessPhase? {
            if (bytes == null || bytes.size > 128) return null
            val payload = bytes.toString(Charsets.UTF_8)
            return entries.firstOrNull { payload == "vpn:${it.token}" }
        }
    }
}
