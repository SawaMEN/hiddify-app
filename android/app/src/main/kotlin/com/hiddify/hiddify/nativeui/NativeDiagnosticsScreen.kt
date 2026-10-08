package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.hiddify.hiddify.nativeui.NativeButton as Button
import com.hiddify.hiddify.nativeui.NativeCard as Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.hiddify.hiddify.nativeui.NativeOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativediagnostics.NativeDiagnosticDetail
import com.hiddify.hiddify.nativediagnostics.NativeDiagnosticOutcome
import com.hiddify.hiddify.nativediagnostics.NativeDiagnosticSnapshot
import com.hiddify.hiddify.nativediagnostics.NativeDiagnosticStage

@Composable
fun NativeDiagnosticsScreen(
    snapshot: NativeDiagnosticSnapshot?,
    busy: Boolean,
    report: String,
    onBack: () -> Unit,
    onRun: () -> Unit,
    onCancel: () -> Unit,
    onShareReport: (String) -> Unit,
) {
    var previewOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NativePageHeader(stringResource(R.string.native_diagnostics_title), onBack)
        Text(stringResource(R.string.native_diagnostics_summary))
        Button(onClick = onRun, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.native_diagnostics_run))
        }
        if (busy) {
            CircularProgressIndicator()
            TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
        }
        snapshot?.checks?.forEach { check ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(stageLabel(check.stage)), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(when (check.outcome) {
                            NativeDiagnosticOutcome.PASSED -> R.string.native_diagnostics_passed
                            NativeDiagnosticOutcome.FAILED -> R.string.native_diagnostics_failed
                            NativeDiagnosticOutcome.SKIPPED -> R.string.native_diagnostics_skipped
                        }),
                        color = if (check.outcome == NativeDiagnosticOutcome.FAILED) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(detailLabel(check.detail)))
                    check.httpStatus?.let { Text(stringResource(R.string.native_diagnostics_http_status, it)) }
                }
            }
        }
        Text(stringResource(R.string.native_diagnostics_report_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { previewOpen = true },
            enabled = !busy && !snapshot?.checks.isNullOrEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.native_diagnostics_preview)) }
    }
    if (previewOpen) {
        AlertDialog(
            onDismissRequest = { previewOpen = false },
            title = { Text(stringResource(R.string.native_diagnostics_preview)) },
            text = {
                SelectionContainer {
                    Text(
                        report,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onShareReport(report) }) {
                    Text(stringResource(R.string.native_diagnostics_share))
                }
            },
            dismissButton = {
                TextButton(onClick = { previewOpen = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

private fun stageLabel(stage: NativeDiagnosticStage): Int = when (stage) {
    NativeDiagnosticStage.NETWORK -> R.string.native_diagnostics_network
    NativeDiagnosticStage.PROFILE -> R.string.native_diagnostics_profile
    NativeDiagnosticStage.CORE -> R.string.native_diagnostics_core
    NativeDiagnosticStage.TUNNEL -> R.string.native_diagnostics_tunnel
}

private fun detailLabel(detail: NativeDiagnosticDetail): Int = when (detail) {
    NativeDiagnosticDetail.NETWORK_AVAILABLE -> R.string.native_diagnostics_network_available
    NativeDiagnosticDetail.NETWORK_UNAVAILABLE -> R.string.native_diagnostics_network_unavailable
    NativeDiagnosticDetail.PROFILE_AVAILABLE -> R.string.native_diagnostics_profile_available
    NativeDiagnosticDetail.PROFILE_MISSING -> R.string.native_diagnostics_profile_missing
    NativeDiagnosticDetail.CORE_AVAILABLE -> R.string.native_diagnostics_core_available
    NativeDiagnosticDetail.CORE_UNAVAILABLE -> R.string.native_diagnostics_core_unavailable
    NativeDiagnosticDetail.DISCONNECTED -> R.string.native_diagnostics_disconnected
    NativeDiagnosticDetail.PROBE_OK -> R.string.native_diagnostics_probe_ok
    NativeDiagnosticDetail.PROBE_FAILED -> R.string.native_diagnostics_probe_failed
    NativeDiagnosticDetail.INVALID_PROBE_URL -> R.string.native_diagnostics_invalid_url
}
