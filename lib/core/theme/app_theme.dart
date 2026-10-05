import 'package:cupertino_ui/cupertino_ui.dart';
import 'package:material_ui/material_ui.dart';
import 'package:hiddify/core/theme/app_theme_mode.dart';
import 'package:hiddify/core/theme/theme_extensions.dart';

class AppTheme {
  AppTheme(this.mode, this.fontFamily);

  final AppThemeMode mode;
  final String fontFamily;
  ColorScheme? _lightScheme;
  ColorScheme? _darkScheme;
  ThemeData? _lightTheme;
  ThemeData? _darkTheme;

  static const Color _cyan = Color(0xFF00E5FF);
  static const Color _magenta = Color(0xFFFF3FD1);
  static const Color _violet = Color(0xFF8B5CF6);
  static const Color _green = Color(0xFF39E58C);

  static const double _radiusSmall = 16;
  static const double _radiusMedium = 24;
  static const double _radiusLarge = 24;
  static const double _radiusExtraLarge = 28;

  ThemeData lightTheme(ColorScheme? dynamicScheme) {
    if (_lightTheme == null || _lightScheme != dynamicScheme) {
      _lightScheme = dynamicScheme;
      _lightTheme = _buildTheme(Brightness.light, dynamicScheme);
    }
    return _lightTheme!;
  }

  ThemeData darkTheme(ColorScheme? dynamicScheme) {
    if (_darkTheme == null || _darkScheme != dynamicScheme) {
      _darkScheme = dynamicScheme;
      _darkTheme = _buildTheme(Brightness.dark, dynamicScheme);
    }
    return _darkTheme!;
  }

