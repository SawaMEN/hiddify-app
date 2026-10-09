package com.hiddify.hiddify.nativeui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.hiddify.hiddify.nativepreferences.NativeThemeMode

// Stable, opaque surfaces and restrained accents keep both themes readable.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CE5D2),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF174940),
    onPrimaryContainer = Color(0xFFD5F5EE),
    secondary = Color(0xFFB0C7FF),
    onSecondary = Color(0xFF213451),
    secondaryContainer = Color(0xFF324567),
    onSecondaryContainer = Color(0xFFE0EAFF),
    tertiary = Color(0xFFD4BFFF),
    onTertiary = Color(0xFF392850),
    tertiaryContainer = Color(0xFF503D68),
    onTertiaryContainer = Color(0xFFEBDDFF),
    surface = Color(0xFF121C24),
    surfaceContainerLowest = Color(0xFF091219),
    surfaceContainerLow = Color(0xFF17232D),
    surfaceContainer = Color(0xFF1E2D38),
    surfaceContainerHigh = Color(0xFF293945),
    surfaceContainerHighest = Color(0xFF354854),
    onSurface = Color(0xFFEAF3F7),
    onSurfaceVariant = Color(0xFFB7C9D3),
    outline = Color(0xFF8097A6),
    outlineVariant = Color(0xFF30434F),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF091219),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B59),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCEF4E9),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF435F91),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0EAFF),
    onSecondaryContainer = Color(0xFF213451),
    tertiary = Color(0xFF725498),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEBDDFF),
    onTertiaryContainer = Color(0xFF291641),
    surface = Color(0xFFF7FAFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDF4F5),
    surfaceContainerHigh = Color(0xFFE4ECEF),
    surfaceContainerHighest = Color(0xFFD9E4E8),
    onSurface = Color(0xFF162A33),
    onSurfaceVariant = Color(0xFF4B626D),
    outline = Color(0xFF718792),
    outlineVariant = Color(0xFFCADADD),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFFFFFFF),
)

@Composable
fun NativeAppTheme(mode: NativeThemeMode, accent: com.hiddify.hiddify.nativepreferences.NativeAccentColor = com.hiddify.hiddify.nativepreferences.NativeAccentColor.GRAY, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    val base = if (dark) DarkColors else LightColors
    // Four coordinated primary tones for each accent: foreground, text, container, container text.
    val tones = when (accent) {
        com.hiddify.hiddify.nativepreferences.NativeAccentColor.GRAY -> if (dark)
            listOf(0xFFC3C7CF, 0xFF2B3038, 0xFF414750, 0xFFE0E3EB) else listOf(0xFF575E68, 0xFFFFFFFF, 0xFFE0E3EB, 0xFF171C24)
        com.hiddify.hiddify.nativepreferences.NativeAccentColor.BLUE -> if (dark)
            listOf(0xFFACC7FF, 0xFF12305C, 0xFF2C4774, 0xFFD8E3FF) else listOf(0xFF365F99, 0xFFFFFFFF, 0xFFD8E3FF, 0xFF001B3E)
        com.hiddify.hiddify.nativepreferences.NativeAccentColor.VIOLET -> if (dark)
            listOf(0xFFD0BCFF, 0xFF381E72, 0xFF4F378B, 0xFFEADDFF) else listOf(0xFF6750A4, 0xFFFFFFFF, 0xFFEADDFF, 0xFF21005D)
        com.hiddify.hiddify.nativepreferences.NativeAccentColor.ROSE -> if (dark)
            listOf(0xFFFFB1C5, 0xFF5E1133, 0xFF7B294A, 0xFFFFD9E2) else listOf(0xFF984061, 0xFFFFFFFF, 0xFFFFD9E2, 0xFF3E001C)
        com.hiddify.hiddify.nativepreferences.NativeAccentColor.ORANGE -> if (dark)
            listOf(0xFFFFB787, 0xFF542100, 0xFF753509, 0xFFFFDBC4) else listOf(0xFFFFB787, 0xFF542100, 0xFFFFE6D5, 0xFF331000)
        com.hiddify.hiddify.nativepreferences.NativeAccentColor.GREEN -> if (dark)
            listOf(0xFFA2D4AB, 0xFF0D381E, 0xFF285033, 0xFFBDEFC6) else listOf(0xFF356A4A, 0xFFFFFFFF, 0xFFBDEFC6, 0xFF00210D)
        com.hiddify.hiddify.nativepreferences.NativeAccentColor.BROWN -> if (dark)
            listOf(0xFFF1BCA0, 0xFF4B2816, 0xFF663E2A, 0xFFFFDBC8) else listOf(0xFF80543D, 0xFFFFFFFF, 0xFFFFDBC8, 0xFF301407)
        com.hiddify.hiddify.nativepreferences.NativeAccentColor.AMBER -> if (dark)
            listOf(0xFFE7C36D, 0xFF3D2E00, 0xFF574419, 0xFFFFE1A1) else listOf(0xFF735B18, 0xFFFFFFFF, 0xFFFFE1A1, 0xFF251A00)
    }
    val colors = base.copy(primary = Color(tones[0]), onPrimary = Color(tones[1]),
        primaryContainer = Color(tones[2]), onPrimaryContainer = Color(tones[3]))
    val typography = Typography().let { defaults ->
        defaults.copy(
            headlineSmall = defaults.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall = defaults.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        )
    }
    MaterialTheme(
        colorScheme = colors.copy(background = if (mode == NativeThemeMode.BLACK) Color.Black else colors.surfaceContainerLowest),
        typography = typography,
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp),
        ),
        content = {
            CompositionLocalProvider(LocalNativeMotionEnabled provides nativeMotionEnabled()) {
                content()
            }
        },
    )
}
