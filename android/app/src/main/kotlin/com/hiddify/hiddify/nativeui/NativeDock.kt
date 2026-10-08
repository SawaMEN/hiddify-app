package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/** A translucent frosted surface without capturing/replaying the entire Compose page. */
@Composable
internal fun NativeDock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    val tint = if (dark) Color(0xFF34373D) else Color(0xFFE7E8EC)
    Box(modifier.clip(shape)
        .background(tint.copy(alpha = .90f))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .09f), Color.Transparent)))
        .border(1.dp, Color.White.copy(alpha = .18f), shape)) { content() }
}
