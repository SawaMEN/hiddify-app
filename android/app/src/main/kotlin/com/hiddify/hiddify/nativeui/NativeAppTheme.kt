package com.hiddify.hiddify.nativeui

import android.os.Build
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiddify.hiddify.R
import com.hiddify.hiddify.nativepreferences.NativeThemeMode

// Keep these tokens aligned with lib/core/theme/app_theme.dart.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFA5EEFA),
    secondary = Color(0xFFFF3FD1),
    onSecondary = Color(0xFF3B002F),
    secondaryContainer = Color(0xFF5B1248),
    onSecondaryContainer = Color(0xFFFFD8EE),
    tertiary = Color(0xFF39E58C),
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF005137),
    onTertiaryContainer = Color(0xFF8DF8C2),
    surface = Color(0xFF111318),
    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
    onSurface = Color(0xFFE3E2E8),
    onSurfaceVariant = Color(0xFFC2C7CE),
    outline = Color(0xFF8C9198),
    outlineVariant = Color(0xFF42474D),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF0B0E12),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFA5EEFA),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF8F1A73),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD8EE),
    onSecondaryContainer = Color(0xFF3B002F),
    tertiary = Color(0xFF006C48),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF8DF8C2),
    onTertiaryContainer = Color(0xFF002113),
    surface = Color(0xFFFAF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F2FA),
    surfaceContainer = Color(0xFFEEECF4),
    surfaceContainerHigh = Color(0xFFE8E6EE),
    surfaceContainerHighest = Color(0xFFE2E0E8),
    onSurface = Color(0xFF1B1B20),
    onSurfaceVariant = Color(0xFF46474D),
    outline = Color(0xFF76777D),
    outlineVariant = Color(0xFFC6C6CD),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFFFFFFF),
)

private val Manrope = FontFamily(
    Font(R.font.manrope_regular, weight = FontWeight.Normal),
    Font(R.font.manrope_medium, weight = FontWeight.Medium),
    Font(R.font.manrope_semibold, weight = FontWeight.SemiBold),
    Font(R.font.manrope_bold, weight = FontWeight.Bold),
)
private val BaseTypography = Typography()
private val AppTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = Manrope),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = Manrope),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = Manrope),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = Manrope),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = Manrope),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = Manrope),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = Manrope),
)

@Composable
fun NativeAppTheme(mode: NativeThemeMode, content: @Composable () -> Unit) {
    val dark = mode.isDark(isSystemInDarkTheme())
    // DynamicColorBuilder in the Dart app also follows Android's wallpaper palette.
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalInspectionMode.current) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors.copy(background = if (mode == NativeThemeMode.BLACK) Color.Black else colors.surfaceContainerLowest),
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = { CompositionLocalProvider(LocalNativeMotionEnabled provides nativeMotionEnabled()) { content() } },
    )
}
