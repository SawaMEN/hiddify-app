package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hiddify.hiddify.nativeui.NativeButton as Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativediagnostics.NativeVpnProtection

@Composable
fun NativeVpnProtectionScreen(
    protection: NativeVpnProtection,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.native_protection_title), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text(stringResource(R.string.native_back)) }
        }
        Text(stringResource(R.string.native_protection_summary))
        ProtectionRow(R.string.native_protection_always_on, protection.alwaysOn)
        ProtectionRow(R.string.native_protection_lockdown, protection.lockdown)
        if (protection.alwaysOn == null || protection.lockdown == null) {
            Text(stringResource(R.string.native_protection_unknown_hint), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.native_protection_open_settings))
        }
        TextButton(onClick = onRefresh) { Text(stringResource(R.string.native_logs_refresh)) }
    }
}

@Composable
private fun ProtectionRow(title: Int, value: Boolean?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(title), modifier = Modifier.weight(1f))
        Text(stringResource(when (value) {
            true -> R.string.native_protection_enabled
            false -> R.string.native_protection_disabled
            null -> R.string.native_protection_unknown
        }))
    }
}
