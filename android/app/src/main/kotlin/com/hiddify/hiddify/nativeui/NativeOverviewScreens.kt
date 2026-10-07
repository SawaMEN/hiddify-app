package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R

@Composable
internal fun NativePageHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.native_back_arrow), stringResource(R.string.native_back))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun NativeSettingsLink(title: Int, icon: Int, onClick: () -> Unit, summary: Int? = null, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(icon), null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            if (summary != null) Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.native_chevron), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun NativeSettingsOverviewScreen(
    onOpenProfiles: () -> Unit,
    onOpenCategory: (NativeSettingsCategory) -> Unit,
    onOpenCoreOptions: () -> Unit,
    onOpenChain: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLegacy: () -> Unit,
    onOpenDns: () -> Unit = {},
    onOpenTls: () -> Unit = {},
    onOpenInbound: () -> Unit = {},
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NativePageHeader(stringResource(R.string.native_settings))
        listOf(
            Triple(R.string.native_profiles, R.drawable.native_list, onOpenProfiles),
            Triple(R.string.native_settings_general, R.drawable.native_layers, { onOpenCategory(NativeSettingsCategory.APP) }),
            Triple(R.string.native_chain_open, R.drawable.native_route, onOpenChain),
            Triple(R.string.native_core_dns, R.drawable.native_dns, onOpenDns),
            Triple(R.string.native_core_tls, R.drawable.native_shield, onOpenTls),
            Triple(R.string.native_inbound_title, R.drawable.native_route, onOpenInbound),
            Triple(R.string.native_settings_vpn, R.drawable.native_shield, { onOpenCategory(NativeSettingsCategory.VPN) }),
            Triple(R.string.native_core_options_title, R.drawable.native_settings, onOpenCoreOptions),
            Triple(R.string.native_settings_custom_routing, R.drawable.native_route, { onOpenCategory(NativeSettingsCategory.ROUTING) }),
            Triple(R.string.native_settings_transfer, R.drawable.native_logs, { onOpenCategory(NativeSettingsCategory.BACKUP) }),
            Triple(R.string.native_logs_title, R.drawable.native_logs, onOpenLogs),
            Triple(R.string.native_about_open, R.drawable.native_info, onOpenAbout),
            Triple(R.string.native_advanced_legacy, R.drawable.native_layers, onOpenLegacy),
        ).forEach { (title, icon, open) ->
            NativeGlass(Modifier.fillMaxWidth(), radius = 24) { NativeSettingsLink(title, icon, open) }
        }
    }
}

