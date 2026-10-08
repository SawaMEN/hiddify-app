package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativediagnostics.NativeVpnProtection

@Composable
fun NativeVpnProtectionScreen(protection: NativeVpnProtection, onBack: () -> Unit,
    onOpenSettings: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        NativePageHeader(stringResource(R.string.native_protection_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(stringResource(R.string.native_protection_summary))
            Spacer(Modifier.height(16.dp))
            ProtectionRow(R.string.native_protection_always_on, protection.alwaysOn)
            ProtectionRow(R.string.native_protection_lockdown, protection.lockdown)
            NativeButton(onOpenSettings, Modifier.fillMaxWidth()) { Text(stringResource(R.string.native_protection_open_settings)) }
        }
    }
}

@Composable
private fun ProtectionRow(title: Int, value: Boolean?) {
    ListItem(headlineContent = { Text(stringResource(title)) }, trailingContent = {
        Text(stringResource(when (value) {
            true -> R.string.native_protection_enabled
            false -> R.string.native_protection_disabled
            null -> R.string.native_protection_unknown
        }))
    }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}
