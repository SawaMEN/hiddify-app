package com.hiddify.hiddify.nativeconnection

import org.junit.Assert.*
import org.junit.Test

class NativeBackgroundPermissionPolicyTest {
    private fun eligible() = NativeBackgroundPermissionPolicy.shouldPrompt(
        vpnStarted = true, appResumed = true,
        notificationPermissionPending = false, batteryExempt = false,
        alreadyShown = false, dialogVisible = false,
    )

    @Test fun promptsAfterVpnStartsWhenUnrestrictedBackgroundIsNotGranted() {
        assertTrue(eligible())
    }

    @Test fun doesNotInterruptDuringNotificationPermissionOrWhilePaused() {
        assertFalse(NativeBackgroundPermissionPolicy.shouldPrompt(
            true, true, true, false, false, false,
        ))
        assertFalse(NativeBackgroundPermissionPolicy.shouldPrompt(
            true, false, false, false, false, false,
        ))
        assertFalse(NativeBackgroundPermissionPolicy.shouldPrompt(
            false, true, false, false, false, false,
        ))
    }

    @Test fun neverPromptsWhenAlreadyAllowedOrDismissedOrOpen() {
        assertFalse(NativeBackgroundPermissionPolicy.shouldPrompt(
            true, true, false, true, false, false,
        ))
        assertFalse(NativeBackgroundPermissionPolicy.shouldPrompt(
            true, true, false, false, true, false,
        ))
        assertFalse(NativeBackgroundPermissionPolicy.shouldPrompt(
            true, true, false, false, false, true,
        ))
    }
}
