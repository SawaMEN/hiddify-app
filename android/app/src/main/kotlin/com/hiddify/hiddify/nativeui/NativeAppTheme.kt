package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.hiddify.hiddify.nativepreferences.NativeThemeMode

@Composable
fun NativeAppTheme(mode: NativeThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val colors = when {
        mode == NativeThemeMode.BLACK -> darkColorScheme(background = Color.Black, surface = Color.Black)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
