package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.hiddify.hiddify.nativeui.NativeGlassDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeImportSummary

/** showImportPreview from the Dart reference. No source URLs/credentials enter the dialog. */
@Composable
internal fun NativeImportPreviewDialog(summary: NativeImportSummary, onDecision: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.native_import_preview)) },
        text = {
            Column(Modifier.widthIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.native_import_hint))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.native_import_servers, summary.servers))
                Text(stringResource(R.string.native_import_duplicates, summary.duplicates))
                Text(stringResource(R.string.native_import_unknown_types, summary.unknownTypes))
                Text(stringResource(R.string.native_import_overrides,
                    summary.overrides.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "—"))
                if (summary.insecureHttp) {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.native_import_plain_http), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { Button(onClick = { onDecision(true) }) { Text(stringResource(R.string.native_import_confirm)) } },
        dismissButton = { NativeTextButton(onClick = { onDecision(false) }) { Text(stringResource(android.R.string.cancel)) } },
    )
}
