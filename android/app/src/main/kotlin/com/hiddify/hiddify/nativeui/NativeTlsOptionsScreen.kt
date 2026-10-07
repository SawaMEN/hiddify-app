package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeTlsOptions

private val TlsDraftSaver = listSaver<NativeTlsOptions, Any>(
    save = { listOf(it.fragment, it.fragmentSize, it.fragmentSleep, it.mixedSniCase, it.padding, it.paddingSize) },
    restore = { NativeTlsOptions(it[0] as Boolean, it[1] as String, it[2] as String,
        it[3] as Boolean, it[4] as Boolean, it[5] as String) },
)

@Composable
internal fun NativeTlsOptionsScreen(
    options: NativeTlsOptions?, busy: Boolean, canSave: Boolean,
    onBack: () -> Unit, onSave: (NativeTlsOptions) -> Unit,
) {
    if (options == null) {
        Column {
            NativePageHeader(stringResource(R.string.native_core_tls), onBack)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        return
    }
    var draft by rememberSaveable(options, stateSaver = TlsDraftSaver) { mutableStateOf<NativeTlsOptions>(options) }
    var editor by rememberSaveable(options) { mutableStateOf("") }
    var range by rememberSaveable(options) { mutableStateOf("") }
    fun edit(key: String, value: String) { editor = key; range = value }
    val enabled = !busy && draft.fragment
    val valid = runCatching { draft.validated() }.isSuccess
    val notSet = stringResource(R.string.native_tls_not_set)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NativePageHeader(stringResource(R.string.native_core_tls), if (busy) null else onBack)
        NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
            Text(stringResource(R.string.native_tls_summary), Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
        NativeCard(Modifier.fillMaxWidth()) {
            NativePreferenceSwitch(R.string.native_core_tls_fragment, R.string.native_tls_fragment_summary,
                R.drawable.native_route, draft.fragment, !busy, { draft = draft.copy(fragment = it) })
            NativePreferenceValueRow(R.string.native_core_tls_fragment_size, draft.fragmentSize.ifEmpty { notSet }, enabled,
                R.drawable.native_route) { edit("size", draft.fragmentSize) }
            NativePreferenceValueRow(R.string.native_core_tls_fragment_sleep, draft.fragmentSleep.ifEmpty { notSet }, enabled,
                R.drawable.native_route) { edit("sleep", draft.fragmentSleep) }
            NativePreferenceSwitch(R.string.native_core_tls_mixed_sni, R.string.native_tls_sni_summary,
                R.drawable.native_shield, draft.mixedSniCase, enabled, { draft = draft.copy(mixedSniCase = it) })
            NativePreferenceSwitch(R.string.native_core_tls_padding, R.string.native_tls_padding_summary,
                R.drawable.native_layers, draft.padding, enabled, { draft = draft.copy(padding = it) })
            // Dart enables all subordinate controls with fragmentation, including the padding range.
            NativePreferenceValueRow(R.string.native_core_tls_padding_size, draft.paddingSize.ifEmpty { notSet }, enabled,
                R.drawable.native_layers) { edit("padding", draft.paddingSize) }
        }
        Text(stringResource(if (canSave) R.string.native_settings_reconnect_note else R.string.native_tls_disconnect),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!valid) Text(stringResource(R.string.native_tls_invalid), color = MaterialTheme.colorScheme.error)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        NativeButton(onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth(),
            enabled = !busy && canSave && valid && draft != options) { Text(stringResource(R.string.native_tls_save)) }
    }
    if (editor.isNotEmpty()) {
        val title = when (editor) {
            "size" -> R.string.native_core_tls_fragment_size
            "sleep" -> R.string.native_core_tls_fragment_sleep
            else -> R.string.native_core_tls_padding_size
        }
        val normalized = runCatching { NativeTlsOptions.normalizeRange(range) }.getOrNull()
        AlertDialog(onDismissRequest = { if (!busy) editor = "" }, title = { Text(stringResource(title)) },
            text = {
                OutlinedTextField(range, { if (it.length <= 21) range = it }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.native_tls_range)) }, isError = normalized == null,
                    supportingText = { Text(stringResource(R.string.native_tls_invalid)) })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (normalized != null) {
                        draft = when (editor) {
                            "size" -> draft.copy(fragmentSize = normalized)
                            "sleep" -> draft.copy(fragmentSleep = normalized)
                            else -> draft.copy(paddingSize = normalized)
                        }
                        editor = ""
                    }
                }, enabled = !busy && normalized != null) { Text(stringResource(R.string.native_profile_save)) }
            },
            dismissButton = { TextButton(onClick = { editor = "" }, enabled = !busy) {
                Text(stringResource(android.R.string.cancel))
            } })
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "TLS · dark", widthDp = 390, heightDp = 844)
@Composable
private fun TlsPreview() {
    NativeAppTheme(com.hiddify.hiddify.nativepreferences.NativeThemeMode.DARK) {
        NativeAtmosphere { Column(Modifier.padding(horizontal = 20.dp)) {
            NativeTlsOptionsScreen(NativeTlsOptions(fragment = true), false, true, {}, {})
        } }
    }
}
