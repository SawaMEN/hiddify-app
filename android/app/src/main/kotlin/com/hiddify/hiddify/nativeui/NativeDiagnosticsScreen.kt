package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hiddify.hiddify.nativeui.NativeGlassDialog as AlertDialog
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativediagnostics.*

@Composable
fun NativeDiagnosticsScreen(snapshot: NativeDiagnosticSnapshot?, busy: Boolean, report: String,
    onBack: () -> Unit, onRun: () -> Unit, onShareReport: (String) -> Unit,
    connected: Boolean = false, sessionKey: String = "") {
    val scope = rememberCoroutineScope()
    var speedJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var speedBusy by remember { mutableStateOf(false) }
    var speedMbps by remember { mutableStateOf<String?>(null) }
    var speedFailed by remember { mutableStateOf(false) }
    LaunchedEffect(connected, sessionKey) { speedJob?.cancel(); speedMbps = null; speedFailed = false }
    var previewOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        NativePageHeader(stringResource(R.string.native_diagnostics_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            NativeButton(onRun, Modifier.fillMaxWidth(), enabled = !busy && !speedBusy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.native_diagnostic_play), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.native_diagnostics_run))
            }
            Spacer(Modifier.height(16.dp))
            NativeOutlinedButton(onClick = {
                if (speedBusy) speedJob?.cancel() else speedJob = scope.launch {
                    speedBusy = true; speedFailed = false; speedMbps = null
                    try {
                        val sample = com.hiddify.hiddify.nativecore.NativeSpeedTestRepository.download()
                        speedMbps = String.format(java.util.Locale.getDefault(), "%.1f", sample.megabitsPerSecond)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                      catch (_: Exception) { speedFailed = true }
                    finally { speedBusy = false }
                }
            }, enabled = connected && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (speedBusy) R.string.native_speed_test_cancel else R.string.native_speed_test_run))
            }
            Text(stringResource(R.string.native_speed_test_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            speedMbps?.let { Text(stringResource(R.string.native_speed_test_result, it), style = MaterialTheme.typography.titleLarge) }
            if (speedFailed) Text(stringResource(R.string.native_speed_test_failed), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            snapshot?.checks?.forEach { check ->
                ListItem(headlineContent = { Text(stringResource(stageLabel(check.stage))) },
                    supportingContent = { Text(stringResource(detailLabel(check.detail))) },
                    trailingContent = { Text(stringResource(when (check.outcome) {
                        NativeDiagnosticOutcome.PASSED -> R.string.native_diagnostics_passed
                        NativeDiagnosticOutcome.FAILED -> R.string.native_diagnostics_failed
                        NativeDiagnosticOutcome.SKIPPED -> R.string.native_diagnostics_skipped
                    })) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.native_diagnostics_report_hint))
            NativeOutlinedButton(onClick = { previewOpen = true }, enabled = !busy && !snapshot?.checks.isNullOrEmpty(),
                modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.native_report_share), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.native_diagnostics_share))
            }
        }
    }
    if (previewOpen) AlertDialog(onDismissRequest = { previewOpen = false },
        title = { Text(stringResource(R.string.native_diagnostics_preview)) },
        text = { SelectionContainer { Text(report, Modifier.widthIn(max = 500.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { NativeButton(onClick = { onShareReport(report) }) { Text(stringResource(R.string.native_diagnostics_share)) } },
        dismissButton = { NativeTextButton(onClick = { previewOpen = false }) { Text(stringResource(android.R.string.cancel)) } })
}

private fun stageLabel(stage: NativeDiagnosticStage) = when (stage) {
    NativeDiagnosticStage.NETWORK -> R.string.native_diagnostics_network
    NativeDiagnosticStage.DNS -> R.string.native_diagnostics_dns
    NativeDiagnosticStage.SERVER -> R.string.native_diagnostics_server
    NativeDiagnosticStage.PROFILE -> R.string.native_diagnostics_profile
    NativeDiagnosticStage.CORE -> R.string.native_diagnostics_core
    NativeDiagnosticStage.TUNNEL -> R.string.native_diagnostics_tunnel
}

private fun detailLabel(detail: NativeDiagnosticDetail) = when (detail) {
    NativeDiagnosticDetail.NETWORK_AVAILABLE, NativeDiagnosticDetail.NETWORK_UNAVAILABLE -> R.string.native_diagnostics_network_available
    NativeDiagnosticDetail.DNS_AVAILABLE, NativeDiagnosticDetail.DNS_UNAVAILABLE -> R.string.native_diagnostics_dns_hint
    NativeDiagnosticDetail.SERVER_AVAILABLE, NativeDiagnosticDetail.SERVER_UNAVAILABLE -> R.string.native_diagnostics_tcp_hint
    NativeDiagnosticDetail.NO_ENDPOINT -> R.string.native_diagnostics_no_endpoint
    NativeDiagnosticDetail.TRANSPORT_SKIPPED -> R.string.native_diagnostics_transport_skipped
    NativeDiagnosticDetail.PROFILE_AVAILABLE -> R.string.native_diagnostics_profile_available
    NativeDiagnosticDetail.PROFILE_MISSING -> R.string.native_diagnostics_profile_missing
    NativeDiagnosticDetail.CORE_AVAILABLE -> R.string.native_diagnostics_core_available
    NativeDiagnosticDetail.CORE_UNAVAILABLE -> R.string.native_diagnostics_core_unavailable
    NativeDiagnosticDetail.DISCONNECTED -> R.string.native_diagnostics_disconnected
    NativeDiagnosticDetail.PROBE_OK, NativeDiagnosticDetail.PROBE_FAILED -> R.string.native_diagnostics_tunnel_hint
    NativeDiagnosticDetail.INVALID_PROBE_URL -> R.string.native_diagnostics_invalid_url
}
