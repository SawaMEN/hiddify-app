package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.privacy.NativeRegionalAppKind
import com.hiddify.hiddify.privacy.NativeRegionalMode

@Composable
internal fun NativeRoutingModeChoices(
    mode: NativeRegionalMode,
    enabled: Boolean,
    handbookRouting: Boolean = false,
    fullTunnel: Boolean = false,
    onOpenApps: (NativeRegionalAppKind) -> Unit,
    canOpenApps: Boolean = enabled,
    onChange: (NativeRegionalMode) -> Unit,
) {
    val selectedMode = com.hiddify.hiddify.nativeprivacy.NativePrivacyControls.selectedRoutingMode(mode, handbookRouting, fullTunnel)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(stringResource(R.string.native_regional_mode), Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium)
        for (choice in listOf(NativeRegionalMode.RUSSIAN_BYPASS, NativeRegionalMode.SELECTED_PROXY, NativeRegionalMode.OFF)) {
            val title = when (choice) {
                NativeRegionalMode.RUSSIAN_BYPASS -> R.string.native_regional_bypass
                NativeRegionalMode.SELECTED_PROXY -> R.string.native_regional_selected
                NativeRegionalMode.OFF -> R.string.native_privacy_handbook
            }
            val description = when (choice) {
                NativeRegionalMode.RUSSIAN_BYPASS -> R.string.native_routing_bypass_description
                NativeRegionalMode.SELECTED_PROXY -> R.string.native_regional_selected_summary
                NativeRegionalMode.OFF -> R.string.native_privacy_handbook_summary
            }
            val appKind = when (choice) {
                NativeRegionalMode.RUSSIAN_BYPASS -> NativeRegionalAppKind.DIRECT
                NativeRegionalMode.SELECTED_PROXY -> NativeRegionalAppKind.PROXY
                NativeRegionalMode.OFF -> null
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Editing a list must not select its mode or change the independent network exception.
                Row(Modifier.weight(1f).selectable(selectedMode == choice, enabled = enabled, role = Role.RadioButton,
                    onClick = { onChange(choice) }).padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selectedMode == choice, onClick = null, enabled = enabled)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(description), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (appKind != null) IconButton(enabled = canOpenApps, onClick = { onOpenApps(appKind) }) {
                    Icon(painterResource(R.drawable.native_settings), stringResource(
                        if (appKind == NativeRegionalAppKind.DIRECT) R.string.native_routing_edit_direct
                        else R.string.native_routing_edit_proxy))
                }
            }
        }
    }
}
