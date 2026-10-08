package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
internal fun NativeAtmosphere(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.background)
        .background(Brush.linearGradient(listOf(scheme.primary.copy(alpha = .065f),
            Color.Transparent, scheme.secondary.copy(alpha = .045f))))) {
        val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .035f)
        Canvas(Modifier.fillMaxSize()) {
            val step = 32.dp.toPx()
            var x = 0f
            while (x < size.width) { drawLine(gridColor, Offset(x, 0f), Offset(x, size.height)); x += step }
            var y = 0f
            while (y < size.height) { drawLine(gridColor, Offset(0f, y), Offset(size.width, y)); y += step }
        }
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() }
    }
}

/** Shared surface decoration for native sheets and dialogs. */
@Composable
internal fun Modifier.nativeGlassDecoration(
    shape: Shape = RoundedCornerShape(20.dp),
): Modifier = background(MaterialTheme.colorScheme.surfaceContainerLow, shape)
    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)

@Composable
internal fun NativeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        modifier = modifier, shape = RoundedCornerShape(20.dp),
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
    androidx.compose.material3.Surface(
        modifier = modifier, shape = RoundedCornerShape(radius.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(accent.copy(alpha = .065f),
            Color.Transparent, MaterialTheme.colorScheme.secondary.copy(alpha = .035f))))) { content() }
    }
}

@Composable
internal fun NativeButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.Button(onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        content = content)
}

@Composable
internal fun NativeOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        content = content)
}

@Composable
internal fun NativeTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(14.dp), contentPadding = contentPadding, content = content)
}

@Composable
internal fun NativeElevatedButton(onClick: () -> Unit, enabled: Boolean = true,
    modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.ElevatedButton(onClick = onClick, enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
        shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary), content = content)
}
