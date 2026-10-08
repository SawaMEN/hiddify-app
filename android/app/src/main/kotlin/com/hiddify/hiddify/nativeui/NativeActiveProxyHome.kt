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
import com.hiddify.hiddify.nativeui.NativeNeonIcon as Icon
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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
    val delayStyle = MaterialTheme.typography.titleMedium.toSpanStyle().copy(fontWeight = FontWeight.Bold)
    val delayText = if (timeout) stringResource(R.string.native_active_timeout) else outbound.delayMs.toString()
    Row(Modifier.semantics { contentDescription = description }
        .clickable(enabled = !busy, onClick = onTest).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.native_wifi_signal), null)
        if (outbound.delayMs > 0) {
            Text(buildAnnotatedString {
                withStyle(delayStyle) {
                    append(delayText)
                }
                if (!timeout) append(" ms")
            }, style = MaterialTheme.typography.bodyLarge,
                color = if (timeout) MaterialTheme.colorScheme.error else LocalContentColor.current)
        } else Box(Modifier.size(width = 48.dp, height = 18.dp)
            .background(LocalContentColor.current.copy(alpha = .16f), RoundedCornerShape(8.dp)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NativeActiveProxyFooter(
    outbound: NativeOutbound,
    busy: Boolean,
    onOpen: () -> Unit,
    onTest: () -> Unit,
    ipVisibilitySession: NativeIpVisibilitySession? = null,
    hapticFeedback: Boolean = true,
    operationRevision: Int = 0,
) {
    val visibility = ipVisibilitySession ?: remember { NativeIpVisibilitySession() }
    val view = LocalView.current
    DisposableEffect(visibility) {
        visibility.attach()
        onDispose { visibility.detach() }
    }
    var inspectPending by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf(false) }
    var requestedAtRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(inspectPending, busy, operationRevision) {
        if (inspectPending && operationRevision != requestedAtRevision && !busy) {
            inspectPending = false; inspecting = true
        }
    }
    val ip = outbound.ipInfo?.ip.orEmpty()
    val ipLabel = stringResource(R.string.native_outbound_ip)
    NativeGlass(Modifier.fillMaxWidth(), radius = 24) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clickable(enabled = !busy) { requestedAtRevision = operationRevision; inspectPending = true; onTest() }.padding(horizontal = 8.dp)) {
                NativeOutboundCountryBadge(outbound.ipInfo?.countryCode.orEmpty(), size = 48.dp,
                    organization = outbound.ipInfo?.organization.orEmpty())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(outbound.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                    overflow = TextOverflow.Clip)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).semantics { contentDescription = ipLabel }) {
                        if (ip.isNotBlank()) {
                            Crossfade(visibility.visible, animationSpec = tween(200), label = "Active proxy IP") { visible ->
                                Text(if (visible) ip else obscureActiveIp(ip),
                                    Modifier.combinedClickable(onClick = {
                                        if (!visibility.visible && hapticFeedback) {
                                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                        }
                                        visibility.toggle()
                                    },
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
            Icon(painterResource(R.drawable.settings_chevron), null, Modifier.padding(16.dp))
        }
    }
    if (inspecting) NativeOutboundInfoDialog(outbound, onDismiss = { inspecting = false })
}

private fun obscureActiveIp(ip: String): String = when {
    '.' in ip -> ip.split('.').let { "${it.first()}.*.*.${it.last()}" }
    ':' in ip -> ip.split(':').mapIndexed { index, part -> if (index == 0) part else "*".repeat(part.length) }.joinToString(":")
    else -> "*.*.*.*"
}
