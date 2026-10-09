package com.hiddify.hiddify.nativecore

import org.junit.Assert.*
import org.junit.Test

class NativeAutoPingPolicyTest {
    @Test fun startsImmediatelyThenWaitsOneMinute() {
        val policy = NativeAutoPingPolicy()
        assertTrue(policy.shouldTest("profile:proxy", 0))
        // A busy operation may defer starting without consuming the attempt.
        assertTrue(policy.shouldTest("profile:proxy", 2_000))
        policy.started(2_000)
        assertFalse(policy.shouldTest("profile:proxy", 61_999))
        assertTrue(policy.shouldTest("profile:proxy", 62_000))
    }

    @Test fun serverSwitchAndReconnectDoNotReuseTheOldSchedule() {
        val policy = NativeAutoPingPolicy()
        assertTrue(policy.shouldTest("profile:proxy:first", 0))
        policy.started(0)
        assertTrue(policy.shouldTest("profile:proxy:second", 1_000))
        policy.started(1_000)
        assertFalse(policy.shouldTest("profile:proxy:second", 2_000))
        policy.reset()
        assertTrue(policy.shouldTest("profile:proxy:second", 2_000))
    }
}
