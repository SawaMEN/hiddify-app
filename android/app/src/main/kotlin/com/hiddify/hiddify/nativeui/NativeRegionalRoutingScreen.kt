package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hiddify.hiddify.nativeui.NativeButton as Button
import com.hiddify.hiddify.nativeui.NativeCard as Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.hiddify.hiddify.nativeui.NativeTextField as OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.privacy.NativeRegionalAppKind
import com.hiddify.hiddify.privacy.NativeRegionalMode
import com.hiddify.hiddify.privacy.NativeRegionalOptions

@Composable
fun NativeRegionalRoutingScreen(
    options: NativeRegionalOptions,
    busy: Boolean,
    fullTunnel: Boolean,
    handbookRouting: Boolean,
    onBack: () -> Unit,
    onSave: (NativeRegionalOptions) -> Unit,
    onOpenApps: (NativeRegionalAppKind) -> Unit,
) {
    var mode by rememberSaveable(options.mode) { mutableStateOf(options.mode.value) }
    var network by rememberSaveable(options.russianNetworkBypass) { mutableStateOf(options.russianNetworkBypass) }
    var apps by rememberSaveable(options.russianAppsBypass) { mutableStateOf(options.russianAppsBypass) }
    var restricted by rememberSaveable(options.restrictedServicesProxy) { mutableStateOf(options.restrictedServicesProxy) }
    var direct by rememberSaveable(options.directDomains) { mutableStateOf(options.directDomains) }
    var proxy by rememberSaveable(options.proxyDomains) { mutableStateOf(options.proxyDomains) }
    val draft = NativeRegionalOptions(NativeRegionalMode.fromValue(mode), network, apps, restricted, direct, proxy)
    val valid = runCatching { draft.validated() }.isSuccess

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NativePageHeader(stringResource(R.string.native_regional_title), onBack)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.native_regional_summary))
                Text(stringResource(R.string.native_settings_reconnect_note))
                if (fullTunnel || handbookRouting) {
                    Text(stringResource(if (fullTunnel) R.string.native_regional_full_tunnel
                        else R.string.native_regional_handbook), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Text(stringResource(R.string.native_regional_mode), style = MaterialTheme.typography.titleMedium)
        NativeRegionalMode.entries.forEach { choice ->
            val label = when (choice) {
                NativeRegionalMode.OFF -> R.string.native_regional_off
                NativeRegionalMode.RUSSIAN_BYPASS -> R.string.native_regional_bypass
                NativeRegionalMode.SELECTED_PROXY -> R.string.native_regional_selected
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mode == choice.value, enabled = !busy, onClick = { mode = choice.value })
                TextButton(enabled = !busy, onClick = { mode = choice.value }) { Text(stringResource(label)) }
            }
        }
        if (mode == NativeRegionalMode.SELECTED_PROXY.value) {
            Text(stringResource(R.string.native_regional_selected_summary), style = MaterialTheme.typography.bodySmall)
        }
        RegionalSwitch(R.string.native_regional_network, R.string.native_regional_network_summary,
            network, !busy) { network = it }
        RegionalSwitch(R.string.native_regional_apps, R.string.native_regional_apps_summary,
            apps, !busy) { apps = it }
        RegionalSwitch(R.string.native_regional_restricted, R.string.native_regional_restricted_summary,
            restricted, !busy) { restricted = it }
        if (draft != options) {
            Text(stringResource(R.string.native_regional_apps_save_first), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(enabled = !busy && draft == options, onClick = { onOpenApps(NativeRegionalAppKind.DIRECT) }) {
            Text(stringResource(R.string.native_regional_apps_direct_title))
        }
        TextButton(enabled = !busy && draft == options, onClick = { onOpenApps(NativeRegionalAppKind.PROXY) }) {
            Text(stringResource(R.string.native_regional_apps_proxy_title))
        }
        OutlinedTextField(value = direct, onValueChange = { if (it.length <= 8192) direct = it },
            enabled = !busy, modifier = Modifier.fillMaxWidth(), minLines = 3,
            label = { Text(stringResource(R.string.native_regional_direct_domains)) },
            supportingText = { Text(stringResource(R.string.native_regional_domains_hint)) })
        OutlinedTextField(value = proxy, onValueChange = { if (it.length <= 8192) proxy = it },
            enabled = !busy, modifier = Modifier.fillMaxWidth(), minLines = 3,
            label = { Text(stringResource(R.string.native_regional_proxy_domains)) },
            supportingText = { Text(stringResource(R.string.native_regional_domains_hint)) })
        if (!valid) Text(stringResource(R.string.native_regional_invalid), color = MaterialTheme.colorScheme.error)
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && valid && draft != options, onClick = { onSave(draft) },
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.native_regional_save)) }
    }
}

@Composable
private fun RegionalSwitch(title: Int, summary: Int, checked: Boolean, enabled: Boolean,
    onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}
