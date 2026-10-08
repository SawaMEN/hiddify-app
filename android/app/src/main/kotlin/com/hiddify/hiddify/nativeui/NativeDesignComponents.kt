package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon

@Composable
internal fun NativeBackground(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val scheme = MaterialTheme.colorScheme
        val intensity = if (scheme.background == Color.Black) 0.045f else 0.10f
        Box(Modifier.fillMaxSize().drawWithCache {
            val blueGlow = Brush.radialGradient(
                listOf(scheme.primary.copy(alpha = intensity), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.12f),
                radius = size.maxDimension * 0.75f,
            )
            val violetGlow = Brush.radialGradient(
                listOf(scheme.tertiary.copy(alpha = intensity), Color.Transparent),
                center = Offset(size.width, size.height * 0.85f),
                radius = size.maxDimension * 0.65f,
            )
            onDrawBehind {
                drawRect(blueGlow)
                drawRect(violetGlow)
            }
        }) { content() }
    }
}

/** Tonal Material surface for layouts that do not use a ColumnScope. */
@Composable
internal fun NativeSurface(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.large, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = 2.dp,
    ) {
        val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)
        Box(Modifier.drawWithCache {
            val sheen = Brush.linearGradient(listOf(highlight, Color.Transparent))
            onDrawBehind { drawRect(sheen) }
        }) { content() }
    }
}

@Composable
internal fun NativeIconBadge(icon: Int, enabled: Boolean = true) {
    androidx.compose.material3.Surface(
        modifier = Modifier.size(42.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.12f else 0.04f),
        contentColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.38f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(22.dp))
        }
    }
}

@Composable
internal fun NativeButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.Button(onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), enabled = enabled, content = content)
}

@Composable
internal fun NativeOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), enabled = enabled, content = content)
}

@Composable
internal fun NativeTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    contentPadding: PaddingValues = androidx.compose.material3.ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), enabled = enabled,
        contentPadding = contentPadding, content = content)
}

@Composable
internal fun NativeElevatedButton(onClick: () -> Unit, enabled: Boolean = true,
    modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.ElevatedButton(onClick = onClick, enabled = enabled,
        modifier = modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp), content = content)
}
