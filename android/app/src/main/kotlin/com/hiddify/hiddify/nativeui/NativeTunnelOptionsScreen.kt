package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import com.hiddify.hiddify.nativeui.NativeGlassDialog as AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.hiddify.hiddify.nativeui.NativeTextField as OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeTunnelOptions

private val TunnelModeLabels = listOf("ipv4_only" to R.string.native_tunnel_ipv4_only,
    "prefer_ipv4" to R.string.native_tunnel_prefer_ipv4, "prefer_ipv6" to R.string.native_tunnel_prefer_ipv6,
    "ipv6_only" to R.string.native_tunnel_ipv6_only)

@Composable
internal fun NativeTunnelOptionsScreen(options: NativeTunnelOptions?, busy: Boolean, canSave: Boolean,
    onBack: () -> Unit, onSave: (NativeTunnelOptions) -> Unit) {
    if (options == null) {
        Column {
            NativePageHeader(stringResource(R.string.native_tunnel_title), onBack)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    var mode by rememberSaveable(options) { mutableStateOf(options.ipv6Mode) }
    var mtu by rememberSaveable(options) { mutableStateOf(options.mtu.toString()) }
    var editor by rememberSaveable(options) { mutableStateOf("") }
    var input by rememberSaveable(options) { mutableStateOf("") }
    val draft = NativeTunnelOptions(mode, mtu.toIntOrNull() ?: 0)
    val valid = runCatching { draft.validated() }.isSuccess
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NativePageHeader(stringResource(R.string.native_tunnel_title), if (busy) null else onBack)
        NativeCard(Modifier.fillMaxWidth()) {
            NativePreferenceValueRow(R.string.native_tunnel_ipv6, stringResource(TunnelModeLabels.first { it.first == mode }.second),
                !busy, R.drawable.native_dns) { editor = "ipv6" }
            NativePreferenceValueRow(R.string.native_core_mtu, mtu, !busy, R.drawable.native_route) { input = mtu; editor = "mtu" }
        }
        Text(stringResource(R.string.native_tunnel_summary), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(if (canSave) R.string.native_settings_reconnect_note else R.string.native_tunnel_disconnect),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!valid) Text(stringResource(R.string.native_tunnel_invalid), color = MaterialTheme.colorScheme.error)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        NativeButton(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth(),
            enabled = !busy && canSave && valid && draft != options) { Text(stringResource(R.string.native_tunnel_save)) }
    }
    if (editor.isNotEmpty()) {
        val ipv6 = editor == "ipv6"
        val minimum = if (mode == "ipv4_only") 576 else 1280
        val inputValid = input.toIntOrNull() in minimum..65535
        AlertDialog(onDismissRequest = { if (!busy) editor = "" },
            title = { Text(stringResource(if (ipv6) R.string.native_tunnel_ipv6 else R.string.native_core_mtu)) },
            text = {
                Column {
                    if (ipv6) TunnelModeLabels.forEach { (key, label) ->
                        Row(Modifier.fillMaxWidth().selectable(selected = mode == key, enabled = !busy,
                            role = Role.RadioButton, onClick = { mode = key; editor = "" }).padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(mode == key, onClick = null, enabled = !busy)
                            Text(stringResource(label), Modifier.padding(start = 12.dp))
                        }
                    } else Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                      OutlinedTextField(input, { if (it.length <= 5 && it.all(Char::isDigit)) input = it },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true, isError = !inputValid,
                        label = { Text(stringResource(R.string.native_core_mtu)) },
                        supportingText = { if (!inputValid) Text(stringResource(R.string.native_tunnel_invalid)) })
                      Text(stringResource(R.string.native_mtu_presets_hint), style = MaterialTheme.typography.bodySmall)
                      com.hiddify.hiddify.nativecore.NativeTunnelMtuPresets.values.forEach { value ->
                          NativeTextButton(onClick = { input = value.toString() }, enabled = !busy,
                              modifier = Modifier.fillMaxWidth()) { Text(value.toString()) }
                      }
                    }
                }
            }, confirmButton = {
                if (!ipv6) NativeTextButton(onClick = { mtu = input.toInt().toString(); editor = "" },
                    enabled = !busy && inputValid) { Text(stringResource(R.string.native_profile_save)) }
            }, dismissButton = { NativeTextButton(onClick = { editor = "" }, enabled = !busy) {
                Text(stringResource(android.R.string.cancel))
            } })
    }
}
