package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.constant.ServiceMode

data class NativeSettingsState(
    val serviceMode: String,
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
    val dynamicNotification: Boolean,
    val debugMode: Boolean,
    val disableMemoryLimit: Boolean,
)

@Composable
fun NativeSettingsScreen(
    state: NativeSettingsState,
    canChangeServiceMode: Boolean,
    onBack: () -> Unit,
    onOpenPerAppRouting: () -> Unit,
    onOpenCoreOptions: () -> Unit,
    onOpenChain: () -> Unit,
    onOpenWifiSharingGuide: () -> Unit,
    onImportSettingsClipboard: () -> Unit,
    onImportSettingsFile: () -> Unit,
    onExportSettingsClipboard: (Boolean) -> Unit,
    onExportSettingsFile: (Boolean) -> Unit,
    onResetSettings: () -> Unit,
    onProxyOnlyChanged: (Boolean) -> Unit,
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
    onDynamicNotificationChanged: (Boolean) -> Unit,
    onDebugModeChanged: (Boolean) -> Unit,
    onDisableMemoryLimitChanged: (Boolean) -> Unit,
) {
    var includePrivateExport by rememberSaveable { mutableStateOf(false) }
    var resetSettingsOpen by rememberSaveable { mutableStateOf(false) }

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
                title = stringResource(R.string.native_setting_proxy_only),
                summary = stringResource(R.string.native_setting_proxy_only_summary),
                checked = state.serviceMode != ServiceMode.VPN,
                enabled = canChangeServiceMode,
                onCheckedChange = onProxyOnlyChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_root),
                summary = stringResource(R.string.native_setting_root_summary),
                checked = state.rootRequested,
                enabled = canChangeServiceMode && state.serviceMode == ServiceMode.VPN,
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
                onClick = onOpenWifiSharingGuide,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_wifi_guide_open))
            }
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

        SettingsSection(title = stringResource(R.string.native_core_options_title)) {
            Text(
                text = stringResource(R.string.native_core_options_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenCoreOptions,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_core_options_open))
            }
            OutlinedButton(
                onClick = onOpenChain,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_chain_open))
            }
        }

        SettingsSection(title = stringResource(R.string.native_settings_transfer)) {
            Text(
                text = stringResource(R.string.native_settings_transfer_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onImportSettingsClipboard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_settings_import_clipboard))
            }
            OutlinedButton(
                onClick = onImportSettingsFile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_settings_import_file))
            }
            SettingSwitch(
                title = stringResource(R.string.native_settings_include_private),
                summary = stringResource(R.string.native_settings_include_private_summary),
                checked = includePrivateExport,
                onCheckedChange = { includePrivateExport = it },
            )
            OutlinedButton(
                onClick = { onExportSettingsClipboard(includePrivateExport) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_settings_export_clipboard))
            }
            OutlinedButton(
                onClick = { onExportSettingsFile(includePrivateExport) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_settings_export_file))
            }
            TextButton(
                onClick = { resetSettingsOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_settings_reset))
            }
        }

        SettingsSection(title = stringResource(R.string.native_settings_app)) {
            SettingSwitch(
                title = stringResource(R.string.native_setting_dynamic_notification),
                summary = stringResource(R.string.native_setting_dynamic_notification_summary),
                checked = state.dynamicNotification,
                onCheckedChange = onDynamicNotificationChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_disable_memory_limit),
                summary = stringResource(R.string.native_setting_disable_memory_limit_summary),
                checked = state.disableMemoryLimit,
                onCheckedChange = onDisableMemoryLimitChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_debug),
                summary = stringResource(R.string.native_setting_debug_summary),
                checked = state.debugMode,
                onCheckedChange = onDebugModeChanged,
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

    if (resetSettingsOpen) {
        AlertDialog(
            onDismissRequest = { resetSettingsOpen = false },
            title = { Text(stringResource(R.string.native_settings_reset_title)) },
            text = { Text(stringResource(R.string.native_settings_reset_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetSettingsOpen = false
                        onResetSettings()
                    },
                ) {
                    Text(stringResource(R.string.native_settings_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { resetSettingsOpen = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
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
