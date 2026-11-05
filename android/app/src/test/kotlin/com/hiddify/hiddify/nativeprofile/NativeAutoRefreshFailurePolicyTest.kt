package com.hiddify.hiddify.nativeprofile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAutoRefreshFailurePolicyTest {
    @Test fun suppressesAutomaticUpdatesOnlyAfterThreeDays() {
        val first = 1_800_000_000_000L
        val threeDays = NativeAutoRefreshFailurePolicy.SUSPEND_AFTER_MS
        assertFalse(NativeAutoRefreshFailurePolicy.isSuspended(0L, first + threeDays))
        assertFalse(NativeAutoRefreshFailurePolicy.isSuspended(first, first + threeDays - 1L))
        assertTrue(NativeAutoRefreshFailurePolicy.isSuspended(first, first + threeDays))
        assertTrue(NativeAutoRefreshFailurePolicy.isSuspended(first, first + threeDays + 1L))
        assertFalse(NativeAutoRefreshFailurePolicy.isSuspended(first, first - 1L))
    }

    @Test fun keepsInitialFailureUntilSuccessClearsIt() {
        val first = 1_800_000_000_000L
        assertEquals(first, NativeAutoRefreshFailurePolicy.firstFailureAt(0L, first))
        assertEquals(first, NativeAutoRefreshFailurePolicy.firstFailureAt(first, first + 60_000L))
        assertEquals(first + 90_000L,
            NativeAutoRefreshFailurePolicy.firstFailureAt(0L, first + 90_000L))
    }
}
