package com.hiddify.hiddify.nativecore

internal data class NativeSpeedSample(val bytes: Long, val transferNanos: Long) {
    val megabitsPerSecond: Double get() = bytes.toDouble() * 8000.0 / transferNanos
    fun validated(): NativeSpeedSample {
        require(bytes in 65_536..MAX_DOWNLOAD && transferNanos > 0) { "Insufficient speed-test sample" }
        return this
    }
    companion object { const val MAX_DOWNLOAD = 5L * 1024 * 1024 }
}
