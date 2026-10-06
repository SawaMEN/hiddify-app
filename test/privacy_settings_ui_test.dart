import 'dart:async';

import 'package:flutter/material.dart' as legacy;
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/app_info/app_info_provider.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/model/app_info_entity.dart';
import 'package:hiddify/core/model/environment.dart';
import 'package:hiddify/core/model/region.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/features/about/widget/about_page.dart';
import 'package:hiddify/features/intro/widget/intro_page.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/features/settings/widget/wifi_sharing_tile.dart';
import 'package:hiddify/features/settings/widget/wifi_sharing_instructions_page.dart';
import 'package:hiddify/features/settings/notifier/config_option/config_option_notifier.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_overview_page.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:material_ui/material_ui.dart' as ui;
import 'package:shared_preferences/shared_preferences.dart';

class _Options extends ConfigOptionNotifier {
  final pending = Completer<void>();
  bool called = false;
  @override
  Future<bool> build() async => false;
  @override
  Future<void> updateTogether(Future<void> Function() operation, {bool applyImmediately = false}) async {
    called = true;
    await operation();
    await pending.future;
  }
}

class _Info extends AppInfo {
  @override
  Future<AppInfoEntity> build() async => const AppInfoEntity(
    name: 'VetrOFF Client',
    version: '1.0.0',
    buildNumber: '1',
    release: Release.general,
    operatingSystem: 'android',
    operatingSystemVersion: 'test',
    environment: Environment.prod,
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<ProviderContainer> setup(WidgetTester tester, ui.Widget page, {bool russian = false, double scale = 1}) async {
    SharedPreferences.setMockInitialValues({'region': 'ru'});
    final preferences = (await tester.runAsync(SharedPreferences.getInstance))!;
    final container = ProviderContainer(
      overrides: [
        sharedPreferencesProvider.overrideWith((_) async => preferences),
        translationsProvider.overrideWith((_) => (russian ? AppLocale.ru : AppLocale.en).build()),
        configOptionNotifierProvider.overrideWith(_Options.new),
        appInfoProvider.overrideWith(_Info.new),
      ],
    );
    addTearDown(container.dispose);
    await tester.runAsync(() async {
      await container.read(sharedPreferencesProvider.future);
      await container.read(translationsProvider.future);
      await container.read(appInfoProvider.future);
      await container.read(configOptionNotifierProvider.future);
    });
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: ui.MaterialApp(
          locale: ui.Locale(russian ? 'ru' : 'en'),
          supportedLocales: const [ui.Locale('ru'), ui.Locale('en')],
          localizationsDelegates: ui.GlobalMaterialLocalizations.delegates,
          builder: (context, child) => ui.MaterialUiCompatibilityBridge(
            child: ui.MediaQuery(
              data: ui.MediaQuery.of(context).copyWith(textScaler: ui.TextScaler.linear(scale)),
              child: child!,
            ),
          ),
          home: page,
        ),
      ),
    );
    await tester.pumpAndSettle();
    return container;
  }

  testWidgets('External routing disables built-in controls and preserves selections', (tester) async {
    final container = await setup(tester, const VpnPrivacyOverviewPage());
    final category = find.byKey(const ui.ValueKey('privacy-routing'));
    await tester.scrollUntilVisible(category, 300);
    await tester.tap(find.text('Routing'));
    await tester.pumpAndSettle();
    final alternative = find.byKey(const ui.ValueKey('handbook-routing'));
    await tester.ensureVisible(alternative);
    await tester.tap(alternative);
    await tester.pump();
    final options = container.read(configOptionNotifierProvider.notifier) as _Options;
    expect(container.read(VpnPrivacyPreferences.handbookRouting), true);
    expect(container.read(VpnPrivacyPreferences.russianNetworkBypass), true);
    options.pending.complete();
    await tester.pumpAndSettle();
    final builtIn = find.ancestor(
      of: find.text('Russian domains and IPs direct'),
      matching: find.byType(legacy.SwitchListTile),
    );
    expect(tester.widget<legacy.SwitchListTile>(builtIn).onChanged, isNull);
    expect(tester.widget<legacy.SwitchListTile>(builtIn).value, false);
    expect(find.byKey(const ui.ValueKey('handbook-proxy')), findsOneWidget);
    expect(find.byKey(const ui.ValueKey('handbook-direct')), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const ui.SizedBox());
  });

  testWidgets('Sharing generates credentials and waits for options to apply', (tester) async {
    final container = await setup(tester, const ui.Scaffold(body: WifiSharingTile()));
    await tester.tap(find.byKey(const ui.ValueKey('wifi-vpn-sharing')));
    await tester.pump();
    expect(container.read(VpnPrivacyPreferences.wifiSharing), true);
    expect(container.read(ConfigOptions.lanSharingPassword).length, 24);
    final options = container.read(configOptionNotifierProvider.notifier) as _Options;
    expect(options.called, true);
    options.pending.complete();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const ui.SizedBox());
  });

  testWidgets('Sharing help has instructions on every OS tab', (tester) async {
    await setup(tester, const WifiSharingInstructionsPage());
    for (final tab in ['Android', 'Windows', 'Linux', 'macOS', 'iOS / iPadOS']) {
      await tester.ensureVisible(find.text(tab));
      await tester.tap(find.text(tab));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    }
    await tester.scrollUntilVisible(find.text('iOS → Wi-Fi → ⓘ → Configure Proxy'), 500, maxScrolls: 80);
    expect(find.text('Illustration • use your own details'), findsWidgets);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const ui.SizedBox());
  });

  testWidgets('Russian sharing diagrams fit a narrow screen with enlarged text', (tester) async {
    await tester.binding.setSurfaceSize(const ui.Size(360, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await setup(tester, const WifiSharingInstructionsPage(), russian: true, scale: 1.5);
    await tester.scrollUntilVisible(find.text('Android → Изменить сеть → Прокси'), 400, maxScrolls: 100);
    expect(find.text('Имя хоста прокси'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.scrollUntilVisible(find.text('Раздатчик с root → Клиент без прокси'), 400, maxScrolls: 100);
    expect(find.text('Нет / Выкл.'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const ui.SizedBox());
  });

  testWidgets('Expanded routing category survives preference changes and busy indicator insertion', (tester) async {
    final container = await setup(tester, const VpnPrivacyOverviewPage());
    final category = find.byKey(const ui.ValueKey('privacy-routing'));
    await tester.scrollUntilVisible(category, 300);
    await tester.tap(find.text('Routing'));
    await tester.pumpAndSettle();
    final setting = find.text('Russian domains and IPs direct');
    await tester.ensureVisible(setting);
    await tester.tap(setting);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));
    final options = container.read(configOptionNotifierProvider.notifier) as _Options;
    expect(options.called, isTrue);
    final tile = find.ancestor(of: setting, matching: find.byType(legacy.SwitchListTile));
    expect(tester.widget<legacy.SwitchListTile>(tile).onChanged, isNull);
    expect(setting, findsOneWidget);
    expect(container.read(VpnPrivacyPreferences.russianNetworkBypass), isFalse);
    options.pending.complete();
    await tester.pumpAndSettle();
    expect(setting, findsOneWidget);
    expect(find.textContaining('Old Russia-region'), findsNothing);
    await tester.pumpWidget(const ui.SizedBox());
  });

  testWidgets('Modern protocol switch explains its allow-list and applies device policy', (tester) async {
    final container = await setup(tester, const VpnPrivacyOverviewPage());
    final setting = find.byKey(const ui.ValueKey('modern-protocols-only'));
    await tester.scrollUntilVisible(setting, 250);
    expect(container.read(VpnPrivacyPreferences.modernProtocolsOnly), false);
    expect(find.textContaining('TCP: AnyTLS and Naive'), findsOneWidget);
    await tester.tap(find.text('Masked protocols only'));
    await tester.pump();
    final options = container.read(configOptionNotifierProvider.notifier) as _Options;
    expect(options.called, true);
    expect(container.read(VpnPrivacyPreferences.modernProtocolsOnly), true);
    options.pending.complete();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const ui.SizedBox());
  });

  testWidgets('UDP remains opt-in and persists independently of masked protocol policy', (tester) async {
    final container = await setup(tester, const VpnPrivacyOverviewPage());
    await container.read(VpnPrivacyPreferences.modernProtocolsOnly.notifier).update(true);
    await tester.pump();
    final udp = find.byKey(const ui.ValueKey('modern-allow-udp'));
    await tester.scrollUntilVisible(udp, 250);
    expect(container.read(VpnPrivacyPreferences.modernAllowUDP), false);
    await tester.tap(find.text('Allow UDP / QUIC'));
    await tester.pump();
    expect(container.read(VpnPrivacyPreferences.modernAllowUDP), true);
    final options = container.read(configOptionNotifierProvider.notifier) as _Options;
    expect(options.called, true);
    options.pending.complete();
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const ui.SizedBox());
  });

  testWidgets('First launch has no region selector and stored region cannot affect defaults', (tester) async {
    final container = await setup(tester, const IntroPage());
    expect(find.text('Region'), findsNothing);
    expect(container.read(ConfigOptions.region), Region.other);
    expect(ConfigOptions.preferences.containsKey('region'), isFalse);
    expect(container.read(ConfigOptions.directDnsAddress), 'udp://1.1.1.1');
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const ui.SizedBox());
  });

  testWidgets('About labels the fork and original project on a narrow screen with large text', (tester) async {
    await tester.binding.setSurfaceSize(const ui.Size(320, 640));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await setup(tester, const AboutPage(), russian: true, scale: 1.5);
    expect(find.text('Форк Hiddify'), findsOneWidget);
    expect(find.textContaining('независимый форк Hiddify'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Оригинальный Hiddify'), 200);
    expect(find.text('hiddify/hiddify-app'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const ui.SizedBox());
  });
}
