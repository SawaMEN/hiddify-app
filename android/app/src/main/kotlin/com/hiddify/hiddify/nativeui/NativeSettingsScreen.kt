package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R

data class NativeSettingsState(
    val rootRequested: Boolean,
    val wifiSharing: Boolean,
    val fullTunnel: Boolean,
    val encryptedDns: Boolean,
    val publicDns: Boolean,
    val disableSystemProxy: Boolean,
    val disableIpv6: Boolean,
    val handbookRouting: Boolean,
    val handbookProxy: Boolean,
    val handbookDirect: Boolean,
    val handbookProxySites: String,
    val handbookDirectSites: String,
)

@Composable
fun NativeSettingsScreen(
    state: NativeSettingsState,
    canChangeServiceMode: Boolean,
    onBack: () -> Unit,
    onOpenPerAppRouting: () -> Unit,
    onRootModeChanged: (Boolean) -> Unit,
    onWifiSharingChanged: (Boolean) -> Unit,
    onFullTunnelChanged: (Boolean) -> Unit,
    onEncryptedDnsChanged: (Boolean) -> Unit,
    onPublicDnsChanged: (Boolean) -> Unit,
    onDisableSystemProxyChanged: (Boolean) -> Unit,
    onDisableIpv6Changed: (Boolean) -> Unit,
    onHandbookRoutingChanged: (Boolean) -> Unit,
    onHandbookProxyChanged: (Boolean) -> Unit,
    onHandbookDirectChanged: (Boolean) -> Unit,
    onHandbookProxySitesChanged: (String) -> Unit,
    onHandbookDirectSitesChanged: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.native_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.native_back))
            }
        }

        SettingsSection(title = stringResource(R.string.native_settings_vpn)) {
            SettingSwitch(
                title = stringResource(R.string.native_setting_root),
                summary = stringResource(R.string.native_setting_root_summary),
                checked = state.rootRequested,
                enabled = canChangeServiceMode,
                onCheckedChange = onRootModeChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_wifi_sharing),
                summary = stringResource(R.string.native_setting_wifi_sharing_summary),
                checked = state.wifiSharing,
                enabled = canChangeServiceMode,
                onCheckedChange = onWifiSharingChanged,
            )
            OutlinedButton(
                onClick = onOpenPerAppRouting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_per_app_open))
            }
            SettingSwitch(
                title = stringResource(R.string.native_setting_full_tunnel),
                summary = stringResource(R.string.native_setting_full_tunnel_summary),
                checked = state.fullTunnel,
                onCheckedChange = onFullTunnelChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_ipv4_only),
                summary = stringResource(R.string.native_setting_ipv4_only_summary),
                checked = state.disableIpv6,
                onCheckedChange = onDisableIpv6Changed,
            )
        }

        SettingsSection(title = stringResource(R.string.native_settings_dns)) {
            SettingSwitch(
                title = stringResource(R.string.native_setting_encrypted_dns),
                summary = stringResource(R.string.native_setting_encrypted_dns_summary),
                checked = state.encryptedDns,
                onCheckedChange = onEncryptedDnsChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_public_dns),
                summary = stringResource(R.string.native_setting_public_dns_summary),
                checked = state.publicDns,
                onCheckedChange = onPublicDnsChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_disable_system_proxy),
                summary = stringResource(R.string.native_setting_disable_system_proxy_summary),
                checked = state.disableSystemProxy,
                onCheckedChange = onDisableSystemProxyChanged,
            )
        }

        SettingsSection(title = stringResource(R.string.native_settings_custom_routing)) {
            SettingSwitch(
                title = stringResource(R.string.native_setting_custom_routing),
                summary = stringResource(R.string.native_setting_custom_routing_summary),
                checked = state.handbookRouting,
                onCheckedChange = onHandbookRoutingChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_proxy_list),
                summary = stringResource(R.string.native_setting_proxy_list_summary),
                checked = state.handbookProxy,
                enabled = state.handbookRouting,
                onCheckedChange = onHandbookProxyChanged,
            )
            OutlinedTextField(
                value = state.handbookProxySites,
                onValueChange = onHandbookProxySitesChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.handbookRouting && state.handbookProxy,
                label = { Text(stringResource(R.string.native_setting_proxy_domains)) },
                supportingText = { Text(stringResource(R.string.native_domains_hint)) },
                minLines = 3,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_direct_list),
                summary = stringResource(R.string.native_setting_direct_list_summary),
                checked = state.handbookDirect,
                enabled = state.handbookRouting,
                onCheckedChange = onHandbookDirectChanged,
            )
            OutlinedTextField(
                value = state.handbookDirectSites,
                onValueChange = onHandbookDirectSitesChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.handbookRouting && state.handbookDirect,
                label = { Text(stringResource(R.string.native_setting_direct_domains)) },
                supportingText = { Text(stringResource(R.string.native_domains_hint)) },
                minLines = 3,
            )
        }

        if (!canChangeServiceMode) {
            Text(
                text = stringResource(R.string.native_settings_disconnect_required),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(R.string.native_settings_reconnect_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
