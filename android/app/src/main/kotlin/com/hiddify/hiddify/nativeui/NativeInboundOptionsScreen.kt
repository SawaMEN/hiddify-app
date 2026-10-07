package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeInboundOptions

private val InboundDraftSaver = listSaver<NativeInboundOptions, Any>(
    save = { listOf(it.strictRoute, it.tunImplementation, it.mixedEnabled, it.mixedPort,
        it.directEnabled, it.directPort, it.allowLan, it.lanPassword) },
    restore = { NativeInboundOptions(it[0] as Boolean, it[1] as String, it[2] as Boolean,
        it[3] as Int, it[4] as Boolean, it[5] as Int, it[6] as Boolean, it[7] as String) },
)

@Composable
internal fun NativeInboundOptionsScreen(
    options: NativeInboundOptions, busy: Boolean, canSave: Boolean, onBack: () -> Unit,
    onSave: (NativeInboundOptions) -> Unit, onOpenVpnOptions: () -> Unit,
) {
    var value by rememberSaveable(options, stateSaver = InboundDraftSaver) { mutableStateOf(options) }
    var mixedPort by rememberSaveable(options) { mutableStateOf(options.mixedPort.toString()) }
    var directPort by rememberSaveable(options) { mutableStateOf(options.directPort.toString()) }
    val draft = value.copy(mixedPort = mixedPort.toIntOrNull() ?: 0, directPort = directPort.toIntOrNull() ?: 0)
    val valid = runCatching { draft.validated() }.isSuccess
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NativePageHeader(stringResource(R.string.native_inbound_title), if (busy) null else onBack)
        NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
            NativeSettingsLink(R.string.native_current_mode, R.drawable.native_settings, onOpenVpnOptions, enabled = !busy)
        }
        NativeCard(Modifier.fillMaxWidth()) {
            NativePreferenceSwitch(R.string.native_core_strict_route, R.string.native_inbound_strict_summary,
                R.drawable.native_route, value.strictRoute, !busy, { value = value.copy(strictRoute = it) })
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.native_core_tun_stack), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("mixed", "system", "gvisor").forEach { stack ->
                        FilterChip(selected = value.tunImplementation == stack, enabled = !busy,
                            onClick = { value = value.copy(tunImplementation = stack) }, label = { Text(stack) })
                    }
                }
            }
        }
        NativeCard(Modifier.fillMaxWidth()) {
            NativePreferenceSwitch(R.string.native_core_mixed_port_enabled, R.string.native_inbound_mixed_summary,
                R.drawable.native_route, value.mixedEnabled, !busy, { value = value.copy(mixedEnabled = it) })
            PortField(R.string.native_core_mixed_port, mixedPort, !busy) { mixedPort = it }
            NativePreferenceSwitch(R.string.native_core_direct_port_enabled, R.string.native_inbound_direct_summary,
                R.drawable.native_route, value.directEnabled, !busy, { value = value.copy(directEnabled = it) })
            PortField(R.string.native_core_direct_port, directPort, !busy) { directPort = it }
        }
        NativeCard(Modifier.fillMaxWidth()) {
            NativePreferenceSwitch(R.string.native_inbound_lan, R.string.native_inbound_lan_summary,
                R.drawable.native_route, value.allowLan, !busy, { value = value.copy(allowLan = it) })
            OutlinedTextField(value = value.lanPassword, onValueChange = { value = value.copy(lanPassword = it.take(128)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = !busy,
                label = { Text(stringResource(R.string.native_inbound_password)) },
                supportingText = { Text(stringResource(R.string.native_inbound_password_hint)) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        }
        Text(stringResource(if (canSave) R.string.native_settings_reconnect_note else R.string.native_inbound_disconnect),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!valid) Text(stringResource(R.string.native_inbound_invalid), color = MaterialTheme.colorScheme.error)
        NativeButton(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth(),
            enabled = !busy && canSave && valid && draft != options) { Text(stringResource(R.string.native_inbound_save)) }
    }
}

@Composable
private fun PortField(title: Int, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { input -> if (input.length <= 5 && input.all(Char::isDigit)) onChange(input) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        enabled = enabled, label = { Text(stringResource(title)) }, singleLine = true,
        isError = value.toIntOrNull() !in 1..65535, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@androidx.compose.ui.tooling.preview.Preview(name = "Inbound · dark", widthDp = 390, heightDp = 844)
@Composable
private fun InboundPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.DARK) {
        NativeAtmosphere {
            Column(Modifier.padding(horizontal = 20.dp)) {
                NativeInboundOptionsScreen(NativeInboundOptions(), false, true, {}, {}, {})
            }
        }
    }
}
