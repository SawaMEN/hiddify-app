package com.hiddify.hiddify.nativecore

import java.util.Locale

data class NativeSystemStats(
    val memoryBytes: Long = 0,
    val goroutines: Int = 0,
    val connectionsIn: Int = 0,
    val connectionsOut: Int = 0,
    val trafficAvailable: Boolean = false,
    val speedAvailable: Boolean = false,
    val uplink: Long = 0,
    val downlink: Long = 0,
    val uplinkTotal: Long = 0,
    val downlinkTotal: Long = 0,
    val currentOutbound: String = "",
    val currentProfile: String = "",
)

/** Main-thread owned: unary RPC returns counters, not a rate. Use monotonic receipt times. */
internal class NativeTrafficRateMeter {
    private var previous: NativeSystemStats? = null
    private var previousAt = 0L

    fun reset() { previous = null; previousAt = 0L }

    fun sample(stats: NativeSystemStats, elapsedMillis: Long): NativeSystemStats {
        val unavailable = stats.copy(uplink = 0, downlink = 0, speedAvailable = false)
        if (!stats.trafficAvailable || stats.uplinkTotal < 0 || stats.downlinkTotal < 0 || elapsedMillis < 0) {
            reset()
            return unavailable.copy(trafficAvailable = false)
        }
        val before = previous
        val interval = elapsedMillis - previousAt
        previous = stats
        previousAt = elapsedMillis
        if (before == null || interval <= 0 || interval > 10_000 || before.currentProfile != stats.currentProfile ||
            stats.uplinkTotal < before.uplinkTotal || stats.downlinkTotal < before.downlinkTotal) return unavailable
        fun rate(delta: Long): Long = (delta.toDouble() * 1000.0 / interval).toLong()
        return stats.copy(
            uplink = rate(stats.uplinkTotal - before.uplinkTotal),
            downlink = rate(stats.downlinkTotal - before.downlinkTotal),
            speedAvailable = true,
        )
    }
}

internal data class NativeTrafficAmount(val value: String, val unitIndex: Int)

internal fun nativeTrafficAmount(bytes: Long, locale: Locale = Locale.getDefault()): NativeTrafficAmount {
    var value = bytes.coerceAtLeast(0).toDouble()
    var unit = 0
    while (value >= 1024 && unit < 6) { value /= 1024; unit++ }
    // Avoid displaying a rounded "1024 KiB" at the boundary.
    if (unit > 0 && value >= 1023.5 && unit < 6) { value /= 1024; unit++ }
    val format = if (unit == 0 || value >= 100) "%.0f" else "%.1f"
    return NativeTrafficAmount(String.format(locale, format, value), unit)
}
