package com.hiddify.hiddify.nativeconnection

import org.junit.Assert.*
import org.junit.Test

class NativeRecoveryPolicyTest {
    @Test fun ordinaryAndAdaptiveBudgetsAreBounded() {
        for ((adaptive, expected) in listOf(false to listOf(2L, 4L, 8L, 16L, 30L),
            true to listOf(5L, 10L, 20L, 40L, 60L, 90L, 120L, 180L))) {
            val policy = NativeRecoveryPolicy()
            for (delay in expected) {
                assertEquals(delay, policy.nextDelaySeconds(adaptive))
                policy.recordAttempt()
            }
            assertNull(policy.nextDelaySeconds(adaptive))
        }
    }

    @Test fun observingDelaysDoesNotSpendAttempts() {
        val policy = NativeRecoveryPolicy()
        repeat(10) { assertEquals(5L, policy.nextDelaySeconds(true)) }
        assertEquals(0, policy.attempts)
        policy.recordAttempt()
        assertEquals(10L, policy.nextDelaySeconds(true))
    }

    @Test fun resetAndRestoreRetainBoundedBudgets() {
        val policy = NativeRecoveryPolicy(3)
        assertEquals(16L, policy.nextDelaySeconds(false))
        assertEquals(40L, policy.nextDelaySeconds(true))
        policy.reset()
        assertEquals(0, policy.attempts)
        assertEquals(0, NativeRecoveryPolicy(-1).attempts)
        assertNull(NativeRecoveryPolicy(100).nextDelaySeconds(true))
    }

    @Test fun changingModeCannotCreateAnUnlimitedBudget() {
        val policy = NativeRecoveryPolicy(6)
        assertNull(policy.nextDelaySeconds(false))
        assertEquals(120L, policy.nextDelaySeconds(true))
    }

    private fun decision(desired: Boolean = true, enabled: Boolean = true, foreground: Boolean = true,
        permission: Boolean = true, pending: Boolean = false, adaptive: Boolean = true,
        network: Boolean = true, owned: Boolean = false) = NativeRecoveryPolicy.decision(
        desired, enabled, foreground, permission, pending, adaptive, network, owned)

    @Test fun manualDisconnectDisabledRecoveryBackgroundAndRevokedPermissionStopRetries() {
        assertEquals(NativeRecoveryDecision.STOP, decision(desired = false))
        assertEquals(NativeRecoveryDecision.STOP, decision(enabled = false))
        assertEquals(NativeRecoveryDecision.STOP, decision(foreground = false))
        assertEquals(NativeRecoveryDecision.STOP, decision(permission = false))
        assertEquals(NativeRecoveryDecision.STOP, decision(desired = false, owned = true, network = false))
    }

    @Test fun liveCoreAndConcurrentOperationsAreNeverRestarted() {
        assertEquals(NativeRecoveryDecision.WAIT_CORE, decision(owned = true))
        assertEquals(NativeRecoveryDecision.WAIT_CORE, decision(pending = true))
        assertEquals(NativeRecoveryDecision.WAIT_CORE, decision(owned = true, network = false))
    }

    @Test fun offlineAdaptiveNetworkWaitsAndEligibleStoppedServiceCanStart() {
        assertEquals(NativeRecoveryDecision.WAIT_NETWORK, decision(network = false))
        assertEquals(NativeRecoveryDecision.START, decision())
        assertEquals(NativeRecoveryDecision.START, decision(adaptive = false, network = false))
    }
}
