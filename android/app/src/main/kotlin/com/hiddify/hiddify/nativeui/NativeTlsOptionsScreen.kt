package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeTlsOptionField
import com.hiddify.hiddify.nativecore.NativeTlsOptions

@Composable
internal fun NativeTlsOptionsScreen(
    options: NativeTlsOptions?, busy: Boolean, canSave: Boolean, loadFailed: Boolean,
    onRetry: () -> Unit, onBack: () -> Unit, onSave: (NativeTlsOptionField, String) -> Unit,
) {
    var editor by rememberSaveable { mutableStateOf<NativeTlsOptionField?>(null) }
    var range by rememberSaveable { mutableStateOf("") }
    val enabled = !busy && canSave
    val dependentEnabled = enabled && options?.fragment == true
    fun format(value: String) = runCatching { NativeTlsOptions.normalizeRange(value, allowEmpty = true) }.getOrDefault(value)
    fun edit(field: NativeTlsOptionField) {
        if (options == null || !dependentEnabled) return
        range = format(field.value(options).toString())
        editor = field
    }
    fun save(field: NativeTlsOptionField, value: String) {
        if (!enabled || (field != NativeTlsOptionField.FRAGMENT && !dependentEnabled)) return
        onSave(field, value)
        editor = null
    }
    val notSet = stringResource(R.string.native_tls_not_set)
    Column {
        NativePageHeader(stringResource(R.string.native_core_tls), onBack)
        if ((options == null && !loadFailed) || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (options == null && loadFailed) Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.native_settings_load_failed), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.native_profiles_retry)) }
        }
        if (options != null) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            NativePreferenceTile(R.string.native_core_tls_fragment, R.drawable.native_tls_content_cut,
                null, enabled, options.fragment) { save(NativeTlsOptionField.FRAGMENT, (!options.fragment).toString()) }
            NativePreferenceTile(R.string.native_core_tls_fragment_size, R.drawable.native_tls_straighten,
                format(options.fragmentSize).ifEmpty { notSet }, dependentEnabled) { edit(NativeTlsOptionField.FRAGMENT_SIZE) }
            NativePreferenceTile(R.string.native_core_tls_fragment_sleep, R.drawable.native_tls_snooze,
                format(options.fragmentSleep).ifEmpty { notSet }, dependentEnabled) { edit(NativeTlsOptionField.FRAGMENT_SLEEP) }
            NativePreferenceTile(R.string.native_core_tls_mixed_sni, R.drawable.native_tls_text_fields,
                null, dependentEnabled, options.mixedSniCase) { save(NativeTlsOptionField.MIXED_SNI_CASE, (!options.mixedSniCase).toString()) }
            NativePreferenceTile(R.string.native_core_tls_padding, R.drawable.native_tls_expand,
                null, dependentEnabled, options.padding) { save(NativeTlsOptionField.PADDING, (!options.padding).toString()) }
            // Dart gates the padding range on fragmentation, regardless of the padding switch.
            // Its padding row uses format(), so an imported empty range has an empty subtitle.
            NativePreferenceTile(R.string.native_core_tls_padding_size, R.drawable.native_tls_straighten,
                format(options.paddingSize), dependentEnabled) { edit(NativeTlsOptionField.PADDING_SIZE) }
        }
    }
    val field = editor
    if (field != null && options != null) {
        val title = when (field) {
            NativeTlsOptionField.FRAGMENT_SIZE -> R.string.native_core_tls_fragment_size
            NativeTlsOptionField.FRAGMENT_SLEEP -> R.string.native_core_tls_fragment_sleep
            else -> R.string.native_core_tls_padding_size
        }
        val valid = runCatching { field.applyTo(options, range) }.isSuccess
        val focus = remember(field) { FocusRequester() }
        LaunchedEffect(field) { focus.requestFocus() }
        AlertDialog(onDismissRequest = { editor = null }, title = { Text(stringResource(title)) },
            text = {
                NativePreferenceInput(range, { if (it.length <= 21) range = it }, possibleValues = emptyList(),
                    enabled = dependentEnabled, valid = valid, invalidMessage = stringResource(R.string.native_tls_invalid),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    onDone = { save(field, range) }, modifier = Modifier.focusRequester(focus))
            }, confirmButton = {
                TextButton(onClick = { save(field, range) }, enabled = dependentEnabled && valid) {
                    Text(stringResource(android.R.string.ok))
                }
            }, dismissButton = {
                Row {
                    TextButton(onClick = { save(field, field.value(NativeTlsOptions()).toString()) }, enabled = dependentEnabled) {
                        Text(stringResource(R.string.native_quick_reset))
                    }
                    TextButton(onClick = { editor = null }) { Text(stringResource(android.R.string.cancel)) }
                }
            })
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "TLS · dark", widthDp = 390, heightDp = 844)
@Composable
private fun TlsPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.DARK) {
        NativeAtmosphere { Column(Modifier.padding(horizontal = 20.dp)) {
            NativeTlsOptionsScreen(NativeTlsOptions(fragment = true), false, true, false, {}, {}, { _, _ -> })
        } }
    }
}
