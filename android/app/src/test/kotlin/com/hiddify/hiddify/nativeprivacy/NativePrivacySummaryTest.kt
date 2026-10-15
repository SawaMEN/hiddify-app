package com.hiddify.hiddify.nativeprivacy

import com.hiddify.hiddify.privacy.NativeProxyPrivacy
import com.hiddify.hiddify.privacy.NativeRegionalMode
import com.hiddify.hiddify.privacy.NativeRegionalOptions
import org.junit.Assert.*
import org.junit.Test

class NativePrivacySummaryTest {
    private fun summary(mode: NativeRegionalMode = NativeRegionalMode.RUSSIAN_BYPASS,
        network: Boolean = false, full: Boolean = false, community: Boolean = false,
        encrypted: Boolean = false, publicDns: Boolean = false,
        proxy: NativeProxyPrivacy = NativeProxyPrivacy(false, false, false),
        adaptive: Boolean = false, masked: Boolean = false, root: Boolean = false, sharing: Boolean = false, vpn: Boolean = true, lan: Boolean = false, communityEnabled: Boolean = true) =
        NativePrivacySummary.features(NativeRegionalOptions(mode = mode, russianNetworkBypass = network),
            full, community, encrypted, publicDns, proxy, adaptive, masked, root, sharing, vpn, lan, communityEnabled)

    @Test fun appAndServiceRowsAreMutuallyExclusiveAndFollowTheSelectedMode() {
        assertEquals(listOf(NativePrivacyFeature.RUSSIAN_APPS), summary())
        assertEquals(listOf(NativePrivacyFeature.BLOCKED_SERVICES), summary(NativeRegionalMode.SELECTED_PROXY))
        assertTrue(summary(NativeRegionalMode.OFF).isEmpty())
    }

    @Test fun effectiveCommunityAndFullTunnelModesHideInactiveAppLists() {
        for (mode in NativeRegionalMode.entries) {
            assertEquals(listOf(NativePrivacyFeature.COMMUNITY_LISTS), summary(mode, community = true))
            assertEquals(listOf(NativePrivacyFeature.FULL_TUNNEL), summary(mode, full = true))
            assertEquals(listOf(NativePrivacyFeature.FULL_TUNNEL), summary(mode, full = true, community = true))
        }
    }

    @Test fun networkIsGroupedWithTheEffectiveRoutingModeWithoutDuplicateApplicationRows() {
        for (mode in NativeRegionalMode.entries) for (full in listOf(false, true)) for (community in listOf(false, true)) {
            val on = summary(mode, network = true, full = full, community = community)
            val off = summary(mode, network = false, full = full, community = community)
            assertEquals(if (off.isEmpty()) listOf(NativePrivacyFeature.RUSSIAN_APPS) else off, on)
            assertTrue(on.size <= 1)
        }
        assertEquals(listOf(NativePrivacyFeature.BLOCKED_SERVICES),
            summary(NativeRegionalMode.SELECTED_PROXY, network = true))
        assertEquals(listOf(NativePrivacyFeature.RUSSIAN_APPS),
            summary(network = true, community = true, communityEnabled = false))
    }

    @Test fun summaryReflectsIndividualDnsProxyAndConnectionSettings() {
        val enabled = summary(NativeRegionalMode.OFF, encrypted = true, publicDns = true,
            proxy = NativeProxyPrivacy(), adaptive = true, masked = true, root = true, sharing = true)
        assertEquals(setOf(NativePrivacyFeature.ENCRYPTED_DNS, NativePrivacyFeature.HIDDEN_LOCAL_PROXY,
            NativePrivacyFeature.HIDDEN_API, NativePrivacyFeature.ADAPTIVE_NETWORK,
            NativePrivacyFeature.MASKED_PROTOCOLS, NativePrivacyFeature.ROOT, NativePrivacyFeature.WIFI_SHARING), enabled.toSet())
        assertEquals(enabled.size, enabled.distinct().size)
        assertTrue(summary(NativeRegionalMode.OFF).isEmpty())
    }

    @Test fun nonRootWifiSharingDoesNotClaimTheRequiredLocalProxyIsHidden() {
        val sharing = summary(proxy = NativeProxyPrivacy(), sharing = true)
        assertFalse(NativePrivacyFeature.HIDDEN_LOCAL_PROXY in sharing)
        assertTrue(NativePrivacyFeature.WIFI_SHARING in sharing)
        assertTrue(NativePrivacyFeature.HIDDEN_LOCAL_PROXY in summary(proxy = NativeProxyPrivacy(), sharing = true, root = true))
    }
    @Test fun summaryFollowsAutoSetupAndRestoreWithoutChangingTheRoutingRows() {
        val original = mapOf<String, Any>("flutter.privacy-routing-mode" to "proxy-selected",
            "flutter.privacy-russian-network-bypass" to false, "flutter.privacy-use-root" to true,
            "flutter.privacy-encrypted-dns" to false)
        fun rows(values: Map<String, Any>) = summary(
            NativeRegionalMode.fromValue(values["flutter.privacy-routing-mode"] as String),
            network = values["flutter.privacy-russian-network-bypass"] == true,
            encrypted = values["flutter.privacy-encrypted-dns"] == true,
            root = values["flutter.privacy-use-root"] == true)
        val applied = original + NativePrivacySetup.configure(original)
        assertEquals(listOf(NativePrivacyFeature.BLOCKED_SERVICES, NativePrivacyFeature.ENCRYPTED_DNS), rows(applied))
        assertEquals(rows(original), rows(applied + NativePrivacySetup.restore(applied)))
    }

    @Test fun lanAccessTemporarilyOverridesLocalProxyHidingWithoutChangingSavedChoice() {
        val proxy = NativeProxyPrivacy()
        assertFalse(NativePrivacyFeature.HIDDEN_LOCAL_PROXY in summary(proxy = proxy, lan = true))
        assertTrue(NativePrivacyFeature.HIDDEN_LOCAL_PROXY in summary(proxy = proxy, lan = true, root = true))
        assertTrue(proxy.hideLocalProxy)
    }

    @Test fun proxyOnlyModeDoesNotClaimAndroidVpnOrRootFeaturesAreEffective() {
        val features = summary(full = true, encrypted = true, publicDns = true, proxy = NativeProxyPrivacy(),
            root = true, sharing = true, vpn = false)
        assertEquals(listOf(NativePrivacyFeature.ENCRYPTED_DNS, NativePrivacyFeature.HIDDEN_API), features)
        assertEquals(listOf(NativePrivacyFeature.COMMUNITY_LISTS), summary(community = true, vpn = false))
    }

    @Test fun rootModeDoesNotClaimAndroidVpnNetworkMetadataIsInUse() {
        val features = summary(NativeRegionalMode.OFF, root = true, publicDns = true, proxy = NativeProxyPrivacy())
        assertFalse(NativePrivacyFeature.PUBLIC_DNS in features)
        assertFalse(NativePrivacyFeature.HIDDEN_SYSTEM_PROXY in features)
        assertTrue(NativePrivacyFeature.ROOT in features)
        val android = summary(NativeRegionalMode.OFF, publicDns = true, proxy = NativeProxyPrivacy())
        assertTrue(NativePrivacyFeature.PUBLIC_DNS in android)
        assertTrue(NativePrivacyFeature.HIDDEN_SYSTEM_PROXY in android)
    }

    @Test fun emptyCommunitySelectionDoesNotClaimListsOrBuiltInAppsAreApplied() {
        assertTrue(summary(community = true, communityEnabled = false).isEmpty())
    }

}
