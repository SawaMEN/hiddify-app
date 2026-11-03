package com.hiddify.hiddify.nativeui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativecore.*

internal fun proxyStateLabel(state: NativeProxyRowState): Int = when (state) {
    NativeProxyRowState.READY -> R.string.native_proxy_ready
    NativeProxyRowState.UNTESTED -> R.string.native_proxy_untested
    NativeProxyRowState.PING_FAILED -> R.string.native_proxy_ping_failed
    NativeProxyRowState.CONNECTING -> R.string.native_proxy_connecting
    NativeProxyRowState.REGISTERING -> R.string.native_proxy_registering
    NativeProxyRowState.SEARCHING -> R.string.native_proxy_searching
    NativeProxyRowState.RECONNECTING -> R.string.native_proxy_reconnecting
    NativeProxyRowState.FAILED -> R.string.native_proxy_failed
}

/** Stable 48 dp action; fade between measured states without shifting the server name. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NativeProxyStatusIndicator(outbound: NativeOutbound, signal: Boolean, enabled: Boolean,
    onTest: () -> Unit, onToggleStyle: () -> Unit) {
    val state = outbound.rowState()
    val stateText = stringResource(proxyStateLabel(state))
    val label = if (state == NativeProxyRowState.READY)
        stringResource(R.string.native_outbounds_delay, outbound.delayMs) else stateText
    val scheme = MaterialTheme.colorScheme
    val tint = when (state) {
        NativeProxyRowState.FAILED, NativeProxyRowState.PING_FAILED -> scheme.error
        NativeProxyRowState.READY -> if (outbound.delayMs < 800) scheme.primary else scheme.tertiary
        else -> scheme.onSurfaceVariant
    }
    val motion = LocalNativeMotionEnabled.current
    Box(Modifier.widthIn(min = 64.dp, max = 120.dp).heightIn(min = 48.dp)
        .semantics { contentDescription = label }
        .combinedClickable(enabled = enabled, onClick = onTest, onLongClick = onToggleStyle)
        .padding(6.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(targetState = Triple(state, signal, outbound.delayMs), transitionSpec = {
            if (motion) fadeIn(tween(180, delayMillis = 70)) togetherWith fadeOut(tween(70))
            else EnterTransition.None togetherWith ExitTransition.None
        }, label = "Proxy signal state") { (shownState, shownSignal, delay) ->
            if (shownSignal && shownState == NativeProxyRowState.READY) {
                val bars = outbound.copy(delayMs = delay).signalBars()
                Canvas(Modifier.size(30.dp, 24.dp)) {
                    val unit = size.width / 7f
                    for (index in 0..3) {
                        val height = size.height * (.28f + index * .24f)
                        drawRoundRect(tint.copy(alpha = if (index < bars) 1f else .18f),
                            Offset(index * unit * 2f, size.height - height), Size(unit, height),
                            CornerRadius(unit / 2f))
                    }
                }
            } else Text(if (shownState == NativeProxyRowState.READY) delay.toString()
                else stringResource(proxyStateLabel(shownState)), color = tint,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}
