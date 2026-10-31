package com.hiddify.hiddify.nativeui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativeprofile.NativeProfileRefreshPhase

/** VetrOFF-toned update -> loading -> success/failure, all inside an unchanged tap target. */
@Composable
internal fun NativeRefreshMotionIcon(
    phase: NativeProfileRefreshPhase?,
    modifier: Modifier = Modifier,
) {
    val motion = LocalNativeMotionEnabled.current
    val colors = MaterialTheme.colorScheme
    Box(modifier.size(22.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(targetState = phase,
            transitionSpec = {
                if (!motion) EnterTransition.None togetherWith ExitTransition.None
                else (fadeIn(tween(210)) + scaleIn(tween(210), initialScale = 0.85f)) togetherWith
                    (fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.92f))
            },
            label = "Subscription update state") { shown ->
            when (shown) {
                NativeProfileRefreshPhase.UPDATING ->
                    if (motion) CircularProgressIndicator(Modifier.size(19.dp),
                        strokeWidth = 2.4.dp, color = colors.primary)
                    else Box(Modifier.size(19.dp).border(2.dp, colors.primary, CircleShape))
                NativeProfileRefreshPhase.SUCCESS ->
                    Icon(painterResource(R.drawable.native_check), null,
                        Modifier.size(22.dp), tint = colors.primary)
                NativeProfileRefreshPhase.FAILURE ->
                    Icon(painterResource(R.drawable.privacy_error), null,
                        Modifier.size(22.dp), tint = colors.error)
                null -> Icon(painterResource(R.drawable.native_profile_update), null,
                    Modifier.size(22.dp), tint = colors.onSurfaceVariant)
            }
        }
    }
}
