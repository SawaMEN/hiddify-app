package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hiddify.hiddify.nativeui.NativeButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    onSave: (NativeRegionalOptions, Boolean, Boolean) -> Unit,
    onOpenApps: (NativeRegionalAppKind) -> Unit,
) {
    var mode by rememberSaveable(options.mode, handbookRouting) { mutableStateOf(options.mode.value) }
    var community by rememberSaveable(options.mode, handbookRouting) { mutableStateOf(handbookRouting) }
    var routingSelected by rememberSaveable(options.mode, handbookRouting, fullTunnel) { mutableStateOf(false) }
    val draft = options.withMode(NativeRegionalMode.fromValue(mode))
    val changed = draft != options || community != handbookRouting || (routingSelected && fullTunnel)
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
        NativeRoutingModeChoices(NativeRegionalMode.fromValue(mode), !busy, community, fullTunnel && !routingSelected,
            onOpenApps = onOpenApps, canOpenApps = !busy && !changed) { choice ->
            val selected = draft.withMode(choice)
            community = choice == NativeRegionalMode.OFF
            routingSelected = true
            mode = selected.mode.value
        }
        if (changed) {
            Text(stringResource(R.string.native_regional_apps_save_first), style = MaterialTheme.typography.bodySmall)
        }
        if (!valid) Text(stringResource(R.string.native_regional_invalid), color = MaterialTheme.colorScheme.error)
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy && valid && changed, onClick = { onSave(draft, community, routingSelected) },
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.native_regional_save)) }
    }
}
