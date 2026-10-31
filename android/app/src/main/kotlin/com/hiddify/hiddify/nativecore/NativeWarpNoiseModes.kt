package com.hiddify.hiddify.nativecore

/**
 * Named WARP noise modes introduced by upstream 5.3.0.
 *
 * Keep previously saved custom values selectable: a UI upgrade must never erase
 * a nonstandard mode from the user's existing configuration.
 * This list is a presentation contract, not a claim about the installed core's capabilities.
 */
object NativeWarpNoiseModes {
    val presets: List<String> = listOf(
        "m4", "random", "quic", "quic-mimic",
        "dns", "dns-cloudflare", "sip", "stun",
    )

    fun choicesFor(current: String): List<String> {
        val stored = current.trim()
        return if (stored.isEmpty() || stored in presets) presets else presets + stored
    }
}
