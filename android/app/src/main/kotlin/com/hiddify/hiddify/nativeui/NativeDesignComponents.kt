package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Canvas(Modifier.fillMaxSize()) {
            if (scheme.background != Color.Black) {
                // Flutter Alignment(-.9, -.8) / (.95, .35), 64 logical pixel grid.
                val shortest = min(size.width, size.height)
                drawRect(Brush.radialGradient(
                    listOf(scheme.primary.copy(alpha = .12f), Color.Transparent),
                    center = Offset(size.width * .05f, size.height * .1f), radius = shortest * 1.25f,
                ))
                drawRect(Brush.radialGradient(
                    listOf(scheme.secondary.copy(alpha = .08f), Color.Transparent),
                    center = Offset(size.width * .975f, size.height * .675f), radius = shortest * .9f,
                ))
                val step = 64.dp.toPx()
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

@Composable
internal fun NativeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        modifier = modifier, shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), content = content,
    )
}

@Composable
internal fun NativeGlass(
    modifier: Modifier = Modifier,
    radius: Int = 20,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    androidx.compose.material3.Surface(
        modifier = modifier, shape = RoundedCornerShape(radius.dp), color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(accent.copy(alpha = .08f).compositeOver(base), base)))) {
            content()
        }
    }
}

@Composable
internal fun NativeButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val scheme = MaterialTheme.colorScheme
    val base = if (enabled) scheme.primary else scheme.onSurface.copy(alpha = .12f)
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .background(Brush.linearGradient(listOf(
                if (enabled) Color.White.copy(alpha = .12f).compositeOver(base) else base, base)), shape),
        enabled = enabled, shape = shape,
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
        onClick = onClick, modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface.copy(alpha = .12f)),
        colors = ButtonDefaults.outlinedButtonColors(), content = content,
    )
}

@Composable
internal fun NativeTextButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.TextButton(
        onClick = onClick, modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        enabled = enabled, shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}
