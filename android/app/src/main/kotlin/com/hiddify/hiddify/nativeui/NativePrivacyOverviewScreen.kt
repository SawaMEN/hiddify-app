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
    onSaveRegional: (NativeRegionalOptions) -> Unit,
    onSaveConnection: (NativeConnectionOptions) -> Unit,
    onSaveProxy: (NativeProxyPrivacy) -> Unit,
    onOpenApps: (NativeRegionalAppKind) -> Unit,
    onFullTunnel: (Boolean) -> Unit,
    onRoot: (Boolean) -> Unit,
    onEncryptedDns: (Boolean) -> Unit,
    onPublicDns: (Boolean) -> Unit,
    onHandbook: (Boolean) -> Unit,
    onHandbookProxy: (Boolean) -> Unit,
    onHandbookDirect: (Boolean) -> Unit,
    onHandbookProxySites: (String) -> Unit,
    onHandbookDirectSites: (String) -> Unit,
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
        if (!preview && !busy) policy = withContext(Dispatchers.IO) {
            runCatching { RegionalRouting.policy(context, options = regional) }.getOrNull()
        }
    }
    val ready = policy?.get("privacy-routing-mode")?.let { it == "ru-bypass" }
    val directCount = (policy?.get("privacy-direct-packages") as? List<*>)?.size ?: 0
    val proxyCount = (policy?.get("privacy-proxy-packages") as? List<*>)?.size ?: 0
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
                            Text(stringResource(when {
                                !configured -> R.string.native_privacy_ready_description
                                ready == false -> R.string.native_privacy_reapply_description
                                else -> R.string.native_privacy_configured_description
                            }), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (ready != null) {
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth().background(if (ready) scheme.secondaryContainer else scheme.errorContainer,
                            RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(painterResource(if (ready) R.drawable.privacy_check else R.drawable.privacy_error), null, Modifier.size(20.dp))
                            Text(if (ready) stringResource(R.string.native_privacy_policy_active, directCount, proxyCount)
                                else stringResource(R.string.native_privacy_policy_off), style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            NativeButton(onConfigure, Modifier.fillMaxWidth().height(52.dp), enabled = canApply && !busy) {
                Icon(painterResource(R.drawable.privacy_auto), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (configured) R.string.native_privacy_setup_again else R.string.native_privacy_setup_configure))
            }
            Spacer(Modifier.height(10.dp))
            NativeOutlinedButton(onRestore, Modifier.fillMaxWidth().height(48.dp), enabled = canApply && canRestore && !busy) {
                Icon(painterResource(R.drawable.privacy_restore), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.native_privacy_setup_restore))
            }
            if (busy) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (!canApply && !busy) Text(stringResource(R.string.native_privacy_setup_disconnect),
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.native_privacy_what), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Card(Modifier.fillMaxWidth()) {
                PrivacyFeature(R.drawable.privacy_apps, R.string.native_privacy_apps,
                    if (regional.russianAppsBypass) stringResource(R.string.native_privacy_apps_on, directCount)
                    else stringResource(R.string.native_privacy_apps_off), if (!busy) ({ onOpenApps(NativeRegionalAppKind.DIRECT) }) else null)
                PrivacyFeatureDivider()
                PrivacyFeature(R.drawable.privacy_route, R.string.native_privacy_services,
                    if (regional.restrictedServicesProxy) stringResource(R.string.native_privacy_services_on, proxyCount)
                    else stringResource(R.string.native_privacy_services_off), if (!busy) ({ onOpenApps(NativeRegionalAppKind.PROXY) }) else null)
                PrivacyFeatureDivider()
                PrivacyFeature(R.drawable.privacy_language, R.string.native_privacy_network,
                    stringResource(if (regional.russianNetworkBypass) R.string.native_privacy_network_on else R.string.native_privacy_network_off))
                PrivacyFeatureDivider()
                PrivacyFeature(R.drawable.privacy_dns, R.string.native_privacy_signals, stringResource(R.string.native_privacy_signals_summary))
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
                PrivacySwitch(R.string.native_privacy_handbook, R.string.native_privacy_handbook_summary, null,
                    settings.handbookRouting, !busy, onHandbook)
                if (settings.handbookRouting) {
                    NativeCommunityPreferences(settings, !busy, onSaveCommunitySelection)
                    Text(stringResource(R.string.native_privacy_handbook_note), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
                PrivacySwitch(R.string.native_privacy_direct_network, R.string.native_privacy_direct_network_summary,
                    R.drawable.privacy_language, regional.russianNetworkBypass && !settings.handbookRouting, !busy && !settings.handbookRouting) {
                    onSaveRegional(regional.copy(russianNetworkBypass = it))
                }
                PrivacySwitch(R.string.native_privacy_direct_apps, R.string.native_privacy_direct_apps_summary,
                    R.drawable.privacy_apps_outline, regional.russianAppsBypass && !settings.handbookRouting, !busy && !settings.handbookRouting) {
                    onSaveRegional(regional.copy(russianAppsBypass = it))
                }
                PrivacySwitch(R.string.native_privacy_proxy_services, R.string.native_privacy_proxy_services_summary,
                    R.drawable.privacy_route_outline, regional.restrictedServicesProxy && !settings.handbookRouting, !busy && !settings.handbookRouting) {
                    onSaveRegional(regional.copy(restrictedServicesProxy = it))
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
                    root == null -> R.string.native_privacy_root_checking
                    root?.get("detected") != true -> R.string.native_privacy_root_missing
                    root?.get("helper") != true -> R.string.native_privacy_root_helper_missing
                    else -> R.string.native_privacy_root_summary
                }, R.drawable.privacy_admin, settings.rootRequested,
                    !busy && canChangeRoot && root?.get("detected") == true && root?.get("helper") == true, onRoot)
            }
            Spacer(Modifier.height(10.dp))
            PrivacyCategory(R.string.native_privacy_dns, R.string.native_privacy_dns_summary, R.drawable.privacy_dns,
                "dns" in expandedCategories, { onToggleCategory("dns") }) {
                PrivacySwitch(R.string.native_privacy_encrypted, R.string.native_privacy_encrypted_summary,
                    R.drawable.privacy_dns, settings.encryptedDns, !busy, onEncryptedDns)
                PrivacySwitch(R.string.native_privacy_public, R.string.native_privacy_public_summary,
                    R.drawable.privacy_public, settings.publicDns, !busy, onPublicDns)
            }
            Spacer(Modifier.height(10.dp))
            PrivacyCategory(R.string.native_privacy_interfaces, R.string.native_privacy_interfaces_summary, R.drawable.privacy_ethernet,
                "interfaces" in expandedCategories, { onToggleCategory("interfaces") }) {
                PrivacySwitch(R.string.native_proxy_hide_local, R.string.native_proxy_hide_local_summary, R.drawable.privacy_wifi_off,
                    proxy.hideLocalProxy, !busy) { onSaveProxy(proxy.copy(hideLocalProxy = it)) }
                PrivacySwitch(R.string.native_proxy_hide_clash, R.string.native_proxy_hide_clash_summary, R.drawable.privacy_api,
                    proxy.hideClashApi, !busy) { onSaveProxy(proxy.copy(hideClashApi = it)) }
                PrivacySwitch(R.string.native_privacy_system_proxy, R.string.native_privacy_system_proxy_summary, R.drawable.privacy_http,
                    proxy.disableSystemProxy, !busy) { onSaveProxy(proxy.copy(disableSystemProxy = it)) }
            }
            Spacer(Modifier.height(26.dp))
            Text(stringResource(R.string.native_privacy_limits), Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
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
                    settings = NativeSettingsState("vpn", false, false, false, true, true, true, false,
                        false, true, true, "", "", true, false, false),
                    regional = NativeRegionalOptions(), regionalRevision = 0,
                    connection = NativeConnectionOptions(), proxy = NativeProxyPrivacy(),
                    onSaveRegional = {}, onSaveConnection = {}, onSaveProxy = {}, onOpenApps = {},
                    onFullTunnel = {}, onRoot = {}, onEncryptedDns = {}, onPublicDns = {},
                    onHandbook = {}, onHandbookProxy = {}, onHandbookDirect = {},
                    onHandbookProxySites = {}, onHandbookDirectSites = {},
                )
            }
        }
    }
}
