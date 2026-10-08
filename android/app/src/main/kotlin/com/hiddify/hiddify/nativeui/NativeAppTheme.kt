package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.Color
import com.hiddify.hiddify.nativepreferences.NativeThemeMode

// Stable, opaque surfaces and restrained accents keep both themes readable.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FDACD),
    onPrimary = Color(0xFF083C35),
    primaryContainer = Color(0xFF244F49),
    onPrimaryContainer = Color(0xFFC0EEE5),
    secondary = Color(0xFFB8C1E3),
    onSecondary = Color(0xFF293149),
    secondaryContainer = Color(0xFF404961),
    onSecondaryContainer = Color(0xFFDDE3FC),
    tertiary = Color(0xFFC0BDD5),
    onTertiary = Color(0xFF302D43),
    tertiaryContainer = Color(0xFF47445C),
    onTertiaryContainer = Color(0xFFE5DFF9),
    surface = Color(0xFF15191E),
    surfaceContainerLowest = Color(0xFF101419),
    surfaceContainerLow = Color(0xFF1B2026),
    surfaceContainer = Color(0xFF22272E),
    surfaceContainerHigh = Color(0xFF2C3239),
    surfaceContainerHighest = Color(0xFF363D45),
    onSurface = Color(0xFFE9EEF4),
    onSurfaceVariant = Color(0xFFBFC8D2),
    outline = Color(0xFF929DA9),
    outlineVariant = Color(0xFF424B56),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF101419),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF236C60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC0EEE5),
    onPrimaryContainer = Color(0xFF123E36),
    secondary = Color(0xFF555F7B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE3FC),
    onSecondaryContainer = Color(0xFF293149),
    tertiary = Color(0xFF625C78),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5DFF9),
    onTertiaryContainer = Color(0xFF211D32),
    surface = Color(0xFFF7F9FC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEEF2F6),
    surfaceContainerHigh = Color(0xFFE5EAF0),
    surfaceContainerHighest = Color(0xFFDCE3EA),
    onSurface = Color(0xFF18212B),
    onSurfaceVariant = Color(0xFF465360),
    outline = Color(0xFF717E8B),
    outlineVariant = Color(0xFFC6D0DA),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFFFFFFF),
)

@Composable
fun NativeAppTheme(mode: NativeThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = LocalConfiguration.current
    val preview = LocalInspectionMode.current
    val colors = remember(context, configuration, dark, preview) {
        if (!preview && android.os.Build.VERSION.SDK_INT >= 31) {
            if (dark) androidx.compose.material3.dynamicDarkColorScheme(context)
            else androidx.compose.material3.dynamicLightColorScheme(context)
        } else if (dark) DarkColors else LightColors
    }
    MaterialTheme(
        colorScheme = colors.copy(background = if (mode == NativeThemeMode.BLACK) Color.Black else colors.surfaceContainerLowest),
        typography = Typography(),
        shapes = Shapes(),
        content = { CompositionLocalProvider(LocalNativeMotionEnabled provides nativeMotionEnabled(),
            LocalTextSelectionColors provides TextSelectionColors(colors.primary, colors.primary.copy(alpha = .28f))) { content() } },
    )
}
