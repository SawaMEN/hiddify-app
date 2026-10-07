package com.hiddify.hiddify.nativeconnection

import kotlin.math.ceil

data class NativeConnectionOptions(
    val maskedProtocolsOnly: Boolean = false,
    val allowUdp: Boolean = false,
    val adaptiveNetwork: Boolean = false,
) {
    fun preferences(): Map<String, Boolean> = mapOf(
        "flutter.privacy-modern-protocols-only" to maskedProtocolsOnly,
        "flutter.privacy-modern-allow-udp" to allowUdp,
        "flutter.adaptive_network" to adaptiveNetwork,
    )

    fun corePolicy(): Map<String, Boolean> = mapOf(
        "privacy-modern-protocols-only" to maskedProtocolsOnly,
        "privacy-modern-allow-udp" to allowUdp,
        "adaptive-network" to adaptiveNetwork,
    )

    companion object {
        fun fromPreferences(values: Map<String, *>): NativeConnectionOptions = NativeConnectionOptions(
            values["flutter.privacy-modern-protocols-only"] == true,
            values["flutter.privacy-modern-allow-udp"] == true,
            values["flutter.adaptive_network"] == true,
        )
    }
}

enum class NativeInternetHealth { UNCHECKED, CHECKING, AVAILABLE, UNAVAILABLE }

/** Port of the compatibility UI's adaptive timing, independent of Android and wall-clock time. */
class NativeHealthProbePolicy(val adaptive: Boolean) {
    var failures: Int = 0
        private set
    private var successes = 0
    private var averageMs = 0.0

    val timeoutSeconds: Long
        get() = when {
            !adaptive -> 12L
            failures > 0 || successes < 3 -> 30L
            else -> (8.0 + ceil(averageMs * 4.0 / 1000.0)).toLong().coerceIn(12L, 30L)
        }
    val intervalSeconds: Long get() = if (adaptive && failures == 0) 60L else 30L

    fun record(healthy: Boolean, elapsedMs: Long) {
        if (!healthy) {
            failures = (failures + 1).coerceAtMost(1_000_000)
            successes = 0
            return
        }
        failures = 0
        successes = (successes + 1).coerceAtMost(1_000_000)
        val ms = elapsedMs.coerceAtLeast(0).toDouble()
        averageMs = if (averageMs == 0.0) ms else averageMs * 0.7 + ms * 0.3
    }
}
