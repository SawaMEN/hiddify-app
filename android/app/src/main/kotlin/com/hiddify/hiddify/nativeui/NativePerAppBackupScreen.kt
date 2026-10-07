package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.hiddify.hiddify.nativeui.NativeOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativerouting.NativePerAppBackup

@Composable
fun NativePerAppBackupScreen(
    busy: Boolean,
    canImport: Boolean,
    pendingImport: NativePerAppBackup?,
    onBack: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportFile: () -> Unit,
    onExportClipboard: () -> Unit,
    onExportFile: () -> Unit,
    onConfirmImport: () -> Unit,
    onDismissImport: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.native_per_app_backup_title), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text(stringResource(R.string.native_back)) }
        }
        Text(stringResource(R.string.native_per_app_backup_summary))
        if (busy) CircularProgressIndicator()
        if (!canImport) Text(stringResource(R.string.native_per_app_disconnect))
        OutlinedButton(onClick = onImportClipboard, enabled = canImport && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.native_per_app_import_clipboard))
        }
        OutlinedButton(onClick = onImportFile, enabled = canImport && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.native_per_app_import_file))
        }
        OutlinedButton(onClick = onExportClipboard, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.native_per_app_export_clipboard))
        }
        OutlinedButton(onClick = onExportFile, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.native_per_app_export_file))
        }
    }
    if (pendingImport != null) {
        AlertDialog(
            onDismissRequest = onDismissImport,
            title = { Text(stringResource(R.string.native_per_app_restore_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.native_per_app_restore_summary))
                    Text(stringResource(R.string.native_per_app_restore_include,
                        pendingImport.include.selected.size, pendingImport.include.deselected.size))
                    Text(stringResource(R.string.native_per_app_restore_exclude,
                        pendingImport.exclude.selected.size, pendingImport.exclude.deselected.size))
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmImport, enabled = canImport && !busy) {
                    Text(stringResource(R.string.native_per_app_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissImport) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
