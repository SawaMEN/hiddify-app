package com.hiddify.hiddify.nativeui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.NativeOutbound

@Composable
internal fun NativeActiveProxyDelay(outbound: NativeOutbound, busy: Boolean, onTest: () -> Unit) {
    val timeout = outbound.delayMs > 65000
    val description = stringResource(when {
        timeout -> R.string.native_active_delay_timeout
        outbound.delayMs <= 0 -> R.string.native_active_delay_testing
        else -> R.string.native_active_delay_result
    }, outbound.delayMs)
    Row(Modifier.semantics { contentDescription = description }
        .clickable(enabled = !busy, onClick = onTest).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.native_wifi_signal), null)
        if (outbound.delayMs > 0) {
            Text(if (timeout) stringResource(R.string.native_active_timeout) else outbound.delayMs.toString(),
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium,
                color = if (timeout) MaterialTheme.colorScheme.error else LocalContentColor.current)
            if (!timeout) Text("ms", style = MaterialTheme.typography.bodyMedium)
        } else Box(Modifier.size(width = 48.dp, height = 18.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(4.dp)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NativeActiveProxyFooter(
    outbound: NativeOutbound,
    busy: Boolean,
    onOpen: () -> Unit,
    onTest: () -> Unit,
) {
    var showIp by rememberSaveable { mutableStateOf(false) }
    var inspectPending by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf(false) }
    LaunchedEffect(inspectPending, busy) {
        if (inspectPending && !busy) { inspectPending = false; inspecting = true }
    }
    val ip = outbound.ipInfo?.ip.orEmpty()
    val ipLabel = stringResource(R.string.native_outbound_ip)
    NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable(enabled = !busy) { inspectPending = true; onTest() }.padding(horizontal = 8.dp)) {
                NativeOutboundCountryBadge(outbound.ipInfo?.countryCode.orEmpty(), size = 48.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(outbound.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).semantics { contentDescription = ipLabel }) {
                        if (ip.isNotBlank()) {
                            Crossfade(showIp, animationSpec = tween(200), label = "Active proxy IP") { visible ->
                                Text(if (visible) ip else obscureActiveIp(ip),
                                    Modifier.combinedClickable(onClick = { showIp = !showIp },
                                        onLongClick = { if (!busy) onTest() }).padding(horizontal = 2.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else Text(stringResource(R.string.native_active_unknown_ip),
                            Modifier.clickable(enabled = !busy, onClick = onTest).padding(horizontal = 2.dp),
                            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(outbound.type, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(painterResource(R.drawable.native_chevron), null, Modifier.padding(16.dp))
        }
    }
    if (inspecting) NativeOutboundInfoDialog(outbound, onDismiss = { inspecting = false })
}

private fun obscureActiveIp(ip: String): String = when {
    '.' in ip -> ip.split('.').let { "${it.first()}.*.*.${it.last()}" }
    ':' in ip -> ip.split(':').mapIndexed { index, part -> if (index == 0) part else "*".repeat(part.length) }.joinToString(":")
    else -> "*.*.*.*"
}
