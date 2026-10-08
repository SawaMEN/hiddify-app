package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeconnection.NativeConnectionOptions

@Composable
fun NativeConnectionPolicyScreen(options: NativeConnectionOptions, busy: Boolean,
    onBack: () -> Unit, onSave: (NativeConnectionOptions) -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NativePageHeader(stringResource(R.string.native_connection_policy_title), onBack)
        Text(stringResource(R.string.native_settings_reconnect_note))
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ConnectionPolicySwitch(R.string.native_connection_masked, R.string.native_connection_masked_summary,
            options.maskedProtocolsOnly, !busy) { onSave(options.copy(maskedProtocolsOnly = it)) }
        ConnectionPolicySwitch(R.string.native_connection_udp, R.string.native_connection_udp_summary,
            options.allowUdp, !busy && options.maskedProtocolsOnly) { onSave(options.copy(allowUdp = it)) }
        ConnectionPolicySwitch(R.string.native_connection_adaptive, R.string.native_connection_adaptive_summary,
            options.adaptiveNetwork, !busy) { onSave(options.copy(adaptiveNetwork = it)) }
        ConnectionPolicySwitch(R.string.native_recovery_setting, R.string.native_recovery_setting_summary,
            options.autoReconnect, !busy) { onSave(options.copy(autoReconnect = it)) }
        Text(stringResource(R.string.native_connection_probe_summary), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ConnectionPolicySwitch(title: Int, summary: Int, checked: Boolean,
    enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(summary), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}
