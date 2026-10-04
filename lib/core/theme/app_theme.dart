import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:hiddify/core/theme/app_theme_mode.dart';
import 'package:hiddify/core/theme/theme_extensions.dart';

class AppTheme {
  AppTheme(this.mode, this.fontFamily);

  final AppThemeMode mode;
  final String fontFamily;

  static const Color _cyan = Color(0xFF00F5FF);
  static const Color _magenta = Color(0xFFFF2BD6);
  static const Color _violet = Color(0xFF8B5CF6);
  static const Color _green = Color(0xFF39FF88);

  ThemeData lightTheme(ColorScheme? _) => _buildTheme(Brightness.light);

  ThemeData darkTheme(ColorScheme? _) => _buildTheme(Brightness.dark);

  ThemeData _buildTheme(Brightness brightness) {
    final isDark = brightness == Brightness.dark;
    final scheme = ColorScheme.fromSeed(
      seedColor: isDark ? _violet : const Color(0xFF5B35D5),
      brightness: brightness,
    ).copyWith(
      primary: isDark ? _cyan : const Color(0xFF006B73),
      onPrimary: isDark ? const Color(0xFF001F22) : Colors.white,
      primaryContainer: isDark ? const Color(0xFF07343B) : const Color(0xFFD4F8FB),
      onPrimaryContainer: isDark ? const Color(0xFFB9FAFF) : const Color(0xFF002022),
      secondary: isDark ? _magenta : const Color(0xFF8A166F),
      onSecondary: Colors.white,
      secondaryContainer: isDark ? const Color(0xFF42143A) : const Color(0xFFFFD7F3),
      onSecondaryContainer: isDark ? const Color(0xFFFFD8F4) : const Color(0xFF35002A),
      tertiary: isDark ? _green : const Color(0xFF006C45),
      surface: isDark ? const Color(0xFF070913) : const Color(0xFFF8F7FF),
      surfaceContainerLowest: isDark ? const Color(0xFF03040A) : Colors.white,
      surfaceContainerLow: isDark ? const Color(0xFF0B0E1A) : const Color(0xFFF2F0FB),
      surfaceContainer: isDark ? const Color(0xFF101424) : const Color(0xFFECEAF5),
      surfaceContainerHigh: isDark ? const Color(0xFF171C2F) : const Color(0xFFE5E2EF),
      surfaceContainerHighest: isDark ? const Color(0xFF20263B) : const Color(0xFFDDD9E8),
      onSurface: isDark ? const Color(0xFFE9F9FF) : const Color(0xFF171821),
      onSurfaceVariant: isDark ? const Color(0xFF9EB3C4) : const Color(0xFF4C4D5A),
      outline: isDark ? const Color(0xFF55707E) : const Color(0xFF747685),
      outlineVariant: isDark ? const Color(0xFF263642) : const Color(0xFFC8C6D2),
      error: isDark ? const Color(0xFFFF6B8B) : const Color(0xFFBA1A1A),
    );

    final baseTheme = ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: mode.trueBlack && isDark ? Colors.black : scheme.surface,
      canvasColor: scheme.surface,
      fontFamily: fontFamily,
      splashColor: scheme.primary.withValues(alpha: .10),
      highlightColor: scheme.primary.withValues(alpha: .06),
      extensions: <ThemeExtension<dynamic>>{
        isDark ? ConnectionButtonTheme.dark : ConnectionButtonTheme.light,
      },
    );

