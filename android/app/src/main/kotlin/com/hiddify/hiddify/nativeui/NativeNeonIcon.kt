package com.hiddify.hiddify.nativeui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
        modifier = modifier.drawBehind {
            if (tint != Color.Unspecified && size.minDimension > 0f) {
                drawCircle(Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = tint.alpha * .18f), Color.Transparent),
                    center = center, radius = size.minDimension * .85f,
                ), radius = size.minDimension * .85f)
            }
        },
        tint = tint,
    )
}
