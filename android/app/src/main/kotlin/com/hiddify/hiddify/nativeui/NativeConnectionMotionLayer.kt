package com.hiddify.hiddify.nativeui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hiddify.hiddify.constant.Status
import com.hiddify.hiddify.nativeconnection.NativeMotionIntensity
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Bounded native canvas. Frame state is read in drawing, never in page composition. */
@Composable
internal fun NativeConnectionMotionLayer(
    status: Status,
    failed: Boolean,
    downBytesPerSecond: Long,
    upBytesPerSecond: Long,
    modifier: Modifier = Modifier,
    transitioning: Boolean = false,
) {
    val motion = LocalNativeMotionEnabled.current
    val pending = transitioning || status == Status.Starting || status == Status.Stopping
    val connected = status == Status.Started && !failed && !pending
    val download = if (connected && motion) NativeMotionIntensity.level(downBytesPerSecond) else 0f
    val upload = if (connected && motion) NativeMotionIntensity.level(upBytesPerSecond) else 0f
    val down = animateFloatAsState(download, tween(if (motion) 900 else 0), label = "download light")
    val up = animateFloatAsState(upload, tween(if (motion) 900 else 0), label = "upload light")
    val traffic = download > 0f || upload > 0f
    var fading by remember { mutableStateOf(false) }
    LaunchedEffect(traffic, connected, motion) {
        fading = connected && motion
        if (!traffic && fading) { delay(900); fading = false }
    }
    val running = motion && (pending || (connected && (traffic || fading)))
    val phase: State<Float> = if (running) {
        val clock = rememberInfiniteTransition(label = "connection orbit")
        clock.animateFloat(0f, 1f,
            infiniteRepeatable(tween(if (pending) 2000 else 6200, easing = LinearEasing)),
            label = "connection phase")
    } else rememberUpdatedState(0f)
    val alert = remember { Animatable(0f) }
    LaunchedEffect(failed, motion) {
        if (failed && motion) {
            alert.snapTo(1f)
            alert.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
        } else alert.snapTo(0f)
    }
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.fillMaxSize()) {
        if (!motion) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val diameter = size.minDimension
        val rim = diameter * .425f
        val turn = phase.value
        fun arc(color: Color, start: Float, sweep: Float, width: Float, radius: Float = rim) {
            drawArc(color, start, sweep, false,
                Offset(center.x - radius, center.y - radius), Size(radius * 2f, radius * 2f),
                style = Stroke(width, cap = StrokeCap.Round))
        }
        if (failed) {
            // One short emphasis, then a steady marker. No perpetual error animation.
            val tint = colors.error.copy(alpha = .45f + .3f * alert.value)
            arc(tint, 20f, 50f, diameter * .012f)
            arc(tint, 110f, 50f, diameter * .012f)
        } else if (pending) {
            val direction = if (status == Status.Stopping) -1f else 1f
            val sweep = 64f + 34f * (.5f + .5f * sin(turn * 2f * PI).toFloat())
            for (trail in 5 downTo 0) {
                arc(colors.primary.copy(alpha = .10f + (5 - trail) * .10f),
                    direction * (turn * 360f - trail * 9f) - 90f,
                    direction * sweep, diameter * .012f)
            }
        } else if (connected) {
            for (channel in 0..1) {
                val energy = if (channel == 0) down.value else up.value
                if (energy < .001f) continue
                val tint = if (channel == 0) Color(0xFFFFB44D) else Color(0xFF4EA7FF)
                val direction = if (channel == 0) 1f else -1f
                val start = turn * 360f * direction + channel * 180f - 90f
                val radius = rim + diameter * channel * .017f
                // Layered short trails stay inside the reserved halo; no blur or offscreen bitmap.
                for (trail in 5 downTo 0) {
                    arc(tint.copy(alpha = energy * (.035f + (5 - trail) * .026f)),
                        start - direction * trail * 9f, direction * (32f + energy * 66f),
                        diameter * (.006f + energy * .004f), radius)
                }
                for (index in 0 until 18) {
                    val angle = 2.0 * PI * (index / 18.0 + turn * direction) + channel * PI
                    val wave = .5f + .5f * sin(index * .85 + turn * 2.0 * PI).toFloat()
                    val length = diameter * (.008f + .028f * energy * wave)
                    val startPoint = Offset(center.x + cos(angle).toFloat() * radius,
                        center.y + sin(angle).toFloat() * radius)
                    val endPoint = Offset(center.x + cos(angle).toFloat() * (radius + length),
                        center.y + sin(angle).toFloat() * (radius + length))
                    drawLine(tint.copy(alpha = energy * (.16f + .40f * wave)), startPoint, endPoint,
                        diameter * .005f, StrokeCap.Round)
                }
            }
        }
    }
}
