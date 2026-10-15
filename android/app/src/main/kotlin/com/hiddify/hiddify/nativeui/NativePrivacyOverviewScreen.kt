package com.hiddify.hiddify.nativeui

import androidx.compose.material3.Card

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeconnection.NativeConnectionOptions
import com.hiddify.hiddify.nativeprivacy.NativePrivacyControls
import com.hiddify.hiddify.nativeprivacy.NativePrivacyFeature
import com.hiddify.hiddify.nativeprivacy.NativePrivacySummary
import com.hiddify.hiddify.privacy.NativeProxyPrivacy
import com.hiddify.hiddify.privacy.NativeRegionalAppKind
import com.hiddify.hiddify.privacy.NativeRegionalOptions
import com.hiddify.hiddify.privacy.RegionalRouting
import com.hiddify.hiddify.privacy.RootCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Layout and copy mirror VpnPrivacyOverviewPage before the Flutter removal (7197f9e4). */
@Composable
internal fun NativePrivacyOverviewScreen(
    configured: Boolean,
    canRestore: Boolean,
    busy: Boolean,
    canApply: Boolean,
    canChangeRoot: Boolean,
    onConfigure: () -> Unit,
    onRestore: () -> Unit,
    expandedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    settings: NativeSettingsState,
    regional: NativeRegionalOptions,
    regionalRevision: Int,
    connection: NativeConnectionOptions,
    proxy: NativeProxyPrivacy,
    onSaveRegional: (NativeRegionalOptions, Boolean) -> Unit,
    onSaveConnection: (NativeConnectionOptions) -> Unit,
    onSaveProxy: (NativeProxyPrivacy) -> Unit,
    onOpenApps: (NativeRegionalAppKind) -> Unit,
    onFullTunnel: (Boolean) -> Unit,
    onRoot: (Boolean) -> Unit,
    onEncryptedDns: (Boolean) -> Unit,
    onPublicDns: (Boolean) -> Unit,
    allowLan: Boolean = false,
    requiresReconnect: Boolean = false,
    onApplySaved: (() -> Unit)? = null,
    onEnableVpn: () -> Unit = {},
    onSaveCommunitySelection: (Boolean, Boolean, String) -> Unit = { _, _, _ -> },
) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    var policy by remember { mutableStateOf<Map<String, Any>?>(null) }
    var root by remember { mutableStateOf<Map<String, Any>?>(null) }
    // Opening the tab only discovers root. The existing root action handles permission requests.
    LaunchedEffect(Unit) {
        if (!preview) root = withContext(Dispatchers.IO) { RootCore.detect(context) }
    }
    LaunchedEffect(regional, regionalRevision, settings.fullTunnel, settings.handbookRouting, busy) {
        policy = null
        if (!preview && !busy) policy = withContext(Dispatchers.IO) {
            runCatching { RegionalRouting.policy(context, options = regional) }.getOrNull()
        }
    }
    val directCount = (policy?.get("privacy-direct-packages") as? List<*>)?.size
    val proxyCount = (policy?.get("privacy-proxy-packages") as? List<*>)?.size
    val serviceCount = (policy?.get("privacy-selected-services") as? List<*>)?.size
    val vpnMode = settings.serviceMode == com.hiddify.hiddify.constant.ServiceMode.VPN
    val rootMode = vpnMode && settings.rootRequested
    val androidVpn = vpnMode && !rootMode
    val features = NativePrivacySummary.features(regional, settings.fullTunnel, settings.handbookRouting,
        settings.encryptedDns, settings.publicDns, proxy, connection.adaptiveNetwork,
        connection.maskedProtocolsOnly, rootMode, settings.wifiSharingRequested, vpnMode, allowLan, settings.handbookProxy || settings.handbookDirect)
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        // AppBar is outside ListView in Dart and stays visible while the content scrolls.
        NativePageHeader(stringResource(R.string.native_privacy_title))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 28.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.size(52.dp).background(
                            if (configured) scheme.primaryContainer else scheme.surfaceContainerHighest,
                            RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Icon(painterResource(if (configured) R.drawable.privacy_shield_filled else R.drawable.privacy_shield),
                                null, Modifier.size(24.dp), tint = if (configured) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(if (configured) R.string.native_privacy_setup_configured else R.string.native_privacy_setup_ready),
                                style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(if (configured) R.string.native_privacy_configured_description
                                else R.string.native_privacy_ready_description), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            NativeButton(onConfigure, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = canApply && !busy && !configured) {
                Icon(painterResource(R.drawable.privacy_auto), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (configured) R.string.native_privacy_setup_again else R.string.native_privacy_setup_configure))
            }
            if (canRestore) {
                Spacer(Modifier.height(10.dp))
                NativeOutlinedButton(onRestore, Modifier.fillMaxWidth().height(48.dp), enabled = canApply && canRestore && !busy) {
                    Icon(painterResource(R.drawable.privacy_restore), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.native_privacy_setup_restore))
                }
            }
            if (busy) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (!canApply && !busy) Text(stringResource(R.string.native_privacy_setup_disconnect),
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            if (!vpnMode) {
                Text(stringResource(R.string.native_privacy_proxy_mode_notice), Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium)
                NativeOutlinedButton(onEnableVpn, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = canChangeRoot && !busy) { Text(stringResource(R.string.native_privacy_enable_vpn)) }
            }
            if (requiresReconnect) {
                Text(stringResource(R.string.native_privacy_pending_changes), Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.primary)
                if (onApplySaved != null) NativeButton(onApplySaved, Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = !busy) { Text(stringResource(R.string.native_quick_reconnect)) }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.native_privacy_what), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            if (features.isEmpty()) {
                Text(stringResource(R.string.native_privacy_nothing_configured), style = MaterialTheme.typography.bodyMedium)
            } else Card(Modifier.fillMaxWidth()) {
                features.forEachIndexed { index, feature ->
                    if (index > 0) PrivacyFeatureDivider()
                    PrivacyConfiguredFeature(feature, directCount, proxyCount, serviceCount,
                        regional.russianNetworkBypass, vpnMode && !settings.fullTunnel && !settings.handbookRouting &&
                            regional.mode == com.hiddify.hiddify.privacy.NativeRegionalMode.RUSSIAN_BYPASS,
                        if (!busy) onOpenApps else null)
                }
            }
            Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth()) {
                PrivacySwitch(R.string.native_privacy_masked, R.string.native_privacy_masked_summary,
                    R.drawable.privacy_security_filled, connection.maskedProtocolsOnly, !busy) {
                    onSaveConnection(connection.copy(maskedProtocolsOnly = it))
                }
            }
            if (connection.maskedProtocolsOnly) PrivacySwitch(R.string.native_privacy_udp, R.string.native_privacy_udp_summary,
                null, connection.allowUdp, !busy) { onSaveConnection(connection.copy(allowUdp = it)) }
            Text(stringResource(R.string.native_privacy_additional), Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
                style = MaterialTheme.typography.titleMedium)
            PrivacyCategory(R.string.native_privacy_routing, R.string.native_privacy_routing_summary, R.drawable.privacy_route_outline,
                "routing" in expandedCategories, { onToggleCategory("routing") }) {
                NativeRoutingModeChoices(regional.mode, !busy, settings.handbookRouting, settings.fullTunnel && vpnMode) { mode ->
                    onSaveRegional(regional.withMode(mode), mode == com.hiddify.hiddify.privacy.NativeRegionalMode.OFF)
                }
                NativeTextButton(enabled = !busy, onClick = { onOpenApps(NativeRegionalAppKind.DIRECT) }) {
                    Text(stringResource(R.string.native_regional_apps_direct_title))
                }
                NativeTextButton(enabled = !busy, onClick = { onOpenApps(NativeRegionalAppKind.PROXY) }) {
                    Text(stringResource(R.string.native_regional_apps_proxy_title))
                }
                if (settings.fullTunnel) {
                    Text(stringResource(R.string.native_routing_override_notice), Modifier.padding(16.dp))
                }
                if (settings.handbookRouting) {
                    NativeCommunityPreferences(settings, !busy && !settings.fullTunnel, onSaveCommunitySelection)
                    Text(stringResource(R.string.native_privacy_handbook_note), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            PrivacyCategory(R.string.native_privacy_connection, R.string.native_privacy_connection_summary, R.drawable.privacy_security,
                "connection" in expandedCategories, { onToggleCategory("connection") }) {
                PrivacySwitch(R.string.native_privacy_adaptive, R.string.native_privacy_adaptive_summary, R.drawable.privacy_network,
                    connection.adaptiveNetwork, !busy) { onSaveConnection(connection.copy(adaptiveNetwork = it)) }
                PrivacySwitch(R.string.native_privacy_full, R.string.native_privacy_full_summary, R.drawable.privacy_security,
                    settings.fullTunnel, !busy, onFullTunnel)
                PrivacySwitch(R.string.native_privacy_root, when {
                    !canChangeRoot -> R.string.native_profile_disconnect_required
                    root == null -> R.string.native_privacy_root_checking
                    root?.get("detected") != true -> R.string.native_privacy_root_missing
                    root?.get("helper") != true -> R.string.native_privacy_root_helper_missing
                    else -> R.string.native_privacy_root_summary
                }, R.drawable.privacy_admin, settings.rootRequested,
                    NativePrivacyControls.canToggleRoot(busy, canChangeRoot, settings.rootRequested,
                        root?.get("detected") == true, root?.get("helper") == true), onRoot)
            }
            Spacer(Modifier.height(10.dp))
            PrivacyCategory(R.string.native_privacy_dns, R.string.native_privacy_dns_summary, R.drawable.privacy_dns,
                "dns" in expandedCategories, { onToggleCategory("dns") }) {
                PrivacySwitch(R.string.native_privacy_encrypted, R.string.native_privacy_encrypted_summary,
                    R.drawable.privacy_dns, settings.encryptedDns, !busy, onEncryptedDns)
                PrivacySwitch(R.string.native_privacy_public, if (androidVpn) R.string.native_privacy_public_summary else R.string.native_privacy_android_vpn_only,
                    R.drawable.privacy_public, settings.publicDns, !busy && androidVpn, onPublicDns)
            }
            Spacer(Modifier.height(10.dp))
            PrivacyCategory(R.string.native_privacy_interfaces, R.string.native_privacy_interfaces_summary, R.drawable.privacy_ethernet,
                "interfaces" in expandedCategories, { onToggleCategory("interfaces") }) {
                PrivacySwitch(R.string.native_proxy_hide_local, when {
                    !vpnMode -> R.string.native_privacy_proxy_ports_needed
                    proxy.hideLocalProxy && !rootMode && (settings.wifiSharingRequested || allowLan) -> R.string.native_privacy_local_proxy_exception
                    else -> R.string.native_proxy_hide_local_summary
                }, R.drawable.privacy_wifi_off,
                    proxy.hideLocalProxy, !busy && vpnMode) { onSaveProxy(proxy.copy(hideLocalProxy = it)) }
                PrivacySwitch(R.string.native_proxy_hide_clash, R.string.native_proxy_hide_clash_summary, R.drawable.privacy_api,
                    proxy.hideClashApi, !busy) { onSaveProxy(proxy.copy(hideClashApi = it)) }
                PrivacySwitch(R.string.native_privacy_system_proxy, if (androidVpn) R.string.native_privacy_system_proxy_summary else R.string.native_privacy_android_vpn_only, R.drawable.privacy_http,
                    proxy.disableSystemProxy, !busy && androidVpn) { onSaveProxy(proxy.copy(disableSystemProxy = it)) }
            }
            Spacer(Modifier.height(26.dp))
            Text(stringResource(R.string.native_privacy_limits), Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrivacyConfiguredFeature(feature: NativePrivacyFeature, directCount: Int?, proxyCount: Int?,
    serviceCount: Int?, networkBypass: Boolean, directAppsActive: Boolean, onOpenApps: ((NativeRegionalAppKind) -> Unit)?) {
    val (icon, title, description) = when (feature) {
        NativePrivacyFeature.RUSSIAN_APPS -> Triple(R.drawable.privacy_apps, R.string.native_privacy_apps,
            listOfNotNull(
                if (directAppsActive) {
                    if (directCount == null) stringResource(R.string.native_privacy_apps_loading)
                    else stringResource(R.string.native_privacy_apps_on, directCount)
                } else null,
            ).joinToString("\n"))
        NativePrivacyFeature.BLOCKED_SERVICES -> Triple(R.drawable.privacy_route, R.string.native_privacy_services,
            if (serviceCount == null || proxyCount == null) stringResource(R.string.native_privacy_services_loading)
            else stringResource(R.string.native_privacy_services_on, serviceCount, proxyCount))
        NativePrivacyFeature.COMMUNITY_LISTS -> Triple(R.drawable.privacy_route, R.string.native_privacy_handbook,
            stringResource(R.string.native_privacy_community_configured))
        NativePrivacyFeature.FULL_TUNNEL -> Triple(R.drawable.privacy_security, R.string.native_privacy_full,
            stringResource(R.string.native_privacy_full_summary))
        NativePrivacyFeature.ENCRYPTED_DNS -> Triple(R.drawable.privacy_dns, R.string.native_privacy_encrypted,
            stringResource(R.string.native_privacy_encrypted_summary))
        NativePrivacyFeature.PUBLIC_DNS -> Triple(R.drawable.privacy_public, R.string.native_privacy_public,
            stringResource(R.string.native_privacy_public_summary))
        NativePrivacyFeature.HIDDEN_SYSTEM_PROXY -> Triple(R.drawable.privacy_http, R.string.native_privacy_system_proxy,
            stringResource(R.string.native_privacy_system_proxy_summary))
        NativePrivacyFeature.HIDDEN_LOCAL_PROXY -> Triple(R.drawable.privacy_wifi_off, R.string.native_proxy_hide_local,
            stringResource(R.string.native_proxy_hide_local_summary))
        NativePrivacyFeature.HIDDEN_API -> Triple(R.drawable.privacy_api, R.string.native_proxy_hide_clash,
            stringResource(R.string.native_proxy_hide_clash_summary))
        NativePrivacyFeature.ADAPTIVE_NETWORK -> Triple(R.drawable.privacy_network, R.string.native_privacy_adaptive,
            stringResource(R.string.native_privacy_adaptive_summary))
        NativePrivacyFeature.MASKED_PROTOCOLS -> Triple(R.drawable.privacy_security_filled, R.string.native_privacy_masked,
            stringResource(R.string.native_privacy_masked_summary))
        NativePrivacyFeature.ROOT -> Triple(R.drawable.privacy_admin, R.string.native_privacy_root,
            stringResource(R.string.native_privacy_root_summary))
        NativePrivacyFeature.WIFI_SHARING -> Triple(R.drawable.native_wifi_signal, R.string.native_setting_wifi_sharing,
            stringResource(R.string.native_setting_wifi_sharing_summary))
    }
    val kind = when (feature) {
        NativePrivacyFeature.RUSSIAN_APPS -> NativeRegionalAppKind.DIRECT
        NativePrivacyFeature.BLOCKED_SERVICES -> NativeRegionalAppKind.PROXY
        else -> null
    }
    val routingFeature = feature == NativePrivacyFeature.RUSSIAN_APPS || feature == NativePrivacyFeature.BLOCKED_SERVICES ||
        feature == NativePrivacyFeature.COMMUNITY_LISTS || feature == NativePrivacyFeature.FULL_TUNNEL
    val summary = if (networkBypass && routingFeature)
        listOf(description, stringResource(R.string.native_privacy_network_on)).filter { it.isNotEmpty() }.joinToString("\n")
        else description
    PrivacyFeature(icon, title, summary, if (kind != null && onOpenApps != null) ({ onOpenApps(kind) }) else null)
}

@Composable
private fun PrivacyFeatureDivider() {
    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PrivacyFeature(icon: Int, title: Int, summary: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), null, Modifier.padding(top = 2.dp).size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(3.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium)
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.native_chevron), null, Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PrivacyCategory(title: Int, summary: Int, icon: Int, expanded: Boolean, onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(if (expanded) R.string.native_category_expanded else R.string.native_category_collapsed)
    val duration = if (LocalNativeMotionEnabled.current) 200 else 0
    val angle by animateFloatAsState(if (expanded) 270f else 90f, animationSpec = tween(duration), label = "Privacy expansion")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize(animationSpec = tween(duration))) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).semantics { stateDescription = description }
                .heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painterResource(icon), null, Modifier.size(24.dp), tint = if (expanded) scheme.primary else scheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, color = if (expanded) scheme.primary else scheme.onSurface)
                    Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.native_chevron), null, Modifier.size(24.dp).rotate(angle),
                    tint = if (expanded) scheme.primary else scheme.onSurfaceVariant)
            }
            if (expanded) {
                HorizontalDivider(color = scheme.outline)
                content()
                HorizontalDivider(color = scheme.outline)
            }
        }
    }
}

