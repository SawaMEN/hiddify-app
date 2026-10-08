package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
        ).forEach { (title, icon, open) ->
            NativeGlass(Modifier.fillMaxWidth(), radius = 24) { NativeSettingsLink(title, icon, open) }
        }
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
