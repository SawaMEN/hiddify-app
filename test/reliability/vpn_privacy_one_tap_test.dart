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

  test('one-tap setup explicitly enables and reapplies the Russia regional policy', () {
    expect(actions, contains('ConfigOptions.region.notifier).update(Region.ru)'));
    expect(actions, contains("_setRoutingMode(ref, 'ru-bypass', forceRevision: true)"));
    expect(actions, contains('applyImmediately: true'));

    final regionIndex = actions.indexOf('ConfigOptions.region.notifier).update(Region.ru)');
    final routingIndex = actions.indexOf("_setRoutingMode(ref, 'ru-bypass', forceRevision: true)");
    expect(regionIndex, greaterThanOrEqualTo(0));
    expect(routingIndex, greaterThan(regionIndex));
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

  test('ordinary VPN privacy screen hides raw automatic policy details', () {
    expect(overview, isNot(contains('Текущая автоматическая политика')));
    expect(overview, isNot(contains('privacy-direct-packages')));
    expect(overview, contains('Для опытных пользователей'));
    expect(overview, contains('Вернуть всё как было'));
  });

  test('router opens the simple privacy overview by default', () {
    expect(router, contains('vpn_privacy_overview_page.dart'));
    expect(router, contains('child: const VpnPrivacyOverviewPage()'));
  });
}
