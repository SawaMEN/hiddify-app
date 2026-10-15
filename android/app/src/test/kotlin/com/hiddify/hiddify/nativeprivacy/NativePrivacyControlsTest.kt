package com.hiddify.hiddify.nativeprivacy

import com.hiddify.hiddify.privacy.NativeRegionalMode
import org.junit.Assert.*
import org.junit.Test

class NativePrivacyControlsTest {
    @Test fun rootCanAlwaysBeTurnedOffWhileStoppedEvenIfRootOrHelperDisappears() {
        for (detected in listOf(false, true)) for (helper in listOf(false, true)) {
            assertTrue(NativePrivacyControls.canToggleRoot(false, true, true, detected, helper))
            assertEquals(detected && helper, NativePrivacyControls.canToggleRoot(false, true, false, detected, helper))
        }
    }

    @Test fun rootCannotChangeDuringSavingOrWhileTheServiceIsRunning() {
        for (requested in listOf(false, true)) {
            assertFalse(NativePrivacyControls.canToggleRoot(true, true, requested, true, true))
            assertFalse(NativePrivacyControls.canToggleRoot(false, false, requested, true, true))
        }
    }

    @Test fun editingIsBlockedThroughoutStartupSavingReconnectAndRecovery() {
        assertTrue(NativePrivacyControls.canEdit(false, false, false, false, false))
        for (blocked in 0..4) {
            val state = (0..4).map { it == blocked }
            assertFalse(NativePrivacyControls.canEdit(state[0], state[1], state[2], state[3], state[4]))
        }
    }

    @Test fun fullTunnelDoesNotDisplayAMaskedAppOrCommunityModeAsActive() {
        for (mode in NativeRegionalMode.entries) for (community in listOf(false, true)) {
            assertNull(NativePrivacyControls.selectedRoutingMode(mode, community, true))
            assertEquals(if (community) NativeRegionalMode.OFF else mode.takeUnless { it == NativeRegionalMode.OFF },
                NativePrivacyControls.selectedRoutingMode(mode, community, false))
        }
    }

    @Test fun enablingRootOrFullTunnelSelectsVpnAndDisablingPreservesServiceMode() {
        for ((key, values) in listOf(
            "flutter.privacy-use-root" to NativePrivacyControls.rootPreferences(true),
            "flutter.privacy-full-tunnel" to NativePrivacyControls.fullTunnelPreferences(true))) {
            assertEquals(mapOf<String, Any>(key to true, "flutter.service-mode" to "vpn"), values)
        }
        assertEquals(mapOf("flutter.privacy-use-root" to false), NativePrivacyControls.rootPreferences(false))
        assertEquals(mapOf("flutter.privacy-full-tunnel" to false), NativePrivacyControls.fullTunnelPreferences(false))
    }
}
