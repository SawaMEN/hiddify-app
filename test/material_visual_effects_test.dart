import 'package:flutter/material.dart' as legacy;
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/core/theme/app_theme.dart';
import 'package:hiddify/core/theme/app_theme_mode.dart';
import 'package:hiddify/core/theme/visual_effects.dart';
import 'package:hiddify/core/widget/glass_surface.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  test('platform color scheme keeps its accessible foreground pairs', () {
    final dynamicScheme = ColorScheme.fromSeed(seedColor: Colors.orange, brightness: Brightness.dark);
    final theme = AppTheme(AppThemeMode.dark, '').darkTheme(dynamicScheme);
    expect(theme.colorScheme, dynamicScheme);
    expect(theme.filledButtonTheme.style!.foregroundColor!.resolve({}), dynamicScheme.onPrimary);
    expect(AppTheme(AppThemeMode.black, '').darkTheme(dynamicScheme).canvasColor, Colors.black);
  });

  for (final scenario in [
    (platform: TargetPlatform.android, mode: 'automatic', accessible: false, blur: false, motion: true),
    (platform: TargetPlatform.linux, mode: 'automatic', accessible: false, blur: true, motion: true),
    (platform: TargetPlatform.android, mode: 'quality', accessible: false, blur: true, motion: true),
    (platform: TargetPlatform.linux, mode: 'reduced', accessible: false, blur: false, motion: false),
    (platform: TargetPlatform.android, mode: 'quality', accessible: true, blur: false, motion: false),
  ]) {
    testWidgets('effect policy: $scenario', (tester) async {
      SharedPreferences.setMockInitialValues({'visual_effects': scenario.mode});
      final preferences = await SharedPreferences.getInstance();
      final container = ProviderContainer(
        overrides: [sharedPreferencesProvider.overrideWith((ref) async => preferences)],
      );
      addTearDown(container.dispose);
      await container.read(sharedPreferencesProvider.future);
      bool? blur;
      Duration? motion;
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: AppTheme(AppThemeMode.dark, '').darkTheme(null).copyWith(platform: scenario.platform),
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: scenario.accessible),
              child: VisualEffectsHost(child: child!),
            ),
            home: Builder(
              builder: (context) {
                blur = VisualEffects.blurOf(context);
                motion = VisualEffects.durationOf(context);
                return const Scaffold(
                  body: Center(child: GlassSurface(blur: true, child: SizedBox(width: 48, height: 48))),
                );
              },
            ),
          ),
        ),
      );
      expect(blur, scenario.blur);
      expect(motion == Duration.zero, !scenario.motion);
      expect(find.byType(BackdropFilter), scenario.blur ? findsOneWidget : findsNothing);
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('legacy package widgets receive theme and localizations', (tester) async {
    legacy.ThemeData? bridgedTheme;
    legacy.MaterialLocalizations? localizations;
    final modern = AppTheme(AppThemeMode.dark, '').darkTheme(null);
    await tester.pumpWidget(
      MaterialApp(
        theme: modern,
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        home: MaterialUiCompatibilityBridge(
          child: Builder(
            builder: (context) {
              bridgedTheme = legacy.Theme.of(context);
              localizations = legacy.MaterialLocalizations.of(context);
              return const legacy.Scaffold(body: legacy.TextField());
            },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(bridgedTheme!.colorScheme.primary, modern.colorScheme.primary);
    expect(localizations, isNotNull);
    expect(tester.takeException(), isNull);
  });
}
