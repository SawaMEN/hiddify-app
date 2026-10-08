package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeDnsOptionField
import com.hiddify.hiddify.nativecore.NativeDnsOptions

private val DnsStrategies = listOf(
    "" to R.string.native_core_strategy_auto,
    "prefer_ipv6" to R.string.native_core_strategy_prefer_ipv6,
    "prefer_ipv4" to R.string.native_core_strategy_prefer_ipv4,
    "ipv4_only" to R.string.native_core_strategy_ipv4_only,
    "ipv6_only" to R.string.native_core_strategy_ipv6_only,
)
private val RemoteDnsPresets = listOf("local", "tcp://8.8.8.8", "tcp://1.1.1.1",
    "https://1.1.1.1/dns-query", "https://dns.cloudflare.com/dns-query", "tcp://4.4.2.2")
private val DirectDnsPresets = listOf("local", "udp://223.5.5.5", "udp://1.1.1.1", "udp://1.1.1.2",
    "tcp://1.1.1.1", "https://1.1.1.1/dns-query", "https://dns.cloudflare.com/dns-query", "4.4.2.2", "8.8.8.8")

@Composable
internal fun NativeDnsOptionsScreen(
    options: NativeDnsOptions?, busy: Boolean, canSave: Boolean, loadFailed: Boolean,
    onRetry: () -> Unit, onBack: () -> Unit, onSave: (NativeDnsOptionField, String) -> Unit,
) {
    var editor by rememberSaveable { mutableStateOf<NativeDnsOptionField?>(null) }
    var address by rememberSaveable { mutableStateOf("") }
    val enabled = !busy && canSave
    fun edit(field: NativeDnsOptionField) {
        if (options == null) return
        address = field.value(options).toString()
        editor = field
    }
    fun save(field: NativeDnsOptionField, input: String) {
        if (!enabled) return
        onSave(field, input)
        editor = null
    }
    fun strategyLabel(key: String) = DnsStrategies.firstOrNull { it.first == key }?.second ?: R.string.native_core_strategy_auto
    Column {
        NativePageHeader(stringResource(R.string.native_core_dns), onBack)
        if ((options == null && !loadFailed) || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (options == null && loadFailed) Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.native_settings_load_failed), color = MaterialTheme.colorScheme.error)
            NativeTextButton(onClick = onRetry) { Text(stringResource(R.string.native_profiles_retry)) }
        }
        if (options != null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            NativePreferenceTile(R.string.native_core_remote_dns, R.drawable.native_dns_vpn_lock,
                options.remoteAddress, enabled) { edit(NativeDnsOptionField.REMOTE_ADDRESS) }
            NativePreferenceTile(R.string.native_core_remote_dns_strategy, R.drawable.native_dns_sync_alt,
                stringResource(strategyLabel(options.remoteStrategy)), enabled) { edit(NativeDnsOptionField.REMOTE_STRATEGY) }
            NativePreferenceTile(R.string.native_core_fake_dns, R.drawable.native_dns_private_connectivity,
                null, enabled, options.fakeDns) { save(NativeDnsOptionField.FAKE_DNS, (!options.fakeDns).toString()) }
            NativePreferenceTile(R.string.native_core_direct_dns, R.drawable.native_dns_public,
                options.directAddress, enabled) { edit(NativeDnsOptionField.DIRECT_ADDRESS) }
            NativePreferenceTile(R.string.native_core_direct_dns_strategy, R.drawable.native_dns_sync_alt,
                stringResource(strategyLabel(options.directStrategy)), enabled) { edit(NativeDnsOptionField.DIRECT_STRATEGY) }
        }
    }
    val field = editor
    if (field != null && options != null) {
        val remote = field == NativeDnsOptionField.REMOTE_ADDRESS || field == NativeDnsOptionField.REMOTE_STRATEGY
        val strategy = field == NativeDnsOptionField.REMOTE_STRATEGY || field == NativeDnsOptionField.DIRECT_STRATEGY
        val title = if (strategy) {
            if (remote) R.string.native_core_remote_dns_strategy else R.string.native_core_direct_dns_strategy
        } else if (remote) R.string.native_core_remote_dns else R.string.native_core_direct_dns
        val valid = runCatching { field.applyTo(options, address) }.isSuccess
        val focus = remember(field) { FocusRequester() }
        LaunchedEffect(field) { if (!strategy) focus.requestFocus() }
        AlertDialog(onDismissRequest = { editor = null }, title = { Text(stringResource(title)) },
            text = {
                if (strategy) Column(Modifier.verticalScroll(rememberScrollState())) {
                    DnsStrategies.forEach { (key, label) ->
                        val selected = field.value(options) == key
                        Row(Modifier.fillMaxWidth().selectable(selected, enabled, Role.RadioButton) { save(field, key) }
                            .heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, onClick = null, enabled = enabled)
                            Text(stringResource(label), Modifier.padding(start = 8.dp))
                        }
                    }
                } else NativePreferenceInput(address, { if (it.length <= 2048) address = it },
                    possibleValues = if (remote) RemoteDnsPresets else DirectDnsPresets,
                    enabled = enabled, valid = valid, invalidMessage = stringResource(R.string.native_dns_invalid),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    onDone = { save(field, address) }, modifier = Modifier.focusRequester(focus))
            }, confirmButton = {
                if (!strategy) NativeTextButton(onClick = { save(field, address) }, enabled = enabled && valid) {
                    Text(stringResource(android.R.string.ok))
                }
            }, dismissButton = {
                Row {
                    NativeTextButton(onClick = { save(field, field.value(NativeDnsOptions()).toString()) }, enabled = enabled) {
                        Text(stringResource(R.string.native_quick_reset))
                    }
                    NativeTextButton(onClick = { editor = null }) { Text(stringResource(android.R.string.cancel)) }
                }
            })
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "DNS · dark", widthDp = 390, heightDp = 844)
@Composable
private fun DnsPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.DARK) {
        NativeAtmosphere { Column(Modifier.padding(horizontal = 20.dp)) {
            NativeDnsOptionsScreen(NativeDnsOptions(), false, true, false, {}, {}, { _, _ -> })
        } }
    }
}