@Composable
private fun PrivacyCategory(
    title: Int, summary: Int, icon: Int, expanded: Boolean, onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = stringResource(if (expanded) R.string.native_category_expanded else R.string.native_category_collapsed)
    NativeCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = state }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(R.drawable.native_chevron), null, Modifier.rotate(if (expanded) 270f else 90f))
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
internal fun NativePrivacyOverviewScreen(
    configured: Boolean = false,
    canRestore: Boolean = false,
    busy: Boolean = false,
    canApply: Boolean = true,
    onConfigure: () -> Unit = {},
    onRestore: () -> Unit = {},
    expandedCategories: Set<String> = emptySet(),
    onToggleCategory: (String) -> Unit = {},
    onOpenRegional: () -> Unit,
    onOpenPerApp: () -> Unit,
    onOpenPolicy: () -> Unit,
    onOpenTunnel: () -> Unit = {},
    onOpenDns: () -> Unit = {},
    onOpenProxyPrivacy: () -> Unit = {},
    onOpenProtection: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenCoreOptions: () -> Unit,
    onOpenCategory: (NativeSettingsCategory) -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NativePageHeader(stringResource(R.string.native_privacy_title))
        NativeCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(painterResource(R.drawable.native_shield), null, Modifier.size(52.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(if (configured) R.string.native_privacy_setup_configured else R.string.native_privacy_setup_ready),
                        style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.native_privacy_summary), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        NativeButton(onClick = onConfigure, enabled = canApply && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (configured) R.string.native_privacy_setup_again else R.string.native_privacy_setup_configure))
        }
        NativeOutlinedButton(onClick = onRestore, enabled = canApply && canRestore && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.native_privacy_setup_restore))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(stringResource(if (canApply) R.string.native_privacy_setup_apply_note else R.string.native_privacy_setup_disconnect),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrivacyCategory(R.string.native_privacy_routing, R.string.native_privacy_routing_summary, R.drawable.native_route,
            "routing" in expandedCategories, { onToggleCategory("routing") }) {
            NativeSettingsLink(R.string.native_regional_title, R.drawable.native_route, onOpenRegional)
            NativeSettingsLink(R.string.native_per_app_open, R.drawable.native_list, onOpenPerApp)
            NativeSettingsLink(R.string.native_settings_custom_routing, R.drawable.native_route, { onOpenCategory(NativeSettingsCategory.ROUTING) })
        }
        PrivacyCategory(R.string.native_privacy_connection, R.string.native_privacy_connection_summary, R.drawable.native_shield,
            "connection" in expandedCategories, { onToggleCategory("connection") }) {
            NativeSettingsLink(R.string.native_tunnel_title, R.drawable.native_route, onOpenTunnel)
            NativeSettingsLink(R.string.native_connection_policy_title, R.drawable.native_shield, onOpenPolicy)
            NativeSettingsLink(R.string.native_settings_vpn, R.drawable.native_settings, { onOpenCategory(NativeSettingsCategory.VPN) })
            NativeSettingsLink(R.string.native_protection_title, R.drawable.native_shield, onOpenProtection)
        }
        PrivacyCategory(R.string.native_privacy_dns, R.string.native_privacy_dns_summary, R.drawable.native_dns,
            "dns" in expandedCategories, { onToggleCategory("dns") }) {
            NativeSettingsLink(R.string.native_core_dns, R.drawable.native_dns, onOpenDns)
            NativeSettingsLink(R.string.native_settings_dns, R.drawable.native_shield, { onOpenCategory(NativeSettingsCategory.DNS) })
        }
        PrivacyCategory(R.string.native_privacy_interfaces, R.string.native_privacy_interfaces_summary, R.drawable.native_settings,
            "interfaces" in expandedCategories, { onToggleCategory("interfaces") }) {
            NativeSettingsLink(R.string.native_privacy_interfaces, R.drawable.native_settings,
                onOpenProxyPrivacy, R.string.native_privacy_interfaces_summary)
            NativeSettingsLink(R.string.native_core_options_title, R.drawable.native_settings, onOpenCoreOptions, R.string.native_core_options_summary)
        }
        NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
            NativeSettingsLink(R.string.native_filters_title, R.drawable.native_shield, onOpenFilters)
        }
        Text(stringResource(R.string.native_privacy_limits), Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Settings · dark", widthDp = 390, heightDp = 844)
@Composable
private fun SettingsOverviewPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.DARK) {
        NativeAtmosphere {
            Column(Modifier.padding(horizontal = 20.dp)) {
                NativeSettingsOverviewScreen({}, {}, {}, {}, {}, {}, {})
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Privacy · light", widthDp = 390, heightDp = 844)
@Composable
private fun PrivacyOverviewPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.LIGHT) {
        NativeAtmosphere {
            Column(Modifier.padding(horizontal = 16.dp)) {
                NativePrivacyOverviewScreen(onOpenRegional = {}, onOpenPerApp = {}, onOpenPolicy = {},
                    onOpenProtection = {}, onOpenFilters = {}, onOpenCoreOptions = {}, onOpenCategory = {})
            }
        }
    }
}

@Composable
internal fun NativePreferenceValueRow(title: Int, value: String, enabled: Boolean, icon: Int = R.drawable.native_dns, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().alpha(if (enabled) 1f else .38f).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(painterResource(R.drawable.native_chevron), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
