package com.hiddify.hiddify.nativeui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

/** Fade-through keeps successive connection labels from being drawn over one another. */
@Composable
internal fun NativeAnimatedText(text: String, label: String, content: @Composable (String) -> Unit) {
    val motion = LocalNativeMotionEnabled.current
    AnimatedContent(targetState = text, contentAlignment = Alignment.Center,
        transitionSpec = {
            if (!motion) (EnterTransition.None togetherWith ExitTransition.None).using(null)
            else (fadeIn(tween(180, delayMillis = 90, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(80))).using(null)
        }, label = label) { content(it) }
}
