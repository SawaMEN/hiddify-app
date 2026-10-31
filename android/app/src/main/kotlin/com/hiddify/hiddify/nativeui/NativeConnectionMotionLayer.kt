package com.hiddify.hiddify.nativeui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hiddify.hiddify.constant.Status
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/**
 * VetrOFF-native connection visual, drawn on the Compose canvas rather than embedding Flutter.
 *
 * The idle button has no permanent outline/halo. Active traffic lights the edge in two
 * distinct directions; start/stop animates only a short progress arc; errors show a
 * bounded alert rather than a never-ending full-screen effect. Noninteractive overlay.
 */
@Composable
internal fun NativeConnectionMotionLayer(
    status: Status,
    failed: Boolean,
    downBytesPerSecond: Long,
    upBytesPerSecond: Long,
    modifier: Modifier = Modifier,
) {
    val motion = LocalNativeMotionEnabled.current
    val connected = status == Status.Started && !failed
    val pending = status == Status.Starting || status == Status.Stopping
    val down by animateFloatAsState(
            if (connected && motion) nativeMotionTrafficLevel(downBytesPerSecond) else 0f,
            animationSpec = tween(if (motion) 900 else 0),
            label = "download illumination",
    )
    val up by animateFloatAsState(
            if (connected && motion) nativeMotionTrafficLevel(upBytesPerSecond) else 0f,
            animationSpec = tween(if (motion) 900 else 0),
            label = "upload illumination",
    )
    val running = motion && (pending || failed || (connected && (down > 0.001f || up > 0.001f)))
    // Conditional creation also cancels the frame clock on idle/background/reduced motion.
    val phase = if (running) {
        val infinite = rememberInfiniteTransition(label = "native connection motion")
        infinite.animateFloat(0f, 1f,
            animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
            label = "connection phase").value
    } else 0f

    val colors = MaterialTheme.colorScheme
    Canvas(modifier.fillMaxSize()) {
        if (!motion || (!pending && !failed && down < 0.001f && up < 0.001f)) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val diameter = size.minDimension
        // The base Material button has a larger tap target than this purely decorative rim.
        val rim = diameter * 0.425f
        fun arc(color: Color, start: Float, sweep: Float, width: Float, radius: Float = rim) {
            drawArc(color = color, startAngle = start, sweepAngle = sweep,
                useCenter = false, topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = width, cap = StrokeCap.Round))
        }
        if (pending) {
            val reverse = status == Status.Stopping
            arc(colors.primary.copy(alpha = 0.73f),
                start = (if (reverse) -1f else 1f) * phase * 360f - 90f,
                sweep = if (reverse) -98f else 98f, width = diameter * 0.013f)
        } else if (failed) {
            val alpha = 0.48f + 0.18f * sin(phase.toDouble() * 2.0 * PI).toFloat()
            arc(colors.error.copy(alpha = alpha), 15f, 54f, diameter * 0.012f)
            arc(colors.error.copy(alpha = alpha), 110f, 54f, diameter * 0.012f)
        } else if (connected) {
            // Stable rates are smoothed above, and zero traffic draws nothing.
            // Fixed ray count and bounded canvas avoid large blur layers and allocations.
            val strengths = floatArrayOf(down, up)
            val tints = arrayOf(Color(0xFFFFB44D), Color(0xFF4EA7FF))
            for (channel in 0..1) {
                val energy = strengths[channel]
                if (energy < 0.001f) continue
                val count = if (channel == 0) 20 else 24
                val offset = if (channel == 0) 0f else 180f
                for (index in 0 until count) {
                    val angle = 2.0 * PI * (index.toDouble() / count + phase * (if (channel == 0) 0.13 else -0.10)) +
                        offset * PI / 180.0
                    val twinkle = 0.79 + 0.21 * sin(index * 1.71 + phase * 2.0 * PI)
                    val inner = rim + diameter * 0.008f
                    val reach = diameter * (0.015f + 0.063f * energy * twinkle.toFloat())
                    val start = Offset(center.x + cos(angle).toFloat() * inner,
                        center.y + sin(angle).toFloat() * inner)
                    val end = Offset(center.x + cos(angle).toFloat() * (inner + reach),
                        center.y + sin(angle).toFloat() * (inner + reach))
                    drawLine(tints[channel].copy(alpha = (0.18f + energy * 0.6f) * twinkle.toFloat()),
                        start = start, end = end,
                        strokeWidth = diameter * (0.005f + energy * 0.004f), cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** Bounded, finite and monotonic visual intensity (bytes/sec, not total bytes). */
internal fun nativeMotionTrafficLevel(bytesPerSecond: Long): Float {
    if (bytesPerSecond <= 0L) return 0f
    val capped = bytesPerSecond.coerceAtMost(150_000L).toDouble()
    return (ln(1.0 + capped) / ln(150_001.0)).toFloat().coerceIn(0f, 1f)
}
