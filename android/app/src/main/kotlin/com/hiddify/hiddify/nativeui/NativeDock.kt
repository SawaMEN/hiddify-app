package com.hiddify.hiddify.nativeui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/** The dock's reserved area contains only the static atmosphere, never scrolling page content. */
@Composable
internal fun NativeDock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val blurred = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(28.dp)
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < .5f
    val tint = if (dark) Color(0xFF34373D) else Color(0xFFE7E8EC)
    val grid = scheme.onSurface.copy(alpha = .035f)
    val background = scheme.background
    Box(modifier.clip(shape)
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawWithCache {
            if (Build.VERSION.SDK_INT >= 31) {
                // Record only this small static strip. Stats and list redraws do not rebuild it.
                blurred.renderEffect = BlurEffect(20.dp.toPx(), 20.dp.toPx(), TileMode.Clamp)
                blurred.record {
                    drawRect(background)
                    val step = 32.dp.toPx()
                    var x = -(origin.x % step)
                    while (x < size.width) { drawLine(grid, Offset(x, 0f), Offset(x, size.height)); x += step }
                    var y = -(origin.y % step)
                    while (y < size.height) { drawLine(grid, Offset(0f, y), Offset(size.width, y)); y += step }
                }
            }
            onDrawWithContent {
                if (Build.VERSION.SDK_INT >= 31) drawLayer(blurred)
                drawContent()
            }
        }
        .background(tint.copy(alpha = if (Build.VERSION.SDK_INT >= 31) .78f else .94f))
        .border(1.dp, Color.White.copy(alpha = .18f), shape)) { content() }
}
