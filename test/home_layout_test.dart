import 'dart:io';
import 'package:flutter/services.dart';
import 'package:hiddify/features/stats/notifier/stats_notifier.dart';
import 'package:hiddify/features/connection/health/connection_health.dart';
import 'package:hiddify/features/proxy/selection/smart_selection.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/app_info/app_info_provider.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/model/app_info_entity.dart';
import 'package:hiddify/core/model/environment.dart';
import 'package:hiddify/core/theme/app_theme.dart';
import 'package:hiddify/core/theme/app_theme_mode.dart';
import 'package:hiddify/core/theme/visual_effects.dart';
import 'package:hiddify/core/widget/cyber_background.dart';
import 'package:hiddify/features/connection/model/connection_status.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/home/widget/home_page.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/features/proxy/active/active_proxy_notifier.dart';
import 'package:hiddify/features/settings/notifier/config_option/config_option_notifier.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore.pb.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:material_ui/material_ui.dart';

class _Profile extends ActiveProfile {
  @override
  Stream<ProfileEntity?> build() => Stream.value(
    ProfileEntity.local(id: 'preview', active: true, name: 'VetrOFF · Personal', lastUpdate: DateTime(2026)),
  );
}

class _Connection extends ConnectionNotifier {
  @override
  Stream<ConnectionStatus> build() => Stream.value(const Disconnected());
}

class _Proxy extends ActiveProxyNotifier {
  @override
  Stream<OutboundInfo> build() => Stream.value(OutboundInfo.create());
}

class _Options extends ConfigOptionNotifier {
  @override
  Future<bool> build() async => false;
}

class _Info extends AppInfo {
  @override
  Future<AppInfoEntity> build() async {
    final version = RegExp(r'^version:\s*([\d.]+)\+(\d+)', multiLine: true).firstMatch(File('pubspec.yaml').readAsStringSync())!;
    return AppInfoEntity(
      name: 'VetrOFF Client',
      version: version.group(1)!,
      buildNumber: version.group(2)!,
      release: Release.general,
      operatingSystem: 'android',
      operatingSystemVersion: 'test',
      environment: Environment.prod,
    );
  }
}

class _Stats extends StatsNotifier {
  @override
  Stream<SystemInfo> build() => Stream.value(SystemInfo.create());
}

class _Health extends ConnectionHealthNotifier {
  @override
  InternetHealth build() => InternetHealth.unchecked;
}

class _Smart extends SmartSelectionNotifier {
  @override
  String? build() => null;
}

void main() {
  for (final scenario in [
    (size: const Size(390, 844), scale: 1.0, rtl: false, dark: true),
    (size: const Size(320, 640), scale: 2.0, rtl: false, dark: false),
    (size: const Size(360, 640), scale: 1.5, rtl: true, dark: true),
  ]) {
    testWidgets('home fits $scenario', (tester) async {
      await tester.binding.setSurfaceSize(scenario.size);
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.runAsync(() async {
        for (final entry in {
          'Manrope': 'assets/fonts/Manrope.ttf',
          'MaterialIcons': 'fonts/MaterialIcons-Regular.otf',
        }.entries) {
          final loader = FontLoader(entry.key)..addFont(rootBundle.load(entry.value));
          await loader.load();
        }
      });
      final container = ProviderContainer(
        overrides: [
          connectionHealthProvider.overrideWith(_Health.new),
          statsNotifierProvider.overrideWith(_Stats.new),
          smartSelectionProvider.overrideWith(_Smart.new),
          translationsProvider.overrideWith((ref) async => TranslationsEn()),
          activeProfileProvider.overrideWith(_Profile.new),
          hasAnyProfileProvider.overrideWith((ref) => Stream.value(true)),
          connectionNotifierProvider.overrideWith(_Connection.new),
          activeProxyNotifierProvider.overrideWith(_Proxy.new),
          configOptionNotifierProvider.overrideWith(_Options.new),
          appInfoProvider.overrideWith(_Info.new),
        ],
      );
      addTearDown(container.dispose);
      container.read(translationsProvider);
      container.read(activeProfileProvider);
      container.read(hasAnyProfileProvider);
      container.read(connectionNotifierProvider);
      container.read(activeProxyNotifierProvider);
      container.read(configOptionNotifierProvider);
      container.read(appInfoProvider);
      await tester.pump();
      await tester.pump();
      final appTheme = AppTheme(scenario.dark ? AppThemeMode.dark : AppThemeMode.light, 'Manrope');
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            theme: scenario.dark ? appTheme.darkTheme(null) : appTheme.lightTheme(null),
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scenario.scale)),
              child: Directionality(
                textDirection: scenario.rtl ? TextDirection.rtl : TextDirection.ltr,
                child: VisualEffects(
                  blur: false,
                  motion: false,
                  child: MaterialUiCompatibilityBridge(
                    child: RepaintBoundary(
                      key: const ValueKey("home_preview"),
                      child: CyberBackground(child: child!),
                    ),
                  ),
                ),
              ),
            ),
            home: const HomePage(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('home_connection_button')), findsOneWidget);
      expect(tester.takeException(), isNull);
      final name = scenario.rtl
          ? "rtl"
          : scenario.scale > 1
          ? "large-text"
          : "dark";
      await expectLater(find.byKey(const ValueKey("home_preview")), matchesGoldenFile("goldens/home-$name.png"));
      await tester.pumpWidget(const SizedBox());
      container.dispose();
      await tester.pump();
    });
  }
}
