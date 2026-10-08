package com.hiddify.hiddify.nativeui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import kotlin.math.min

@Composable
internal fun NativeAtmosphere(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val drift = if (LocalNativeMotionEnabled.current && scheme.background != Color.Black) {
        rememberInfiniteTransition(label = "Atmosphere").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "Ambient drift",
        )
    } else remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            if (scheme.background != Color.Black) {
                // Animate only drawing: scrolling lists do not recompose on every ambient frame.
                val shortest = min(size.width, size.height)
                drawRect(Brush.radialGradient(
                    listOf(scheme.primary.copy(alpha = .23f), Color.Transparent),
                    center = Offset(size.width * (.05f + drift.value * .22f), size.height * .12f), radius = shortest * 1.25f,
                ))
                drawRect(Brush.radialGradient(
                    listOf(scheme.secondary.copy(alpha = .16f), Color.Transparent),
                    center = Offset(size.width * (.975f - drift.value * .18f), size.height * .675f), radius = shortest * .9f,
                ))
                drawRect(Brush.radialGradient(
                    listOf(scheme.tertiary.copy(alpha = .055f), Color.Transparent),
                    center = Offset(size.width * .15f, size.height * .95f), radius = shortest,
                ))
                val step = 72.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(scheme.primary.copy(alpha = .035f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(scheme.primary.copy(alpha = .035f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    y += step
                }
            }
        }
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface) { content() }
    }
}

/** Translucent tint, specular edge and colored elevation form one shared glass material. */
@Composable
internal fun Modifier.nativeGlassDecoration(
    shape: Shape = RoundedCornerShape(24.dp),
    accent: Color = MaterialTheme.colorScheme.primary,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.onSurface.red > .7f
    val reflection = if (dark) Color.White else scheme.primary
    return shadow(8.dp, shape, clip = false,
        ambientColor = accent.copy(alpha = .18f), spotColor = accent.copy(alpha = .26f))
        .background(Brush.linearGradient(listOf(
            accent.copy(alpha = if (dark) .12f else .045f).compositeOver(scheme.surfaceContainerLow.copy(alpha = .94f)),
            scheme.surfaceContainerLow.copy(alpha = .88f),
            scheme.secondary.copy(alpha = .035f).compositeOver(scheme.surfaceContainerLow.copy(alpha = .94f)),
        )), shape)
        .then(Modifier.background(Brush.linearGradient(listOf(
            reflection.copy(alpha = if (dark) .055f else .018f), Color.Transparent, Color.Transparent,
        )), shape))
        .border(glassBorder(accent), shape)
}

@Composable
private fun glassBorder(accent: Color): BorderStroke = BorderStroke(1.dp, Brush.linearGradient(listOf(
    accent.copy(alpha = .55f), MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
    accent.copy(alpha = .12f), MaterialTheme.colorScheme.secondary.copy(alpha = .3f),
)))

@Composable
internal fun NativeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    androidx.compose.material3.Card(
        modifier = modifier.nativeGlassDecoration(shape), shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = glassBorder(MaterialTheme.colorScheme.primary), content = content,
    )
}

@Composable
internal fun NativeGlass(
    modifier: Modifier = Modifier,
    radius: Int = 20,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius.dp)
    androidx.compose.material3.Surface(
        modifier = modifier.nativeGlassDecoration(shape, accent), shape = shape, color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface, border = glassBorder(accent),
        content = content,
    )
}

@Composable
internal fun NativeButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val scheme = MaterialTheme.colorScheme
    val base = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = .12f)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) .97f else 1f,
        tween(if (LocalNativeMotionEnabled.current) 120 else 0), label = "Button press")
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (enabled) 10.dp else 0.dp, shape, clip = false,
                ambientColor = base.copy(alpha = .2f), spotColor = base.copy(alpha = .4f))
            .background(Brush.linearGradient(listOf(
                if (enabled) Color.White.copy(alpha = .12f).compositeOver(base) else base, base)), shape),
        enabled = enabled, shape = shape, interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
            contentColor = scheme.onPrimary, disabledContainerColor = Color.Transparent,
            disabledContentColor = scheme.onSurface.copy(alpha = .38f)),
        border = if (enabled) BorderStroke(1.dp, Color.White.copy(alpha = .18f)) else null,
        content = content,
    )
}

@Composable
internal fun NativeOutlinedButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).nativeGlassDecoration(RoundedCornerShape(20.dp)), enabled = enabled,
        shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        border = if (enabled) glassBorder(MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)),
        colors = ButtonDefaults.outlinedButtonColors(), content = content,
    )
}

@Composable
internal fun NativeTextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick, modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        enabled = enabled, shape = RoundedCornerShape(20.dp),
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
internal fun NativeElevatedButton(onClick: () -> Unit, enabled: Boolean = true,
    modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    androidx.compose.material3.ElevatedButton(onClick = onClick, enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).nativeGlassDecoration(shape),
        shape = shape, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color.Transparent, contentColor = scheme.primary),
        elevation = ButtonDefaults.elevatedButtonElevation(defaultElevation = 1.dp), content = content)
}

/** A draw-only orbit indicates live/starting state without changing hit targets or layout. */
@Composable
internal fun NativeConnectionAura(accent: Color, running: Boolean, modifier: Modifier = Modifier) {
    val rotation = if (running && LocalNativeMotionEnabled.current) {
        rememberInfiniteTransition(label = "Connection orbit").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "Orbit angle",
        )
    } else remember { mutableFloatStateOf(0f) }
    Canvas(modifier) {
        val radius = size.minDimension / 2f - 3.dp.toPx()
        if (radius <= 0f) return@Canvas
        drawCircle(accent.copy(alpha = .06f), radius, style = Stroke(12.dp.toPx()))
        drawCircle(accent.copy(alpha = .12f), radius, style = Stroke(4.dp.toPx()))
        drawCircle(accent.copy(alpha = .3f), radius, style = Stroke(1.dp.toPx()))
        val inset = Offset(center.x - radius, center.y - radius)
        val bounds = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        for (offset in listOf(0f, 180f)) {
            drawArc(accent.copy(alpha = .8f), rotation.value + offset, 48f, false,
                topLeft = inset, size = bounds, style = Stroke(2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}
