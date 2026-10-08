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

    private fun allowed(previous: Boolean = true, same: Boolean = true, enabled: Boolean = true,
        desired: Boolean = true, user: Boolean = true, permission: Boolean = true,
        stopped: Boolean = false, destroyed: Boolean = false) = NativeRecoveryPolicy.canRecover(
        previous, same, enabled, desired, user, permission, stopped, destroyed)

    @Test fun establishedServiceCanRecoverWithoutAnActivity() { assertTrue(allowed()) }
    @Test fun manualStopRevokedPermissionAndDisabledRecoveryAreTerminal() {
        assertFalse(allowed(desired = false))
        assertFalse(allowed(stopped = true))
        assertFalse(allowed(permission = false))
        assertFalse(allowed(enabled = false))
        assertFalse(allowed(user = false))
    }
    @Test fun firstUseInvalidEditsAndDestroyedOwnersNeverRetry() {
        assertFalse(allowed(previous = false))
        assertFalse(allowed(same = false))
        assertFalse(allowed(destroyed = true))
    }
}