  ThemeData _buildTheme(Brightness brightness, ColorScheme? dynamicScheme) {
    final isDark = brightness == Brightness.dark;
    final fallback = ColorScheme.fromSeed(seedColor: isDark ? _violet : const Color(0xFF5B35D5), brightness: brightness)
        .copyWith(
          primary: isDark ? _cyan : const Color(0xFF006874),
          onPrimary: isDark ? const Color(0xFF00363D) : Colors.white,
          primaryContainer: isDark ? const Color(0xFF004F58) : const Color(0xFFA5EEFA),
          onPrimaryContainer: isDark ? const Color(0xFFA5EEFA) : const Color(0xFF001F24),
          secondary: isDark ? _magenta : const Color(0xFF8F1A73),
          onSecondary: isDark ? const Color(0xFF3B002F) : Colors.white,
          secondaryContainer: isDark ? const Color(0xFF5B1248) : const Color(0xFFFFD8EE),
          onSecondaryContainer: isDark ? const Color(0xFFFFD8EE) : const Color(0xFF3B002F),
          tertiary: isDark ? _green : const Color(0xFF006C48),
          tertiaryContainer: isDark ? const Color(0xFF005137) : const Color(0xFF8DF8C2),
          onTertiary: isDark ? const Color(0xFF003824) : Colors.white,
          onTertiaryContainer: isDark ? const Color(0xFF8DF8C2) : const Color(0xFF002113),
          surface: isDark ? const Color(0xFF111318) : const Color(0xFFFAF8FF),
          surfaceContainerLowest: isDark ? const Color(0xFF0B0E12) : Colors.white,
          surfaceContainerLow: isDark ? const Color(0xFF191C20) : const Color(0xFFF4F2FA),
          surfaceContainer: isDark ? const Color(0xFF1D2024) : const Color(0xFFEEECF4),
          surfaceContainerHigh: isDark ? const Color(0xFF272A2F) : const Color(0xFFE8E6EE),
          surfaceContainerHighest: isDark ? const Color(0xFF32353A) : const Color(0xFFE2E0E8),
          onSurface: isDark ? const Color(0xFFE3E2E8) : const Color(0xFF1B1B20),
          onSurfaceVariant: isDark ? const Color(0xFFC2C7CE) : const Color(0xFF46474D),
          outline: isDark ? const Color(0xFF8C9198) : const Color(0xFF76777D),
          outlineVariant: isDark ? const Color(0xFF42474D) : const Color(0xFFC6C6CD),
          error: isDark ? const Color(0xFFFFB4AB) : const Color(0xFFBA1A1A),
        );

    // Keep platform-generated foreground/background pairs together for contrast.
    final scheme = dynamicScheme ?? fallback;
    final scaffoldColor = mode.trueBlack && isDark ? Colors.black : scheme.surfaceContainerLowest;

    final baseTheme = ThemeData(
      useMaterial3: true,
      brightness: brightness,
      colorScheme: scheme,
      scaffoldBackgroundColor: Colors.transparent,
      canvasColor: scaffoldColor,
      fontFamily: fontFamily,
      fontFamilyFallback: const ["Shabnam", "Emoji"],
      visualDensity: VisualDensity.standard,
      splashFactory: InkRipple.splashFactory,
      splashColor: scheme.primary.withValues(alpha: .10),
      highlightColor: scheme.primary.withValues(alpha: .06),
      focusColor: scheme.primary.withValues(alpha: .12),
      hoverColor: scheme.primary.withValues(alpha: .08),
      dividerColor: scheme.outlineVariant,
      extensions: <ThemeExtension<dynamic>>{
        ConnectionButtonTheme(idleColor: scheme.primary, connectedColor: scheme.tertiary),
      },
    );

    final textTheme = baseTheme.textTheme.copyWith(
      headlineLarge: baseTheme.textTheme.headlineLarge?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -1.2),
      headlineMedium: baseTheme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w700, letterSpacing: -.8),
      headlineSmall: baseTheme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w600, letterSpacing: -.5),
      titleLarge: baseTheme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w500),
      titleMedium: baseTheme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
      labelLarge: baseTheme.textTheme.labelLarge?.copyWith(fontWeight: FontWeight.w600),
    );

    return baseTheme.copyWith(
      textTheme: textTheme,
      appBarTheme: AppBarTheme(
        elevation: 0,
        scrolledUnderElevation: 0,
        backgroundColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        foregroundColor: scheme.onSurface,
        centerTitle: false,
        titleTextStyle: textTheme.titleLarge?.copyWith(color: scheme.onSurface),
        iconTheme: IconThemeData(color: scheme.onSurfaceVariant),
        actionsIconTheme: IconThemeData(color: scheme.onSurfaceVariant),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: scheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        shadowColor: Colors.transparent,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(_radiusMedium),
          side: BorderSide(color: scheme.outlineVariant),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        height: 72,
        elevation: 0,
        backgroundColor: scheme.surfaceContainerLow,
        indicatorColor: scheme.primaryContainer,
        shadowColor: Colors.transparent,
        surfaceTintColor: Colors.transparent,
        iconTheme: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return IconThemeData(color: scheme.onPrimaryContainer);
          }
          return IconThemeData(color: scheme.onSurfaceVariant);
        }),
        labelTextStyle: WidgetStateProperty.resolveWith((states) {
          final selected = states.contains(WidgetState.selected);
          return textTheme.labelMedium?.copyWith(
            color: selected ? scheme.onSurface : scheme.onSurfaceVariant,
            fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
          );
        }),
      ),
      navigationRailTheme: NavigationRailThemeData(
        elevation: 0,
        backgroundColor: scheme.surfaceContainerLow,
        indicatorColor: scheme.primaryContainer,
        selectedIconTheme: IconThemeData(color: scheme.onPrimaryContainer),
        unselectedIconTheme: IconThemeData(color: scheme.onSurfaceVariant),
        selectedLabelTextStyle: textTheme.labelMedium?.copyWith(color: scheme.onSurface, fontWeight: FontWeight.w600),
        unselectedLabelTextStyle: textTheme.labelMedium?.copyWith(color: scheme.onSurfaceVariant),
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
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        labelStyle: TextStyle(color: scheme.onSurfaceVariant),
        floatingLabelStyle: TextStyle(color: scheme.primary),
        hintStyle: TextStyle(color: scheme.onSurfaceVariant),
        prefixIconColor: scheme.onSurfaceVariant,
        suffixIconColor: scheme.onSurfaceVariant,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(_radiusSmall),
          borderSide: BorderSide(color: scheme.outline),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(_radiusSmall),
          borderSide: BorderSide(color: scheme.outline),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(_radiusSmall),
          borderSide: BorderSide(color: scheme.primary, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(_radiusSmall),
          borderSide: BorderSide(color: scheme.error),
        ),
        focusedErrorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(_radiusSmall),
          borderSide: BorderSide(color: scheme.error, width: 2),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10),
          backgroundColor: scheme.primary,
          foregroundColor: scheme.onPrimary,
          disabledBackgroundColor: scheme.onSurface.withValues(alpha: .12),
          disabledForegroundColor: scheme.onSurface.withValues(alpha: .38),
          elevation: 0,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          textStyle: textTheme.labelLarge,
        ).copyWith(backgroundBuilder: _glassButtonLayer),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10),
          backgroundColor: scheme.surfaceContainerLow,
          foregroundColor: scheme.primary,
          elevation: 1,
          shadowColor: scheme.shadow.withValues(alpha: .18),
          surfaceTintColor: Colors.transparent,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          textStyle: textTheme.labelLarge,
        ).copyWith(backgroundBuilder: _glassButtonLayer),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 10),
          foregroundColor: scheme.primary,
          side: BorderSide(color: scheme.outline),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          textStyle: textTheme.labelLarge,
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          foregroundColor: scheme.primary,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          textStyle: textTheme.labelLarge,
        ),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        elevation: 0,
        focusElevation: 0,
        hoverElevation: 0,
        highlightElevation: 0,
        backgroundColor: scheme.primaryContainer,
        foregroundColor: scheme.onPrimaryContainer,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(_radiusLarge)),
      ),
      iconButtonTheme: IconButtonThemeData(
        style: ButtonStyle(
          foregroundColor: WidgetStateProperty.resolveWith((states) {
            if (states.contains(WidgetState.disabled)) {
              return scheme.onSurface.withValues(alpha: .38);
            }
            if (states.contains(WidgetState.selected)) {
              return scheme.onPrimaryContainer;
            }
            return scheme.onSurfaceVariant;
          }),
          backgroundColor: WidgetStateProperty.resolveWith((states) {
            if (states.contains(WidgetState.selected)) {
              return scheme.primaryContainer;
            }
            return Colors.transparent;
          }),
          shape: WidgetStatePropertyAll(RoundedRectangleBorder(borderRadius: BorderRadius.circular(20))),
        ),
      ),
      chipTheme: baseTheme.chipTheme.copyWith(
        backgroundColor: Colors.transparent,
        selectedColor: scheme.secondaryContainer,
        disabledColor: scheme.onSurface.withValues(alpha: .12),
        side: BorderSide(color: scheme.outlineVariant),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(_radiusSmall)),
        labelStyle: textTheme.labelLarge?.copyWith(color: scheme.onSurfaceVariant),
        secondaryLabelStyle: textTheme.labelLarge?.copyWith(color: scheme.onSecondaryContainer),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      ),
      listTileTheme: ListTileThemeData(
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
        iconColor: scheme.onSurfaceVariant,
        textColor: scheme.onSurface,
        selectedColor: scheme.onSecondaryContainer,
        selectedTileColor: scheme.secondaryContainer,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(_radiusMedium)),
      ),
      dialogTheme: DialogThemeData(
        elevation: 0,
        backgroundColor: scheme.surfaceContainerHigh,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(_radiusExtraLarge)),
        titleTextStyle: textTheme.headlineSmall?.copyWith(color: scheme.onSurface),
        contentTextStyle: textTheme.bodyMedium?.copyWith(color: scheme.onSurfaceVariant),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        elevation: 1,
        modalElevation: 1,
        backgroundColor: scheme.surfaceContainerLow,
        modalBackgroundColor: scheme.surfaceContainerLow,
        surfaceTintColor: Colors.transparent,
        showDragHandle: true,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(_radiusExtraLarge)),
        ),
      ),
      popupMenuTheme: PopupMenuThemeData(
        elevation: 3,
        color: scheme.surfaceContainer,
        surfaceTintColor: Colors.transparent,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(_radiusSmall)),
        textStyle: textTheme.labelLarge?.copyWith(color: scheme.onSurface),
      ),
      dividerTheme: DividerThemeData(color: scheme.outlineVariant, thickness: 1, space: 1),
      switchTheme: SwitchThemeData(
        trackColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.disabled)) {
            return scheme.surfaceContainerHighest;
          }
          if (states.contains(WidgetState.selected)) {
            return scheme.primary;
          }
          return scheme.surfaceContainerHighest;
        }),
        thumbColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.disabled)) {
            return scheme.onSurface.withValues(alpha: .38);
          }
          if (states.contains(WidgetState.selected)) {
            return scheme.onPrimary;
          }
          return scheme.outline;
        }),
        trackOutlineColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return Colors.transparent;
          }
          return scheme.outline;
        }),
      ),
      checkboxTheme: CheckboxThemeData(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(2)),
        fillColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return scheme.primary;
          }
          return Colors.transparent;
        }),
        checkColor: WidgetStatePropertyAll(scheme.onPrimary),
        side: BorderSide(color: scheme.onSurfaceVariant, width: 2),
      ),
      radioTheme: RadioThemeData(
        fillColor: WidgetStateProperty.resolveWith((states) {
          if (states.contains(WidgetState.selected)) {
            return scheme.primary;
          }
          return scheme.onSurfaceVariant;
        }),
      ),
      progressIndicatorTheme: ProgressIndicatorThemeData(
        color: scheme.primary,
        linearTrackColor: scheme.surfaceContainerHighest,
        circularTrackColor: scheme.surfaceContainerHighest,
      ),
      tabBarTheme: TabBarThemeData(
        indicatorColor: scheme.primary,
        labelColor: scheme.primary,
        unselectedLabelColor: scheme.onSurfaceVariant,
        dividerColor: scheme.outlineVariant,
        labelStyle: textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w600),
        unselectedLabelStyle: textTheme.titleSmall,
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        elevation: 3,
        backgroundColor: scheme.inverseSurface,
        contentTextStyle: textTheme.bodyMedium?.copyWith(color: scheme.onInverseSurface),
        actionTextColor: scheme.inversePrimary,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(_radiusSmall)),
      ),
      tooltipTheme: TooltipThemeData(
        decoration: BoxDecoration(color: scheme.inverseSurface, borderRadius: BorderRadius.circular(_radiusSmall)),
        textStyle: textTheme.bodySmall?.copyWith(color: scheme.onInverseSurface),
      ),
      textSelectionTheme: TextSelectionThemeData(
        cursorColor: scheme.primary,
        selectionColor: scheme.primary.withValues(alpha: .28),
        selectionHandleColor: scheme.primary,
      ),
    );
  }

  Color scaffoldColorFor(bool dark, ColorScheme? light, ColorScheme? darkScheme) => dark && mode.trueBlack
      ? Colors.black
      : (dark ? darkScheme?.surfaceContainerLowest : light?.surfaceContainerLowest) ??
            (dark ? const Color(0xFF0B0E12) : const Color(0xFFFAF8FF));

  /// Specular highlight without adding a backdrop filter per button.
  static Widget _glassButtonLayer(BuildContext context, Set<WidgetState> states, Widget? child) {
    if (states.contains(WidgetState.disabled)) return child ?? const SizedBox.shrink();
    return DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: Colors.white.withValues(alpha: .18)),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Colors.white.withValues(alpha: .12), Colors.white.withValues(alpha: 0)],
        ),
      ),
      child: child,
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
          primaryColor: isDark ? _cyan : const Color(0xFF006874),
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
          barBackgroundColor: isDark ? const Color(0xF2111318) : const Color(0xF2FAF8FF),
          scaffoldBackgroundColor: scaffoldColorFor(isDark, lightColorScheme, darkColorScheme),
        ),
      ),
    );
  }
}
