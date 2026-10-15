package com.hiddify.hiddify.nativeprivacy

import com.hiddify.hiddify.privacy.NativeProxyPrivacy
import com.hiddify.hiddify.privacy.NativeRegionalMode
import com.hiddify.hiddify.privacy.NativeRegionalOptions

enum class NativePrivacyFeature {
    RUSSIAN_NETWORK, RUSSIAN_APPS, BLOCKED_SERVICES, COMMUNITY_LISTS, FULL_TUNNEL,
    ENCRYPTED_DNS, PUBLIC_DNS, HIDDEN_SYSTEM_PROXY, HIDDEN_LOCAL_PROXY, HIDDEN_API,
    ADAPTIVE_NETWORK, MASKED_PROTOCOLS, ROOT, WIFI_SHARING,
}

/** Derived from saved effective settings, never from the auto-setup backup/status. */
object NativePrivacySummary {
    fun features(
        regional: NativeRegionalOptions,
        fullTunnel: Boolean,
        communityRouting: Boolean,
        encryptedDns: Boolean,
        publicDns: Boolean,
        proxy: NativeProxyPrivacy,
        adaptiveNetwork: Boolean,
        maskedProtocolsOnly: Boolean,
        root: Boolean,
        wifiSharing: Boolean,
        vpnMode: Boolean = true,
        allowLan: Boolean = false,
        communityListsEnabled: Boolean = true,
    ): List<NativePrivacyFeature> = buildList {
        if (regional.russianNetworkBypass) add(NativePrivacyFeature.RUSSIAN_NETWORK)
        when {
            fullTunnel && vpnMode -> add(NativePrivacyFeature.FULL_TUNNEL)
            communityRouting -> if (communityListsEnabled) add(NativePrivacyFeature.COMMUNITY_LISTS)
            vpnMode && regional.mode == NativeRegionalMode.RUSSIAN_BYPASS -> add(NativePrivacyFeature.RUSSIAN_APPS)
            vpnMode && regional.mode == NativeRegionalMode.SELECTED_PROXY -> add(NativePrivacyFeature.BLOCKED_SERVICES)
        }
        if (encryptedDns) add(NativePrivacyFeature.ENCRYPTED_DNS)
        if (publicDns && vpnMode && !root) add(NativePrivacyFeature.PUBLIC_DNS)
        if (proxy.disableSystemProxy && vpnMode && !root) add(NativePrivacyFeature.HIDDEN_SYSTEM_PROXY)
        if (vpnMode && proxy.effectiveHideLocalProxy(wifiSharing || allowLan, root)) add(NativePrivacyFeature.HIDDEN_LOCAL_PROXY)
        if (proxy.hideClashApi) add(NativePrivacyFeature.HIDDEN_API)
        if (adaptiveNetwork) add(NativePrivacyFeature.ADAPTIVE_NETWORK)
        if (maskedProtocolsOnly) add(NativePrivacyFeature.MASKED_PROTOCOLS)
        if (root && vpnMode) add(NativePrivacyFeature.ROOT)
        if (wifiSharing && vpnMode) add(NativePrivacyFeature.WIFI_SHARING)
    }
}
