package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.privacy.NativeProxyPrivacy

@Composable
internal fun NativePreferenceSwitch(
    title: Int, summary: Int, icon: Int,
    checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Switch,
        onValueChange = onChange).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}

@Composable
internal fun NativeProxyPrivacyScreen(
    options: NativeProxyPrivacy,
    busy: Boolean,
    wifiSharing: Boolean,
    rootMode: Boolean,
    onBack: () -> Unit,
    onSave: (NativeProxyPrivacy) -> Unit,
    onOpenCoreOptions: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NativePageHeader(stringResource(R.string.native_privacy_interfaces), onBack)
        NativeCard(Modifier.fillMaxWidth()) {
            NativePreferenceSwitch(R.string.native_proxy_hide_local, R.string.native_proxy_hide_local_summary,
                R.drawable.native_route, options.hideLocalProxy, !busy, { onSave(options.copy(hideLocalProxy = it)) })
            NativePreferenceSwitch(R.string.native_proxy_hide_clash, R.string.native_proxy_hide_clash_summary,
                R.drawable.native_settings, options.hideClashApi, !busy, { onSave(options.copy(hideClashApi = it)) })
            NativePreferenceSwitch(R.string.native_setting_disable_system_proxy, R.string.native_setting_disable_system_proxy_summary,
                R.drawable.native_settings, options.disableSystemProxy, !busy, { onSave(options.copy(disableSystemProxy = it)) })
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (options.hideLocalProxy && !options.effectiveHideLocalProxy(wifiSharing, rootMode)) {
            NativeGlass(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.native_proxy_wifi_exception), Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
            NativeSettingsLink(R.string.native_core_options_title, R.drawable.native_settings,
                onOpenCoreOptions, R.string.native_proxy_core_summary, enabled = !busy)
        }
        Text(stringResource(R.string.native_settings_reconnect_note), Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "Proxy privacy · dark", widthDp = 390, heightDp = 844)
@Composable
private fun ProxyPrivacyPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.DARK) {
        NativeAtmosphere {
            Column(Modifier.padding(horizontal = 16.dp)) {
                NativeProxyPrivacyScreen(NativeProxyPrivacy(), false, true, false, {}, {}, {})
            }
        }
    }
}