    return baseTheme.copyWith(
      appBarTheme: AppBarTheme(
        elevation: 0,
        scrolledUnderElevation: 0,
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        foregroundColor: scheme.onSurface,
        iconTheme: IconThemeData(color: scheme.primary),
      ),
      navigationBarTheme: NavigationBarThemeData(
        elevation: 0,
        backgroundColor: scheme.surfaceContainerLow,
        indicatorColor: scheme.primary.withValues(alpha: isDark ? .18 : .14),
        shadowColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
      ),
      navigationRailTheme: NavigationRailThemeData(
        elevation: 0,
        backgroundColor: scheme.surfaceContainerLow,
        indicatorColor: scheme.primary.withValues(alpha: isDark ? .18 : .14),
        selectedIconTheme: IconThemeData(color: scheme.primary),
        unselectedIconTheme: IconThemeData(color: scheme.onSurfaceVariant),
      ),
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        elevation: 0,
        backgroundColor: scheme.surfaceContainerLow,
        selectedItemColor: scheme.primary,
        unselectedItemColor: scheme.onSurfaceVariant,
        type: BottomNavigationBarType.fixed,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: scheme.surfaceContainerLow,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.outlineVariant),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: scheme.primary, width: 1.6),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(48, 44),
          backgroundColor: scheme.primary,
          foregroundColor: scheme.onPrimary,
          shadowColor: scheme.primary.withValues(alpha: .45),
          elevation: isDark ? 4 : 2,
          shape: const StadiumBorder(),
          textStyle: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          minimumSize: const Size(48, 44),
          backgroundColor: scheme.primary,
          foregroundColor: scheme.onPrimary,
          shadowColor: scheme.primary.withValues(alpha: .45),
          elevation: isDark ? 4 : 2,
          shape: const StadiumBorder(),
          textStyle: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(48, 44),
          foregroundColor: scheme.primary,
          side: BorderSide(color: scheme.primary.withValues(alpha: .72)),
          shape: const StadiumBorder(),
          textStyle: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: scheme.primary,
          textStyle: const TextStyle(fontWeight: FontWeight.w700),
        ),
      ),
      dividerTheme: DividerThemeData(
        color: scheme.outlineVariant.withValues(alpha: .7),
        thickness: 1,
        space: 1,
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: scheme.primary,
        linearTrackColor: scheme.primary.withValues(alpha: .12),
        circularTrackColor: scheme.primary.withValues(alpha: .12),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: scheme.surfaceContainerHigh,
        contentTextStyle: TextStyle(color: scheme.onSurface),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: BorderSide(color: scheme.primary.withValues(alpha: .35)),
        ),
      ),
      tooltipTheme: TooltipThemeData(
        decoration: BoxDecoration(
          color: scheme.surfaceContainerHighest,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: scheme.primary.withValues(alpha: .30)),
          boxShadow: [
            BoxShadow(
              blurRadius: 18,
              color: scheme.primary.withValues(alpha: isDark ? .16 : .08),
            ),
          ],
        ),
        textStyle: TextStyle(color: scheme.onSurface),
      ),
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: scheme.primary,
        selectionColor: scheme.primary.withValues(alpha: .25),
        selectionHandleColor: scheme.secondary,
      ),
    );
  }

  CupertinoThemeData cupertinoThemeData(bool sysDark, ColorScheme? lightColorScheme, ColorScheme? darkColorScheme) {
    final bool isDark = switch (mode) {
      AppThemeMode.system => sysDark,
      AppThemeMode.light => false,
      AppThemeMode.dark => true,
      AppThemeMode.black => true,
    };
    final def = CupertinoThemeData(brightness: isDark ? Brightness.dark : Brightness.light);
    final defaultMaterialTheme = isDark ? darkTheme(darkColorScheme) : lightTheme(lightColorScheme);
    return MaterialBasedCupertinoThemeData(
      materialTheme: defaultMaterialTheme.copyWith(
        cupertinoOverrideTheme: def.copyWith(
          primaryColor: isDark ? _cyan : const Color(0xFF006B73),
          textTheme: CupertinoTextThemeData(
            textStyle: def.textTheme.textStyle.copyWith(fontFamily: fontFamily),
            actionTextStyle: def.textTheme.actionTextStyle.copyWith(fontFamily: fontFamily),
            navActionTextStyle: def.textTheme.navActionTextStyle.copyWith(fontFamily: fontFamily),
            navTitleTextStyle: def.textTheme.navTitleTextStyle.copyWith(fontFamily: fontFamily),
            navLargeTitleTextStyle: def.textTheme.navLargeTitleTextStyle.copyWith(fontFamily: fontFamily),
            pickerTextStyle: def.textTheme.pickerTextStyle.copyWith(fontFamily: fontFamily),
            dateTimePickerTextStyle: def.textTheme.dateTimePickerTextStyle.copyWith(fontFamily: fontFamily),
            tabLabelTextStyle: def.textTheme.tabLabelTextStyle.copyWith(fontFamily: fontFamily),
          ),
          barBackgroundColor: isDark ? const Color(0xF20B0E1A) : const Color(0xF2F8F7FF),
          scaffoldBackgroundColor: defaultMaterialTheme.scaffoldBackgroundColor,
        ),
      ),
    );
  }
}
