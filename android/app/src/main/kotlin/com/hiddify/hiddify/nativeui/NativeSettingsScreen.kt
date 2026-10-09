package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import com.hiddify.hiddify.nativeui.NativeOutlinedButton as OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.hiddify.hiddify.nativepreferences.NativeThemeMode
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

enum class NativeSettingsCategory(val title: Int) {
    VPN(R.string.native_settings_vpn), DNS(R.string.native_settings_dns),
    ROUTING(R.string.native_settings_custom_routing), CORE(R.string.native_core_options_title),
    BACKUP(R.string.native_settings_transfer), APP(R.string.native_settings_general),
}

@Composable
fun NativeSettingsScreen(
    generalPreferences: com.hiddify.hiddify.nativepreferences.NativeGeneralPreferences,
    generalPreferencesBusy: Boolean,
    onChangeLanguage: (com.hiddify.hiddify.nativepreferences.NativeLanguage) -> Unit,
    onChangeHapticFeedback: (Boolean) -> Unit,
    onChangeSmartSelection: (Boolean) -> Unit,
    onOpenGeneralOptions: () -> Unit,
    onOpenTunnel: () -> Unit,
    state: NativeSettingsState,
    category: NativeSettingsCategory? = null,
    accentColor: com.hiddify.hiddify.nativepreferences.NativeAccentColor,
    onChangeAccent: (com.hiddify.hiddify.nativepreferences.NativeAccentColor) -> Unit,
    themeMode: NativeThemeMode,
    themeBusy: Boolean,
    onChangeTheme: (NativeThemeMode) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    canChangeServiceMode: Boolean,
    wifiSharingBusy: Boolean,
    wifiSharingDetails: com.hiddify.hiddify.nativecore.NativeWifiSharingDetails,
    onBack: () -> Unit,
    onOpenPerAppRouting: () -> Unit,
    onOpenConnectionPolicy: () -> Unit,
    onOpenRegionalRouting: () -> Unit,
    onOpenTrafficFilters: () -> Unit,
    onOpenVpnProtection: () -> Unit,
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
    onSaveCommunitySelection: (Boolean, Boolean, String) -> Unit,
) {
    var languagePickerOpen by rememberSaveable { mutableStateOf(false) }
    var accentPickerOpen by rememberSaveable { mutableStateOf(false) }
    var themePickerOpen by rememberSaveable { mutableStateOf(false) }
    var debugNoticeOpen by rememberSaveable { mutableStateOf(false) }
    var includePrivateExport by rememberSaveable { mutableStateOf(false) }
    var resetSettingsOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NativePageHeader(stringResource(category?.title ?: R.string.native_settings), onBack)

        if (category == null || category == NativeSettingsCategory.VPN) SettingsSection(title = stringResource(R.string.native_settings_vpn)) {
            NativeSettingsLink(R.string.native_protection_title, R.drawable.native_shield, onOpenVpnProtection)
            NativeSettingsLink(R.string.native_connection_policy_title, R.drawable.native_shield, onOpenConnectionPolicy)
            NativeSettingsLink(R.string.native_filters_title, R.drawable.native_shield, onOpenTrafficFilters)
            SettingSwitch(
                title = stringResource(R.string.native_setting_proxy_only),
                icon = R.drawable.native_chain_webhook,
                summary = stringResource(R.string.native_setting_proxy_only_summary),
                checked = state.serviceMode != ServiceMode.VPN,
                enabled = canChangeServiceMode,
                onCheckedChange = onProxyOnlyChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_root),
                icon = R.drawable.privacy_admin,
                summary = stringResource(R.string.native_setting_root_summary),
                checked = state.rootRequested,
                enabled = canChangeServiceMode && state.serviceMode == ServiceMode.VPN,
                onCheckedChange = onRootModeChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_wifi_sharing),
                icon = R.drawable.native_chain_wifi,
                summary = stringResource(R.string.native_setting_wifi_sharing_summary),
                checked = state.wifiSharing,
                enabled = !wifiSharingBusy,
                onCheckedChange = onWifiSharingChanged,
            )
            if (wifiSharingBusy) Text(stringResource(R.string.native_wifi_busy))
            NativeWifiCredentialsCard(wifiSharingDetails)
            NativeSettingsLink(R.string.native_wifi_guide_open, R.drawable.native_route, onOpenWifiSharingGuide)
            NativeSettingsLink(R.string.native_per_app_open, R.drawable.native_list, onOpenPerAppRouting)
            SettingSwitch(
                title = stringResource(R.string.native_setting_full_tunnel),
                icon = R.drawable.privacy_public,
                summary = stringResource(R.string.native_setting_full_tunnel_summary),
                checked = state.fullTunnel,
                onCheckedChange = onFullTunnelChanged,
            )
            NativeSettingsLink(R.string.native_tunnel_title, R.drawable.native_route, onOpenTunnel)

        }

        if (category == null || category == NativeSettingsCategory.DNS) SettingsSection(title = stringResource(R.string.native_settings_dns)) {
            SettingSwitch(
                title = stringResource(R.string.native_setting_encrypted_dns),
                icon = R.drawable.native_dns_private_connectivity,
                summary = stringResource(R.string.native_setting_encrypted_dns_summary),
                checked = state.encryptedDns,
                onCheckedChange = onEncryptedDnsChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_public_dns),
                icon = R.drawable.native_dns_public,
                summary = stringResource(R.string.native_setting_public_dns_summary),
                checked = state.publicDns,
                onCheckedChange = onPublicDnsChanged,
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_disable_system_proxy),
                icon = R.drawable.privacy_network,
                summary = stringResource(R.string.native_setting_disable_system_proxy_summary),
                checked = state.disableSystemProxy,
                onCheckedChange = onDisableSystemProxyChanged,
            )
        }

        if (category == null || category == NativeSettingsCategory.ROUTING) SettingsSection(title = stringResource(R.string.native_settings_custom_routing)) {
            NativeSettingsLink(R.string.native_regional_title, R.drawable.native_route, onOpenRegionalRouting)
            SettingSwitch(
                title = stringResource(R.string.native_setting_custom_routing),
                icon = R.drawable.privacy_route,
                summary = stringResource(R.string.native_setting_custom_routing_summary),
                checked = state.handbookRouting,
                onCheckedChange = onHandbookRoutingChanged,
            )
            NativeCommunityPreferences(state, state.handbookRouting && !wifiSharingBusy, onSaveCommunitySelection)

        }

        if (category == null || category == NativeSettingsCategory.CORE) SettingsSection(title = stringResource(R.string.native_core_options_title)) {
            Text(
                text = stringResource(R.string.native_core_options_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NativeSettingsLink(R.string.native_core_options_open, R.drawable.native_settings, onOpenCoreOptions)
            NativeSettingsLink(R.string.native_chain_open, R.drawable.native_route, onOpenChain)
        }

        if (category == null || category == NativeSettingsCategory.BACKUP) SettingsSection(title = stringResource(R.string.native_settings_transfer)) {
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
                icon = R.drawable.native_shield,
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
            NativeTextButton(
                onClick = { resetSettingsOpen = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.native_settings_reset))
            }
        }

        if (category == null || category == NativeSettingsCategory.APP) SettingsSection(title = stringResource(R.string.native_settings_app)) {
            NativePreferenceValueRow(R.string.native_language_title, languageTitle(generalPreferences.language),
                !generalPreferencesBusy, R.drawable.native_layers) { languagePickerOpen = true }
            if (!themeBusy) NativeSettingsLink(R.string.native_theme_title, R.drawable.native_layers,
                { themePickerOpen = true }, themeTitle(themeMode))
            else Text(stringResource(R.string.native_theme_title) + ": " + stringResource(themeTitle(themeMode)))
            NativePreferenceValueRow(R.string.native_accent_title, accentTitle(accentColor),
                !themeBusy, R.drawable.native_layers) { accentPickerOpen = true }
            NativeSettingsLink(R.string.native_general_options_title, R.drawable.native_route, onOpenGeneralOptions)
            NativeSettingsLink(R.string.native_notification_settings, R.drawable.native_settings,
                onOpenNotificationSettings, R.string.native_notification_settings_summary)
            NativeSettingsLink(R.string.native_battery_settings, R.drawable.native_settings,
                onOpenBatterySettings, R.string.native_battery_settings_summary)
            SettingSwitch(
                title = stringResource(R.string.native_setting_dynamic_notification),
                icon = R.drawable.native_info,
                summary = stringResource(R.string.native_setting_dynamic_notification_summary),
                checked = state.dynamicNotification,
                onCheckedChange = onDynamicNotificationChanged,
            )
            NativePreferenceSwitch(R.string.native_smart_selection_title, R.string.native_smart_selection_summary,
                R.drawable.native_layers, generalPreferences.smartSelection, !generalPreferencesBusy, onChangeSmartSelection)
            NativePreferenceSwitch(R.string.native_haptic_title, R.string.native_haptic_summary,
                R.drawable.native_layers, generalPreferences.hapticFeedback, !generalPreferencesBusy, onChangeHapticFeedback)
            SettingSwitch(
                title = stringResource(R.string.native_setting_memory_limit),
                icon = R.drawable.native_general_api,
                summary = stringResource(R.string.native_setting_memory_limit_summary),
                checked = !state.disableMemoryLimit,
                onCheckedChange = { onDisableMemoryLimitChanged(!it) },
            )
            SettingSwitch(
                title = stringResource(R.string.native_setting_debug),
                icon = R.drawable.native_general_description,
                summary = stringResource(R.string.native_setting_debug_summary),
                checked = state.debugMode,
                onCheckedChange = { if (it) debugNoticeOpen = true else onDebugModeChanged(false) },
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

    if (languagePickerOpen) {
        AlertDialog(onDismissRequest = { languagePickerOpen = false },
            title = { Text(stringResource(R.string.native_language_title)) },
            text = {
                Column {
                    com.hiddify.hiddify.nativepreferences.NativeLanguage.entries.forEach { language ->
                        val selected = generalPreferences.language == language
                        Row(Modifier.fillMaxWidth().selectable(selected = selected, enabled = !generalPreferencesBusy,
                            role = Role.RadioButton, onClick = {
                                languagePickerOpen = false
                                onChangeLanguage(language)
                            }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, onClick = null, enabled = !generalPreferencesBusy)
                            Text(languageTitle(language), Modifier.padding(start = 12.dp))
                        }
                    }
                    Text(stringResource(R.string.native_language_available), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }, confirmButton = {
                NativeTextButton(enabled = !generalPreferencesBusy, onClick = {
                    languagePickerOpen = false
                    onChangeLanguage(com.hiddify.hiddify.nativepreferences.NativeLanguage.ENGLISH)
                }) { Text(stringResource(R.string.native_quick_reset)) }
            }, dismissButton = {
                NativeTextButton(onClick = { languagePickerOpen = false }) { Text(stringResource(android.R.string.cancel)) }
            })
    }

    if (accentPickerOpen) {
        AlertDialog(onDismissRequest = { accentPickerOpen = false },
            title = { Text(stringResource(R.string.native_accent_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    com.hiddify.hiddify.nativepreferences.NativeAccentColor.entries.forEach { color ->
                        val selected = color == accentColor
                        Row(Modifier.fillMaxWidth().selectable(selected, !themeBusy, Role.RadioButton) {
                            accentPickerOpen = false
                            onChangeAccent(color)
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, enabled = !themeBusy, onClick = null)
                            Box(Modifier.padding(start = 12.dp).size(18.dp).background(Color(when (color) {
                                com.hiddify.hiddify.nativepreferences.NativeAccentColor.GRAY -> 0xFF575E68
                                com.hiddify.hiddify.nativepreferences.NativeAccentColor.BLUE -> 0xFF365F99
                                com.hiddify.hiddify.nativepreferences.NativeAccentColor.VIOLET -> 0xFF6750A4
                                com.hiddify.hiddify.nativepreferences.NativeAccentColor.ROSE -> 0xFF984061
                                com.hiddify.hiddify.nativepreferences.NativeAccentColor.AMBER -> 0xFF735B18
                                com.hiddify.hiddify.nativepreferences.NativeAccentColor.ORANGE -> 0xFFFFB787
                                com.hiddify.hiddify.nativepreferences.NativeAccentColor.GREEN -> 0xFF356A4A
                                com.hiddify.hiddify.nativepreferences.NativeAccentColor.BROWN -> 0xFF80543D
                            }), CircleShape))
                            Text(stringResource(accentTitle(color)), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }, confirmButton = {
                NativeTextButton(onClick = { accentPickerOpen = false }) { Text(stringResource(android.R.string.cancel)) }
            })
    }

    if (themePickerOpen) {
        AlertDialog(
            onDismissRequest = { themePickerOpen = false },
            title = { Text(stringResource(R.string.native_theme_title)) },
            text = {
                Column {
                    NativeThemeMode.entries.forEach { mode ->
                        val selected = mode == themeMode
                        Row(Modifier.fillMaxWidth().selectable(selected, !themeBusy, Role.RadioButton) {
                            themePickerOpen = false
                            onChangeTheme(mode)
                        }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selected, enabled = !themeBusy, onClick = null)
                            Text(stringResource(themeTitle(mode)), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                NativeTextButton(enabled = !themeBusy, onClick = {
                    themePickerOpen = false
                    onChangeTheme(NativeThemeMode.SYSTEM)
                }) { Text(stringResource(R.string.native_quick_reset)) }
            }, dismissButton = {
                NativeTextButton(onClick = { themePickerOpen = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (debugNoticeOpen) {
        val enableDebug = {
            debugNoticeOpen = false
            onDebugModeChanged(true)
        }
        AlertDialog(onDismissRequest = enableDebug,
            title = { Text(stringResource(R.string.native_setting_debug)) },
            text = { Text(stringResource(R.string.native_setting_debug_notice)) },
            confirmButton = { NativeTextButton(onClick = enableDebug) { Text(stringResource(android.R.string.ok)) } })
    }

    if (resetSettingsOpen) {
        AlertDialog(
            onDismissRequest = { resetSettingsOpen = false },
            title = { Text(stringResource(R.string.native_settings_reset_title)) },
            text = { Text(stringResource(R.string.native_settings_reset_message)) },
            confirmButton = {
                NativeTextButton(
                    onClick = {
                        resetSettingsOpen = false
                        onResetSettings()
                    },
                ) {
                    Text(stringResource(R.string.native_settings_reset))
                }
            },
            dismissButton = {
                NativeTextButton(onClick = { resetSettingsOpen = false }) {
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
    icon: Int = R.drawable.native_settings,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

private fun themeTitle(mode: NativeThemeMode): Int = when (mode) {
    NativeThemeMode.SYSTEM -> R.string.native_theme_system
    NativeThemeMode.LIGHT -> R.string.native_theme_light
    NativeThemeMode.DARK -> R.string.native_theme_dark
    NativeThemeMode.BLACK -> R.string.native_theme_black
}

@Composable
private fun languageTitle(language: com.hiddify.hiddify.nativepreferences.NativeLanguage): String = when (language) {
    com.hiddify.hiddify.nativepreferences.NativeLanguage.SYSTEM -> stringResource(R.string.native_language_system)
    com.hiddify.hiddify.nativepreferences.NativeLanguage.ENGLISH -> "English"
    com.hiddify.hiddify.nativepreferences.NativeLanguage.RUSSIAN -> "Русский"
}

private fun accentTitle(color: com.hiddify.hiddify.nativepreferences.NativeAccentColor): Int = when (color) {
    com.hiddify.hiddify.nativepreferences.NativeAccentColor.GRAY -> R.string.native_accent_gray
    com.hiddify.hiddify.nativepreferences.NativeAccentColor.BLUE -> R.string.native_accent_blue
    com.hiddify.hiddify.nativepreferences.NativeAccentColor.VIOLET -> R.string.native_accent_violet
    com.hiddify.hiddify.nativepreferences.NativeAccentColor.ROSE -> R.string.native_accent_rose
    com.hiddify.hiddify.nativepreferences.NativeAccentColor.AMBER -> R.string.native_accent_amber
    com.hiddify.hiddify.nativepreferences.NativeAccentColor.ORANGE -> R.string.native_accent_orange
    com.hiddify.hiddify.nativepreferences.NativeAccentColor.GREEN -> R.string.native_accent_green
    com.hiddify.hiddify.nativepreferences.NativeAccentColor.BROWN -> R.string.native_accent_brown
}
