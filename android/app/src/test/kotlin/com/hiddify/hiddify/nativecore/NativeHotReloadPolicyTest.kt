package com.hiddify.hiddify.nativecore

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeHotReloadPolicyTest {
    @Test fun successfulReloadMustLeaveCoreStarted() {
        assertTrue(NativeHotReloadPolicy.applied(messageEmpty = true, coreStarted = true))
    }

    @Test fun anIncompatibleTunChangeRequiresManagedReconnect() {
        assertFalse(NativeHotReloadPolicy.applied(messageEmpty = false, coreStarted = true))
    }

    @Test fun stoppedOrTransitioningCoreDoesNotCountAsApplied() {
        assertFalse(NativeHotReloadPolicy.applied(messageEmpty = true, coreStarted = false))
        assertFalse(NativeHotReloadPolicy.applied(messageEmpty = false, coreStarted = false))
    }
}
