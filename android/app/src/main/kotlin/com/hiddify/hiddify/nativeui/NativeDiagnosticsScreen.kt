package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.hiddify.hiddify.nativeui.NativeGlassDialog as AlertDialog
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
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
    onBack: () -> Unit, onRun: () -> Unit, onShareReport: (String) -> Unit) {
    var previewOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        NativePageHeader(stringResource(R.string.native_diagnostics_title), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp)) {
            NativeButton(onRun, Modifier.fillMaxWidth(), enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.native_diagnostic_play), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.native_diagnostics_run))
            }
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
