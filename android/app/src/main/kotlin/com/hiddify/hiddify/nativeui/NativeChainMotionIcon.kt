package com.hiddify.hiddify.nativeui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.hiddify.hiddify.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native Compose adaptation of upstream Hiddify's animated chain-type indicators.
 * Animate only the enabled chain stage; respect TalkBack, background and animator scale.
 * This is a configuration indicator, not evidence that a VPN tunnel is connected.
 */
@Composable
internal fun NativeChainMotionIcon(extraSecurity: Boolean, active: Boolean, profileMode: Boolean) {
    val colors = MaterialTheme.colorScheme
    val main = when {
        !active -> colors.onSurfaceVariant
        profileMode -> Color(0xFF4DA3E8)
        extraSecurity -> Color(0xFF5BD68A)
        else -> Color(0xFFFFC94D)
    }
    val light = when {
        profileMode -> Color(0xFF3CC8E0)
        extraSecurity -> Color(0xFF3CC8E0)
        else -> Color(0xFFFF8A3D)
    }
    val animated = active && LocalNativeMotionEnabled.current
    val degrees = if (animated) {
        val transition = rememberInfiniteTransition(label = "chain status icon")
        val rotation = transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "chain icon rotation",
        )
        rotation.value
    } else 0f

    if (extraSecurity) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.native_shield), null, Modifier.size(20.dp), tint = main)
            if (animated) {
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    for (trail in 3 downTo 0) {
                        val radians = (degrees - trail * 11f) * PI / 180.0
                        val depth = sin(radians)
                        val front = depth >= 0.0
                        val dot = Offset(
                            center.x + cos(radians).toFloat() * size.width * .37f,
                            center.y + depth.toFloat() * size.height * .15f,
                        )
                        val opacity = (if (front) 1f else .38f) * (1f - trail * .23f)
                        drawCircle(
                            color = light.copy(alpha = opacity),
                            radius = size.minDimension * (if (trail == 0) .078f else .047f),
                            center = dot,
                        )
                    }
                }
            }
        }
    } else {
        Canvas(
            Modifier.size(25.dp).graphicsLayer {
                rotationZ = if (animated) degrees else 0f
            },
        ) {
            val unit = size.minDimension
            val ring = Offset(unit * .35f, unit * .34f)
            val stroke = unit * .095f
            drawCircle(main, radius = unit * .16f, center = ring, style = Stroke(stroke))
            drawLine(main, Offset(unit * .47f, unit * .46f), Offset(unit * .80f, unit * .79f),
                strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(main, Offset(unit * .66f, unit * .65f), Offset(unit * .75f, unit * .56f),
                strokeWidth = stroke * .85f, cap = StrokeCap.Round)
            drawLine(main, Offset(unit * .75f, unit * .74f), Offset(unit * .83f, unit * .65f),
                strokeWidth = stroke * .85f, cap = StrokeCap.Round)
        }
    }
}
