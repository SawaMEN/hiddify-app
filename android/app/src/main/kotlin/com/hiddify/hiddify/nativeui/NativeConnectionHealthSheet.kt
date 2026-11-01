package com.hiddify.hiddify.nativeui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.constant.Status
import com.hiddify.hiddify.nativeconnection.NativeInternetHealth
import com.hiddify.hiddify.nativecore.NativeChainOptions
import com.hiddify.hiddify.nativecore.NativeOutbound

private enum class HealthTone { GOOD, PENDING, ERROR, UNKNOWN }

/**
 * A path explanation based on facts VetrOFF actually knows. A running TUN or a green
 * latency test is not proof that a configured chain hop is healthy. Never invent one.
 */
@Composable
internal fun NativeConnectionHealthSheet(
    status: Status,
    health: NativeInternetHealth,
    outbound: NativeOutbound?,
    chain: NativeChainOptions,
    onDismiss: () -> Unit,
    onFullDiagnostics: () -> Unit,
    onTestSelected: () -> Unit,
    onChainSettings: () -> Unit,
) {
    val motion = LocalNativeMotionEnabled.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }

    val vpnTone = when (status) {
        Status.Started -> HealthTone.GOOD
        Status.Starting, Status.Stopping -> HealthTone.PENDING
        Status.Stopped -> HealthTone.UNKNOWN
    }
    val healthTone = when (health) {
        NativeInternetHealth.AVAILABLE -> HealthTone.GOOD
        NativeInternetHealth.UNAVAILABLE -> HealthTone.ERROR
        NativeInternetHealth.PROBE_FAILED -> HealthTone.UNKNOWN
        NativeInternetHealth.CHECKING -> HealthTone.PENDING
        NativeInternetHealth.UNCHECKED -> HealthTone.UNKNOWN
    }
    val proxyTone = when {
        outbound == null || outbound.delayMs <= 0 -> HealthTone.UNKNOWN
        outbound.delayMs > 65000 -> HealthTone.ERROR
        else -> HealthTone.GOOD
    }
    val proxyDescription = when {
        outbound == null -> stringResource(R.string.native_path_no_proxy)
        outbound.delayMs <= 0 -> stringResource(R.string.native_path_proxy_untested)
        outbound.delayMs > 65000 -> stringResource(R.string.native_path_proxy_timeout)
        else -> stringResource(R.string.native_path_proxy_delay, outbound.delayMs)
    }
    val healthDescription = stringResource(when (health) {
        NativeInternetHealth.AVAILABLE -> R.string.native_path_probe_passed
        NativeInternetHealth.UNAVAILABLE -> R.string.native_path_probe_failed
        NativeInternetHealth.PROBE_FAILED -> R.string.native_path_probe_inconclusive
        NativeInternetHealth.CHECKING -> R.string.native_path_probe_running
        NativeInternetHealth.UNCHECKED -> R.string.native_path_probe_unknown
    })
    val configured = chain.status != "off"
    val rows = listOf(
        Triple(stringResource(R.string.native_path_vpn), stringResource(when (status) {
            Status.Started -> R.string.native_status_started
            Status.Starting -> R.string.native_status_starting
            Status.Stopping -> R.string.native_status_stopping
            Status.Stopped -> R.string.native_status_stopped
        }), vpnTone),
        Triple(stringResource(R.string.native_path_selected_proxy), proxyDescription, proxyTone),
        Triple(stringResource(R.string.native_path_internet), healthDescription, healthTone),
        Triple(stringResource(R.string.native_path_chain),
            stringResource(if (configured) R.string.native_path_chain_configured else R.string.native_path_chain_off),
            HealthTone.UNKNOWN),
    )

    NativeModalSheet(onDismissRequest = onDismiss, sheetMaxWidth = 480.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.native_path_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.native_path_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEachIndexed { index, (title, detail, tone) ->
                androidx.compose.runtime.key(index) {
                    AnimatedVisibility(
                        visible = revealed || !motion,
                        enter = fadeIn(tween(if (motion) 180 else 0,
                            delayMillis = if (motion) 55 * index else 0)) +
                            slideInVertically(tween(if (motion) 180 else 0,
                                delayMillis = if (motion) 55 * index else 0)) { it / 6 },
                    ) {
                        HealthLine(title, detail, tone)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            NativeButton(onClick = { onDismiss(); onFullDiagnostics() },
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.native_path_full_diagnostics))
            }
            if (outbound != null) NativeOutlinedButton(
                onClick = { onDismiss(); onTestSelected() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.native_path_test_proxy))
            }
            if (configured) NativeTextButton(onClick = { onDismiss(); onChainSettings() },
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.native_path_chain_settings))
            }
        }
    }
}

@Composable
private fun HealthLine(title: String, detail: String, tone: HealthTone) {
    val scheme = MaterialTheme.colorScheme
    val color = when (tone) {
        HealthTone.GOOD -> scheme.primary
        HealthTone.PENDING -> scheme.tertiary
        HealthTone.ERROR -> scheme.error
        HealthTone.UNKNOWN -> scheme.outline
    }
    Surface(shape = MaterialTheme.shapes.medium, color = scheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant)
            }
        }
    }
}
