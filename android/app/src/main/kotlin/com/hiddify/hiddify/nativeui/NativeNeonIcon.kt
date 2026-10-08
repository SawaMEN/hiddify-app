package com.hiddify.hiddify.nativeui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

/** The halo is drawn behind the glyph; vector alpha and accessibility stay intact. */
@Composable
internal fun NativeNeonIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    androidx.compose.material3.Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = modifier.drawWithCache {
            val radius = size.minDimension * .85f
            val halo = if (tint != Color.Unspecified && radius > 0f) Brush.radialGradient(
                colors = listOf(tint.copy(alpha = tint.alpha * .18f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f), radius = radius,
            ) else null
            onDrawBehind { if (halo != null) drawCircle(halo, radius = radius) }
        },
        tint = tint,
    )
}
