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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeGeneralOptions

private val GeneralDraftSaver = listSaver<NativeGeneralOptions, Any>(
    save = { listOf(it.balancer, it.resolveDestination, it.logLevel, it.testUrl, it.intervalSeconds, it.clashPort, it.useXray) },
    restore = { NativeGeneralOptions(it[0] as String, it[1] as Boolean, it[2] as String,
        it[3] as String, it[4] as Int, it[5] as Int, it[6] as Boolean) },
)
private val BalancerLabels = listOf("round-robin" to R.string.native_core_balancer_round_robin,
    "consistent-hashing" to R.string.native_core_balancer_consistent, "sticky-sessions" to R.string.native_core_balancer_sticky)

@Composable
internal fun NativeGeneralOptionsScreen(
    options: NativeGeneralOptions?, busy: Boolean, canSave: Boolean,
    onBack: () -> Unit, onSave: (NativeGeneralOptions) -> Unit,
) {
    if (options == null) {
        Column {
            NativePageHeader(stringResource(R.string.native_general_options_title), onBack)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    var draft by rememberSaveable(options, stateSaver = GeneralDraftSaver) { mutableStateOf<NativeGeneralOptions>(options) }
    var editor by rememberSaveable(options) { mutableStateOf("") }
    var text by rememberSaveable(options) { mutableStateOf("") }
    var minutes by rememberSaveable(options) { mutableStateOf(10f) }
    fun edit(key: String, value: String) { editor = key; text = value }
    val valid = runCatching { draft.validated() }.isSuccess
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NativePageHeader(stringResource(R.string.native_general_options_title), if (busy) null else onBack)
        NativeCard(Modifier.fillMaxWidth()) {
            NativePreferenceValueRow(R.string.native_core_balancer,
                stringResource(BalancerLabels.first { it.first == draft.balancer }.second), !busy,
                R.drawable.native_route) { editor = "balancer" }
            NativePreferenceSwitch(R.string.native_core_resolve_destination, R.string.native_general_resolve_summary,
                R.drawable.native_route, draft.resolveDestination, !busy, { draft = draft.copy(resolveDestination = it) })
            NativePreferenceValueRow(R.string.native_core_log_level, draft.logLevel.uppercase(), !busy,
                R.drawable.native_logs) { editor = "log" }
            NativePreferenceValueRow(R.string.native_core_test_url, draft.testUrl, !busy,
                R.drawable.native_dns) { edit("url", draft.testUrl) }
            NativePreferenceValueRow(R.string.native_general_interval,
                stringResource(R.string.native_general_seconds, draft.intervalSeconds), !busy,
                R.drawable.native_settings) { minutes = (draft.intervalSeconds / 60f).coerceIn(1f, 60f); editor = "interval" }
            NativePreferenceValueRow(R.string.native_core_clash_api_port, draft.clashPort.toString(), !busy,
                R.drawable.native_settings) { edit("port", draft.clashPort.toString()) }
            NativePreferenceSwitch(R.string.native_core_use_xray, R.string.native_general_xray_summary,
                R.drawable.native_layers, draft.useXray, !busy, { draft = draft.copy(useXray = it) })
        }
        Text(stringResource(if (canSave) R.string.native_settings_reconnect_note else R.string.native_general_disconnect),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!valid) Text(stringResource(R.string.native_general_invalid), color = MaterialTheme.colorScheme.error)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        NativeButton(onClick = { onSave(draft) }, enabled = !busy && canSave && valid && draft != options,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.native_general_save)) }
    }
    if (editor.isNotEmpty()) {
        val title = when (editor) {
            "balancer" -> R.string.native_core_balancer
            "log" -> R.string.native_core_log_level
            "url" -> R.string.native_core_test_url
            "interval" -> R.string.native_general_interval
            else -> R.string.native_core_clash_api_port
        }
        val choices = when (editor) {
            "balancer" -> BalancerLabels.map { it.first }
            "log" -> listOf("trace", "debug", "info", "warn")
            else -> emptyList()
        }
        val inputValid = when (editor) {
            "url" -> runCatching { draft.copy(testUrl = text).validated() }.isSuccess
            "port" -> text.toIntOrNull() in 1..65535
            else -> true
        }
        AlertDialog(onDismissRequest = { if (!busy) editor = "" }, title = { Text(stringResource(title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (choices.isNotEmpty()) choices.forEach { choice ->
                        val selected = (if (editor == "balancer") draft.balancer else draft.logLevel) == choice
                        Row(Modifier.fillMaxWidth().clickable(enabled = !busy, role = Role.RadioButton) {
                            draft = if (editor == "balancer") draft.copy(balancer = choice) else draft.copy(logLevel = choice)
                            editor = ""
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, onClick = null, enabled = !busy)
                            Text(if (editor == "balancer") stringResource(BalancerLabels.first { it.first == choice }.second)
                                else choice.uppercase(), Modifier.padding(start = 8.dp))
                        }
                    } else if (editor == "interval") {
                        Text(stringResource(R.string.native_general_minutes, minutes.toInt()))
                        Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 1f..60f,
                            steps = 58, enabled = !busy)
                        TextButton(onClick = { minutes = 10f }, enabled = !busy) { Text(stringResource(R.string.native_general_reset_interval)) }
                    } else {
                        OutlinedTextField(text, { value ->
                            if (editor == "port") { if (value.length <= 5 && value.all(Char::isDigit)) text = value }
                            else if (value.length <= 2048) text = value
                        }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                            label = { Text(stringResource(title)) }, isError = !inputValid)
                        if (editor == "url") listOf("http://connectivitycheck.gstatic.com/generate_204", "http://www.gstatic.com/generate_204",
                            "https://www.gstatic.com/generate_204", "https://redirector.googlevideo.com/generate_204", "http://cp.cloudflare.com",
                            "http://kernel.org", "http://detectportal.firefox.com", "http://captive.apple.com/hotspot-detect.html",
                            "https://1.1.1.1", "http://1.1.1.1").forEach { url ->
                            TextButton(onClick = { text = url }, enabled = !busy) { Text(url) }
                        }
                    }
                }
            }, confirmButton = {
                if (choices.isEmpty()) TextButton(onClick = {
                    draft = when (editor) {
                        "url" -> draft.copy(testUrl = text.trim())
                        "interval" -> draft.copy(intervalSeconds = minutes.toInt() * 60)
                        else -> draft.copy(clashPort = text.toInt())
                    }
                    editor = ""
                }, enabled = !busy && inputValid) { Text(stringResource(R.string.native_profile_save)) }
            }, dismissButton = { TextButton(onClick = { editor = "" }, enabled = !busy) { Text(stringResource(android.R.string.cancel)) } })
    }
}
