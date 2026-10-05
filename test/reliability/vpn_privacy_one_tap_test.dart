import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final actions = File('lib/features/vpn_privacy/vpn_privacy_actions.dart').readAsStringSync();
  final overview = File('lib/features/vpn_privacy/vpn_privacy_overview_page.dart').readAsStringSync();
  final notifier = File(
    'lib/features/settings/notifier/config_option/config_option_notifier.dart',
  ).readAsStringSync();
  final router = File(
    'lib/core/router/go_router/routing_config_notifier.dart',
  ).readAsStringSync();

  test('one-tap setup enables independent regional switches without changing the core region', () {
    for (final preference in ['russianNetworkBypass', 'russianAppsBypass', 'restrictedServicesProxy']) {
      expect(actions, contains('VpnPrivacyPreferences.$preference.notifier).update(true)'));
      expect(notifier, contains('VpnPrivacyPreferences.$preference,'));
    }
    expect(actions, isNot(contains('ConfigOptions.region.notifier)')));
    expect(actions, contains("_setRoutingMode(ref, 'ru-bypass', forceRevision: true)"));
    expect(actions, contains('applyImmediately: true'));
  });

  test('automatic setup keeps an exact restore point', () {
    expect(actions, contains('automaticSetupBackup'));
    expect(actions, contains('jsonEncode(_snapshot(ref))'));
    expect(actions, contains('restorePrevious'));
    expect(actions, contains("automaticSetupBackup.notifier).update('')"));
  });

  test('region changes schedule native policy application', () {
    final regionListener = RegExp(
      r'ref\.listen\(ConfigOptions\.region,[\s\S]*?_nativePolicyChanged = true;[\s\S]*?_desiredRevision\+\+;[\s\S]*?_scheduleUpdate\(\);',
    );
    expect(regionListener.hasMatch(notifier), isTrue);
    expect(notifier, contains('Future<void> applyPending()'));
  });

  test('ordinary VPN privacy screen hides raw automatic policy UI', () {
    // The overview may read internal policy keys to present a friendly status/count,
    // but it must not expose the old raw policy/debug controls to normal users.
    expect(overview, isNot(contains('Текущая автоматическая политика')));
    expect(overview, isNot(contains('Пакеты напрямую')));
    expect(overview, isNot(contains('Домены напрямую')));
    expect(overview, isNot(contains('Пакеты через VPN')));
    expect(overview, contains('Для опытных пользователей'));
    expect(overview, contains('Вернуть всё как было'));
  });

  test('router opens the simple privacy overview by default', () {
    expect(router, contains('vpn_privacy_overview_page.dart'));
    expect(router, contains('child: const VpnPrivacyOverviewPage()'));
  });
}
