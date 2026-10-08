package com.hiddify.hiddify.nativeconnection

class NativeRecoveryPolicy(initialAttempts: Int = 0) {
    var attempts: Int = initialAttempts.coerceIn(0, 8)
        private set

    fun nextDelaySeconds(adaptive: Boolean): Long? =
        (if (adaptive) listOf(5L, 10L, 20L, 40L, 60L, 90L, 120L, 180L)
            else listOf(2L, 4L, 8L, 16L, 30L)).getOrNull(attempts)

    fun recordAttempt() { attempts = (attempts + 1).coerceAtMost(8) }
    fun reset() { attempts = 0 }

    companion object {
        /** Ownership is supplied by the VPN service, never by Activity visibility. */
        fun canRecover(previousSession: Boolean, sameConfiguration: Boolean, enabled: Boolean,
            desired: Boolean, userStarted: Boolean, permission: Boolean, stopRequested: Boolean, destroyed: Boolean): Boolean =
            previousSession && sameConfiguration && enabled && desired && userStarted && permission && !stopRequested && !destroyed
    }
}
