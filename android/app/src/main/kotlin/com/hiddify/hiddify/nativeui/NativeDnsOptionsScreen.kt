package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeDnsOptions

private val DnsDraftSaver = listSaver<NativeDnsOptions, Any>(
    save = { listOf(it.remoteAddress, it.remoteStrategy, it.fakeDns, it.directAddress, it.directStrategy) },
    restore = { NativeDnsOptions(it[0] as String, it[1] as String, it[2] as Boolean, it[3] as String, it[4] as String) },
)
private val DnsStrategies = listOf(
    "" to R.string.native_core_strategy_auto,
    "prefer_ipv6" to R.string.native_core_strategy_prefer_ipv6,
    "prefer_ipv4" to R.string.native_core_strategy_prefer_ipv4,
    "ipv4_only" to R.string.native_core_strategy_ipv4_only,
    "ipv6_only" to R.string.native_core_strategy_ipv6_only,
)

@Composable
internal fun NativeDnsOptionsScreen(
    options: NativeDnsOptions?, busy: Boolean, canSave: Boolean,
    onBack: () -> Unit, onSave: (NativeDnsOptions) -> Unit, onOpenPrivacy: () -> Unit,
) {
    if (options == null) {
        Column {
            NativePageHeader(stringResource(R.string.native_core_dns), onBack)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    var draft by rememberSaveable(options, stateSaver = DnsDraftSaver) { mutableStateOf<NativeDnsOptions>(options) }
    var editor by rememberSaveable(options) { mutableStateOf("") }
    var address by rememberSaveable(options) { mutableStateOf("") }
    fun edit(key: String, value: String) { address = value; editor = key }
    fun strategyLabel(key: String) = DnsStrategies.firstOrNull { it.first == key }?.second ?: R.string.native_core_strategy_auto
    val valid = runCatching { draft.validated() }.isSuccess
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NativePageHeader(stringResource(R.string.native_core_dns), if (busy) null else onBack)
        NativeCard(Modifier.fillMaxWidth()) {
            NativePreferenceValueRow(R.string.native_core_remote_dns, draft.remoteAddress, !busy) { edit("remote", draft.remoteAddress) }
            NativePreferenceValueRow(R.string.native_core_remote_dns_strategy, stringResource(strategyLabel(draft.remoteStrategy)), !busy) { editor = "remote_strategy" }
            NativePreferenceSwitch(R.string.native_core_fake_dns, R.string.native_dns_fake_summary,
                R.drawable.native_shield, draft.fakeDns, !busy, { draft = draft.copy(fakeDns = it) })
            NativePreferenceValueRow(R.string.native_core_direct_dns, draft.directAddress, !busy) { edit("direct", draft.directAddress) }
            NativePreferenceValueRow(R.string.native_core_direct_dns_strategy, stringResource(strategyLabel(draft.directStrategy)), !busy) { editor = "direct_strategy" }
        }
        NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
            NativeSettingsLink(R.string.native_settings_dns, R.drawable.native_shield, onOpenPrivacy, enabled = !busy)
        }
        Text(stringResource(if (canSave) R.string.native_settings_reconnect_note else R.string.native_dns_disconnect),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!valid) Text(stringResource(R.string.native_dns_invalid), color = MaterialTheme.colorScheme.error)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        NativeButton(onClick = { onSave(draft) }, enabled = canSave && !busy && valid && draft != options,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.native_dns_save)) }
    }
    if (editor.isNotEmpty()) {
        val remote = editor.startsWith("remote")
        val strategy = editor.endsWith("strategy")
        val title = if (strategy) {
            if (remote) R.string.native_core_remote_dns_strategy else R.string.native_core_direct_dns_strategy
        } else if (remote) R.string.native_core_remote_dns else R.string.native_core_direct_dns
        val addressValid = address.trim().isNotEmpty() && address.length <= 2048 && address.none(Char::isISOControl)
        AlertDialog(onDismissRequest = { if (!busy) editor = "" }, title = { Text(stringResource(title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (strategy) DnsStrategies.forEach { (key, label) ->
                        val selected = (if (remote) draft.remoteStrategy else draft.directStrategy) == key
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.RadioButton) {
                            draft = if (remote) draft.copy(remoteStrategy = key) else draft.copy(directStrategy = key)
                            editor = ""
                        }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, onClick = null, enabled = !busy)
                            Text(stringResource(label), Modifier.padding(start = 8.dp))
                        }
                    } else {
                        OutlinedTextField(address, { if (it.length <= 2048) address = it },
                            enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            isError = !addressValid, label = { Text(stringResource(title)) })
                        val presets = if (remote) listOf("local", "tcp://8.8.8.8", "tcp://1.1.1.1", "https://1.1.1.1/dns-query", "https://dns.cloudflare.com/dns-query", "tcp://4.4.2.2")
                            else listOf("local", "udp://223.5.5.5", "udp://1.1.1.1", "udp://1.1.1.2", "tcp://1.1.1.1", "https://1.1.1.1/dns-query", "https://dns.cloudflare.com/dns-query", "4.4.2.2", "8.8.8.8")
                        presets.forEach { value -> TextButton(onClick = { address = value }, enabled = !busy) { Text(value) } }
                    }
                }
            },
            confirmButton = {
                if (!strategy) TextButton(onClick = {
                    draft = if (remote) draft.copy(remoteAddress = address.trim()) else draft.copy(directAddress = address.trim())
                    editor = ""
                }, enabled = !busy && addressValid) { Text(stringResource(R.string.native_profile_save)) }
            },
            dismissButton = { TextButton(onClick = { editor = "" }, enabled = !busy) { Text(stringResource(android.R.string.cancel)) } })
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "DNS · dark", widthDp = 390, heightDp = 844)
@Composable
private fun DnsPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.DARK) {
        NativeAtmosphere { Column(Modifier.padding(horizontal = 20.dp)) {
            NativeDnsOptionsScreen(NativeDnsOptions(), false, true, {}, {}, {})
        } }
    }
}
