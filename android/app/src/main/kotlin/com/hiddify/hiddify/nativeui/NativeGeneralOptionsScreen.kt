package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeGeneralOptionField
import com.hiddify.hiddify.nativecore.NativeGeneralOptions

private val BalancerLabels = listOf("round-robin" to R.string.native_core_balancer_round_robin,
    "consistent-hashing" to R.string.native_core_balancer_consistent, "sticky-sessions" to R.string.native_core_balancer_sticky)
private val TestUrls = listOf("http://connectivitycheck.gstatic.com/generate_204", "http://www.gstatic.com/generate_204",
    "https://www.gstatic.com/generate_204", "https://redirector.googlevideo.com/generate_204", "http://cp.cloudflare.com",
    "http://kernel.org", "http://detectportal.firefox.com", "http://captive.apple.com/hotspot-detect.html",
    "https://1.1.1.1", "http://1.1.1.1")

@Composable
internal fun NativeGeneralOptionsScreen(
    options: NativeGeneralOptions?, busy: Boolean, canSave: Boolean,
    loadFailed: Boolean, onRetry: () -> Unit,
    onBack: () -> Unit, onSave: (NativeGeneralOptionField, String) -> Unit,
) {
    var editor by rememberSaveable { mutableStateOf<NativeGeneralOptionField?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf(10f) }
    val enabled = !busy && canSave
    fun save(field: NativeGeneralOptionField, input: String) {
        if (!enabled) return
        onSave(field, input)
        editor = null
    }
    fun edit(field: NativeGeneralOptionField) {
        if (options == null) return
        text = field.value(options).toString()
        minutes = (options.intervalSeconds / 60f).coerceIn(1f, 60f)
        editor = field
    }
    Column {
        NativePageHeader(stringResource(R.string.native_general_options_title), onBack)
        if ((options == null && !loadFailed) || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (options == null && loadFailed) Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.native_general_settings_failed), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.native_profiles_retry)) }
        }
        if (options != null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            GeneralPreference(R.string.native_core_balancer, R.drawable.native_general_balance,
                stringResource(BalancerLabels.first { it.first == options.balancer }.second), enabled) { edit(NativeGeneralOptionField.BALANCER) }
            GeneralPreference(R.string.native_core_resolve_destination, R.drawable.native_general_find_replace,
                null, enabled, options.resolveDestination) { save(NativeGeneralOptionField.RESOLVE_DESTINATION, (!options.resolveDestination).toString()) }
            GeneralPreference(R.string.native_core_log_level, R.drawable.native_general_description,
                options.logLevel.uppercase(), enabled) { edit(NativeGeneralOptionField.LOG_LEVEL) }
            GeneralPreference(R.string.native_core_test_url, R.drawable.native_quick_link,
                options.testUrl, enabled) { edit(NativeGeneralOptionField.TEST_URL) }
            GeneralPreference(R.string.native_general_interval, R.drawable.native_general_timer,
                if (options.intervalSeconds % 60 == 0) stringResource(R.string.native_general_minutes, options.intervalSeconds / 60)
                else stringResource(R.string.native_general_seconds, options.intervalSeconds), enabled) { edit(NativeGeneralOptionField.INTERVAL) }
            GeneralPreference(R.string.native_core_clash_api_port, R.drawable.native_general_api,
                options.clashPort.toString(), enabled) { edit(NativeGeneralOptionField.CLASH_PORT) }
            GeneralPreference(R.string.native_core_use_xray, R.drawable.native_general_extension,
                null, enabled, options.useXray) { save(NativeGeneralOptionField.USE_XRAY, (!options.useXray).toString()) }
        }
    }
    val field = editor
    if (field != null && options != null) {
        val title = when (field) {
            NativeGeneralOptionField.BALANCER -> R.string.native_core_balancer
            NativeGeneralOptionField.LOG_LEVEL -> R.string.native_core_log_level
            NativeGeneralOptionField.TEST_URL -> R.string.native_core_test_url
            NativeGeneralOptionField.INTERVAL -> R.string.native_general_interval
            else -> R.string.native_core_clash_api_port
        }
        val choices = when (field) {
            NativeGeneralOptionField.BALANCER -> BalancerLabels.map { it.first }
            NativeGeneralOptionField.LOG_LEVEL -> listOf("trace", "debug", "info", "warn")
            else -> emptyList()
        }
        val input = if (field == NativeGeneralOptionField.INTERVAL) (minutes.toInt() * 60).toString() else text
        val valid = runCatching { field.applyTo(options, input) }.isSuccess
        val focus = remember(field) { FocusRequester() }
        LaunchedEffect(field) {
            if (field == NativeGeneralOptionField.TEST_URL || field == NativeGeneralOptionField.CLASH_PORT) focus.requestFocus()
        }
        AlertDialog(onDismissRequest = { editor = null }, title = { Text(stringResource(title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (choices.isNotEmpty()) choices.forEach { choice ->
                        val selected = field.value(options) == choice
                        Row(Modifier.fillMaxWidth().selectable(selected, enabled, Role.RadioButton) { save(field, choice) }
                            .heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected, onClick = null, enabled = enabled)
                            Text(if (field == NativeGeneralOptionField.BALANCER) stringResource(BalancerLabels.first { it.first == choice }.second)
                                else choice.uppercase(), Modifier.padding(start = 8.dp))
                        }
                    } else if (field == NativeGeneralOptionField.INTERVAL) {
                        Text(stringResource(R.string.native_general_minutes, minutes.toInt()))
                        Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 1f..60f,
                            steps = 59, enabled = enabled)
                    } else {
                        NativeTextField(text, { value ->
                            if (field == NativeGeneralOptionField.CLASH_PORT) {
                                if (value.length <= 5 && value.all { it in '0'..'9' }) text = value
                            } else if (value.length <= 2048) text = value
                        }, modifier = Modifier.fillMaxWidth().focusRequester(focus), enabled = enabled, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                            keyboardOptions = KeyboardOptions(keyboardType = if (field == NativeGeneralOptionField.CLASH_PORT)
                                KeyboardType.Number else KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (valid) save(field, input) }),
                            isError = !valid, supportingText = {
                                if (!valid) Text(stringResource(R.string.native_general_invalid))
                            })
                        if (field == NativeGeneralOptionField.TEST_URL) {
                            val matches = TestUrls.filter { it.contains(text, ignoreCase = true) }
                            val suggestions = if (matches.size <= 1) (listOf(text) + TestUrls).distinct() else matches
                            suggestions.forEach { url ->
                                TextButton(onClick = { text = url }, enabled = enabled) { Text(url) }
                            }
                        }
                    }
                }
            }, confirmButton = {
                if (choices.isEmpty()) TextButton(onClick = { save(field, input) }, enabled = enabled && valid) {
                    Text(stringResource(android.R.string.ok))
                }
            }, dismissButton = {
                Row {
                    TextButton(onClick = { save(field, field.value(NativeGeneralOptions()).toString()) }, enabled = enabled) {
                        Text(stringResource(R.string.native_quick_reset))
                    }
                    TextButton(onClick = { editor = null }) { Text(stringResource(android.R.string.cancel)) }
                }
            })
    }
}

@Composable
private fun GeneralPreference(title: Int, icon: Int, value: String?, enabled: Boolean,
    checked: Boolean? = null, onClick: () -> Unit) {
    val interaction = if (checked == null) Modifier.clickable(enabled = enabled, onClick = onClick)
        else Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
    Row(Modifier.fillMaxWidth().then(interaction).heightIn(min = if (value == null) 56.dp else 72.dp)
        .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .38f)
        Icon(painterResource(icon), null, Modifier.size(24.dp), tint = tint)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
            if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
        if (checked != null) Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
