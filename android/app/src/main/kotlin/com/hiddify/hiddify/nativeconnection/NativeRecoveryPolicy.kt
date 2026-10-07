package com.hiddify.hiddify.nativeconnection

enum class NativeRecoveryDecision { STOP, WAIT_NETWORK, WAIT_CORE, START }

class NativeRecoveryPolicy(initialAttempts: Int = 0) {
    var attempts: Int = initialAttempts.coerceIn(0, 8)
        private set

    fun nextDelaySeconds(adaptive: Boolean): Long? =
        (if (adaptive) listOf(5L, 10L, 20L, 40L, 60L, 90L, 120L, 180L)
            else listOf(2L, 4L, 8L, 16L, 30L)).getOrNull(attempts)

    fun recordAttempt() { attempts = (attempts + 1).coerceAtMost(8) }
    fun reset() { attempts = 0 }

    companion object {
        fun decision(desired: Boolean, enabled: Boolean, foreground: Boolean,
            permissionGranted: Boolean, operationPending: Boolean, adaptive: Boolean,
            networkAvailable: Boolean, coreOwned: Boolean): NativeRecoveryDecision = when {
            !desired || !enabled || !foreground || !permissionGranted -> NativeRecoveryDecision.STOP
            operationPending || coreOwned -> NativeRecoveryDecision.WAIT_CORE
            adaptive && !networkAvailable -> NativeRecoveryDecision.WAIT_NETWORK
            else -> NativeRecoveryDecision.START
        }
    }
}
