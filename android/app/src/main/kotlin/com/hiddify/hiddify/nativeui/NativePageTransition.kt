package com.hiddify.hiddify.nativeui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Fixed viewport, fade-through and a small entrance offset; never morph page sizes. */
@Composable
internal fun NativePageTransition(
    page: String,
    direction: Int,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit,
) {
    val motion = LocalNativeMotionEnabled.current
    val distance = with(LocalDensity.current) { (if (direction == 0) 8.dp else 20.dp).roundToPx() }
    val layoutSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    AnimatedContent(
        targetState = page,
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.TopCenter,
        contentKey = { it },
        transitionSpec = {
            if (!motion) {
                (EnterTransition.None togetherWith ExitTransition.None).using(null)
            } else {
                // The old screen is fully faded before new text appears. Translation and alpha
                // affect drawing only; no animated measurement or full-width screen sliding.
                val entrance = tween<Float>(190, delayMillis = 90, easing = FastOutSlowInEasing)
                val translation = tween<androidx.compose.ui.unit.IntOffset>(190, delayMillis = 90,
                    easing = FastOutSlowInEasing)
                val slide = if (direction == 0) slideInVertically(translation) { distance }
                    else slideInHorizontally(translation) { distance * direction * layoutSign }
                ((fadeIn(entrance) + slide) togetherWith fadeOut(tween(80))).using(null)
            }
        },
        label = "Page entrance",
    ) { visiblePage -> content(visiblePage) }
}
