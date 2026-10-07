package com.hiddify.hiddify.privacy

import org.junit.Assert.*
import org.junit.Test

class NativeProxyPrivacyTest {
    @Test fun defaultsMatchFlutterPrivacyPreferences() {
        assertEquals(NativeProxyPrivacy(true, true, true), NativeProxyPrivacy.fromPreferences(emptyMap<String, Any>()))
    }

    @Test fun malformedPreferenceTypesCannotSilentlyDisableRestrictions() {
        val values = mapOf("flutter.privacy-hide-local-proxy" to "false",
            "flutter.privacy-hide-clash-api" to 0, "flutter.privacy-disable-system-proxy" to null)
        assertEquals(NativeProxyPrivacy(), NativeProxyPrivacy.fromPreferences(values))
    }

    @Test fun everyCombinationRoundTripsThroughExistingFlutterKeys() {
        for (local in listOf(false, true)) for (clash in listOf(false, true)) for (system in listOf(false, true)) {
            val expected = NativeProxyPrivacy(local, clash, system)
            assertEquals(expected, NativeProxyPrivacy.fromPreferences(expected.preferenceValues()))
            assertEquals(mapOf("flutter.privacy-hide-local-proxy" to local, "flutter.privacy-hide-clash-api" to clash,
                "flutter.privacy-disable-system-proxy" to system), expected.preferenceValues())
        }
    }

    @Test fun writesOnlyProxyChoicesWithoutChangingDnsRootOrSharing() {
        val input = mapOf<String, Any>("flutter.privacy-public-dns" to false, "flutter.privacy-encrypted-dns" to false,
            "flutter.privacy-use-root" to true, "flutter.wifi-vpn-sharing" to true)
        val changes = NativeProxyPrivacy(false, false, false).preferenceValues()
        assertTrue(input.keys.none { it in changes })
        input.forEach { (key, value) -> assertEquals(value, (input + changes)[key]) }
    }

    @Test fun onlyNonRootSharingKeepsRequestedLocalListenersAvailable() {
        val hidden = NativeProxyPrivacy()
        assertTrue(hidden.effectiveHideLocalProxy(wifiSharing = false, rootMode = false))
        assertTrue(hidden.effectiveHideLocalProxy(wifiSharing = false, rootMode = true))
        assertTrue(hidden.effectiveHideLocalProxy(wifiSharing = true, rootMode = true))
        assertFalse(hidden.effectiveHideLocalProxy(wifiSharing = true, rootMode = false))
        assertTrue(hidden.hideLocalProxy)
        assertTrue(hidden.effectiveHideLocalProxy(wifiSharing = false, rootMode = false))
    }

    @Test fun explicitlyEnabledListenerRemainsEnabledInEverySharingMode() {
        val enabled = NativeProxyPrivacy(hideLocalProxy = false)
        for (wifi in listOf(false, true)) for (root in listOf(false, true)) {
            assertFalse(enabled.effectiveHideLocalProxy(wifi, root))
        }
    }

    @Test fun similarUnprefixedAndCoreKeysDoNotChangeDevicePolicy() {
        assertEquals(NativeProxyPrivacy(), NativeProxyPrivacy.fromPreferences(mapOf(
            "privacy-hide-local-proxy" to false, "enable-clash-api" to true, "set-system-proxy" to true)))
    }
}
