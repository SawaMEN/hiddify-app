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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/** Blur only the captured background; navigation icons and labels stay sharp. */
@Composable
internal fun NativeDock(backdrop: GraphicsLayer, backdropOrigin: Offset, modifier: Modifier = Modifier,
    content: @Composable () -> Unit) {
    val blurred = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(28.dp)
    val dark = MaterialTheme.colorScheme.surface.luminanceBelowHalf()
    val tint = if (dark) Color(0xFF34373D) else Color(0xFFE7E8EC)
    Box(modifier.clip(shape)
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawWithContent {
            if (Build.VERSION.SDK_INT >= 31 && backdrop.size.width > 0 && backdrop.size.height > 0) {
                // A separate layer avoids applying blur to the page or to dock content.
                blurred.renderEffect = BlurEffect(20.dp.toPx(), 20.dp.toPx(), TileMode.Clamp)
                blurred.record {
                    val offset = backdropOrigin - origin
                    translate(offset.x, offset.y) { drawLayer(backdrop) }
                }
                drawLayer(blurred)
            }
            drawContent()
        }
        .background(tint.copy(alpha = if (Build.VERSION.SDK_INT >= 31) .78f else .94f))
        .border(1.dp, Color.White.copy(alpha = .18f), shape)) { content() }
}

private fun Color.luminanceBelowHalf(): Boolean = luminance() < .5f
