package com.hiddify.hiddify.nativecore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeHotReloadPolicyTest {
    @Test fun successfulReloadMayBeRunningWithoutReachableProxies() {
        for (state in listOf(2, 5, 6)) assertTrue(NativeHotReloadPolicy.applied(true, state))
    }
    @Test fun incompatibleSettingsRequireReconnectEvenIfTheOldServiceIsRunning() {
        for (state in listOf(2, 5, 6)) assertFalse(NativeHotReloadPolicy.applied(false, state))
    }
    @Test fun stoppedTransitioningAndUnknownStatesCannotConfirmAppliedSettings() {
        for (state in listOf(-1, 0, 1, 3, 4, 7, Int.MAX_VALUE)) {
            assertFalse(NativeHotReloadPolicy.applied(true, state))
            assertFalse(NativeHotReloadPolicy.applied(false, state))
        }
    }
}
