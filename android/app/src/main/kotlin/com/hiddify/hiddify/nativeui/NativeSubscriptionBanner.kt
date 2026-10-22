package com.hiddify.hiddify.nativeui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.hiddify.hiddify.R
import kotlinx.coroutines.delay

/** An app-only message. Subscription refreshes never enter the Android notification shade. */
internal data class NativeSubscriptionBanner(
    val revision: Long,
    val message: String,
    val kind: Kind,
) {
    enum class Kind { SUCCESS, INFO, ERROR }
}

@Composable
internal fun NativeSubscriptionPopup(
    banner: NativeSubscriptionBanner?,
    onDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalNativeMotionEnabled.current
    LaunchedEffect(banner?.revision) {
        val shown = banner ?: return@LaunchedEffect
        delay(if (shown.kind == NativeSubscriptionBanner.Kind.ERROR) 5200L else 3400L)
        onDismiss(shown.revision)
    }
    // A non-focusable popup stays above profile modal sheets without taking
    // keyboard focus or creating a system notification.
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, with(LocalDensity.current) { 12.dp.roundToPx() }),
        properties = PopupProperties(focusable = false),
    ) {
    AnimatedVisibility(
        visible = banner != null,
        modifier = modifier.widthIn(max = 520.dp).padding(horizontal = 16.dp),
        enter = if (motion) fadeIn(tween(180)) + slideInVertically(tween(230)) { -it / 2 }
            else fadeIn(tween(0)),
        exit = if (motion) fadeOut(tween(170)) + slideOutVertically(tween(210)) { -it / 3 }
            else fadeOut(tween(0)),
    ) {
        val shown = banner
        if (shown != null) {
            val scheme = MaterialTheme.colorScheme
            val accent = when (shown.kind) {
                NativeSubscriptionBanner.Kind.SUCCESS -> scheme.primary
                NativeSubscriptionBanner.Kind.INFO -> scheme.secondary
                NativeSubscriptionBanner.Kind.ERROR -> scheme.error
            }
            Surface(
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
                    Icon(painterResource(if (shown.kind == NativeSubscriptionBanner.Kind.ERROR)
                        R.drawable.privacy_error else R.drawable.native_check),
                        contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                    Text(shown.message, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    }
}