@Composable
private fun PrivacySwitch(title: Int, summary: Int?, icon: Int?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .heightIn(min = if (summary == null) 56.dp else 72.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (icon != null) Icon(painterResource(icon), null, Modifier.size(24.dp), tint = scheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = .38f))
            if (summary != null) Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = .38f))
        }
        Switch(checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.width(60.dp), colors = SwitchDefaults.colors(
            checkedThumbColor = scheme.onPrimary, checkedTrackColor = scheme.primary,
            uncheckedThumbColor = scheme.outline, uncheckedTrackColor = scheme.surfaceContainerHighest,
            uncheckedBorderColor = scheme.outline, disabledCheckedTrackColor = scheme.surfaceContainerHighest,
            disabledUncheckedTrackColor = scheme.surfaceContainerHighest,
            disabledCheckedThumbColor = scheme.onSurface.copy(alpha = .38f), disabledUncheckedThumbColor = scheme.onSurface.copy(alpha = .38f)))
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "VPN privacy · light", widthDp = 390, heightDp = 844)
@androidx.compose.ui.tooling.preview.Preview(name = "VPN privacy · dark", widthDp = 390, heightDp = 844, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrivacyOverviewPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.SYSTEM) {
        NativeBackground {
            Column(Modifier.padding(horizontal = 16.dp)) {
                NativePrivacyOverviewScreen(
                    configured = false, canRestore = false, busy = false, canApply = true, canChangeRoot = true,
                    onConfigure = {}, onRestore = {}, expandedCategories = emptySet(), onToggleCategory = {},
                    settings = NativeSettingsState(serviceMode = "vpn", rootRequested = false, wifiSharing = false,
                        fullTunnel = false, encryptedDns = true, publicDns = true, disableSystemProxy = true,
                        handbookRouting = false, handbookProxy = true, handbookDirect = true,
                        handbookProxySites = "", handbookDirectSites = "", dynamicNotification = true,
                        debugMode = false, disableMemoryLimit = false),
                    regional = NativeRegionalOptions(), regionalRevision = 0,
                    connection = NativeConnectionOptions(), proxy = NativeProxyPrivacy(),
                    onSaveRegional = { _, _ -> }, onSaveConnection = {}, onSaveProxy = {}, onOpenApps = {},
                    onFullTunnel = {}, onRoot = {}, onEncryptedDns = {}, onPublicDns = {},
                )
            }
        }
    }
}
