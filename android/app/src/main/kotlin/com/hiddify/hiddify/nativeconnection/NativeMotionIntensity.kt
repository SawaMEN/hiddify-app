package com.hiddify.hiddify.nativeconnection

import kotlin.math.ln

/**
 * Rate-to-light conversion shared by the native connection interface and JVM tests.
 * Only bytes per second are accepted; negative/absent samples never illuminate the UI.
 */
object NativeMotionIntensity {
    fun level(bytesPerSecond: Long): Float {
        if (bytesPerSecond <= 0L) return 0f
        val capped = bytesPerSecond.coerceAtMost(150_000L).toDouble()
        return (ln(1.0 + capped) / ln(150_001.0)).toFloat().coerceIn(0f, 1f)
    }
}
