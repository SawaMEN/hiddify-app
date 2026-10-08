package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun NativeAtmosphere(content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box { content() }
    }
}

@Composable
internal fun NativeCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(modifier = modifier, content = content)
}

/** Tonal Material surface for layouts that do not use a ColumnScope. */
@Composable
internal fun NativeSurface(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.medium, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        content = content,
    )
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
