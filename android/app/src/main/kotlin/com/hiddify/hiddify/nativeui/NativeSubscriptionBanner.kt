package com.hiddify.hiddify.nativeui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hiddify.hiddify.R
import kotlinx.coroutines.delay

/** Bounded, non-focusable in-app banner. No persistent Popup window while hidden. */
@Composable
internal fun NativeSubscriptionPopup(
    banner: NativeSubscriptionBanner?,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Important: an always-mounted Popup creates an extra Android window even
    // when AnimatedVisibility is false. Remove the window completely when idle.
    val shown = banner ?: return
    val motion = LocalNativeMotionEnabled.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val opacity = remember(shown.revision) { Animatable(if (motion) 0f else 1f) }

    // One coroutine per revision: Compose cancels the previous countdown when
    // a new message arrives or the window leaves composition.
    LaunchedEffect(shown.revision) {
        if (motion) opacity.animateTo(1f, tween(170)) else opacity.snapTo(1f)
        delay(NativeSubscriptionPopupPolicy.durationMillis(shown.kind))
        if (motion) opacity.animateTo(0f, tween(150))
        currentOnDismiss(shown.revision)
    }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, with(LocalDensity.current) { 12.dp.roundToPx() }),
        properties = PopupProperties(focusable = false),
    ) {
        val scheme = MaterialTheme.colorScheme
        val accent = when (shown.kind) {
            NativeSubscriptionBanner.Kind.SUCCESS -> scheme.primary
            NativeSubscriptionBanner.Kind.INFO -> scheme.secondary
            NativeSubscriptionBanner.Kind.ERROR -> scheme.error
        }
        Surface(
            modifier = modifier.widthIn(max = 520.dp).padding(horizontal = 16.dp)
                .graphicsLayer {
                    alpha = opacity.value
                    translationY = -(1f - opacity.value) * 12.dp.toPx()
                }
                .semantics { liveRegion = LiveRegionMode.Polite },
            shape = MaterialTheme.shapes.large,
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
            shadowElevation = 8.dp,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    painterResource(if (shown.kind == NativeSubscriptionBanner.Kind.ERROR)
                        R.drawable.privacy_error else R.drawable.native_check),
                    contentDescription = null, tint = accent, modifier = Modifier.size(24.dp),
                )
                Text(
                    shown.message, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
