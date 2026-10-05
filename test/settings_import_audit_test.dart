import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/directories/directories_provider.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/features/settings/notifier/config_option/config_option_notifier.dart';
import 'package:hiddify/features/route_rules/notifier/rules_notifier.dart';
import 'package:hiddify/hiddifycore/generated/v2/config/route_rule.pb.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('Settings imports validate every value before any write and accept explicit null', () async {
    SharedPreferences.setMockInitialValues({
      'lan_sharing_password': 'original',
      'extra-security-profile-id': 'profile',
    });
    final preferences = await SharedPreferences.getInstance();
    final dir = await Directory.systemTemp.createTemp('hiddify-settings-');
    final container = ProviderContainer(
      overrides: [
        sharedPreferencesProvider.overrideWith((_) async => preferences),
        appDirectoriesProvider.overrideWithBuild((_, _) async => (baseDir: dir, workingDir: dir, tempDir: dir)),
        serviceRunningProvider.overrideWith((_) => false),
      ],
    );
    try {
      await container.read(sharedPreferencesProvider.future);
      await container.read(appDirectoriesProvider.future);
      await container.read(configOptionNotifierProvider.future);
      final notifier = container.read(configOptionNotifierProvider.notifier);
      for (final invalid in ['null', '42', '[]', '{}', '{"lan-sharing-password":"changed","mixed-port":0}']) {
        await expectLater(notifier.importJson(invalid), throwsA(anything));
        expect(container.read(ConfigOptions.lanSharingPassword), 'original');
      }
      await notifier.importJson('{"extra-security":{"profile":{"id":null}},"unblocker":{"warp":{"clean-port":0}}}');
      expect(container.read(ConfigOptions.extraSecurityProfileId), isNull);
      final rules = RouteRule(
        rules: [Rule(name: 'imported', outbound: Outbound.block, listOrder: 42)],
      );
      await notifier.importJson(jsonEncode({'route-rule': rules.toProto3Json()}));
      expect(container.read(rulesNotifierProvider).single.name, 'imported');
      expect(container.read(rulesNotifierProvider).single.listOrder, 0);
      final exported = container.read(ConfigOptions.singboxConfigOptions).toJson();
      await notifier.importJson(jsonEncode(exported));
      expect(container.read(ConfigOptions.singboxConfigOptions).toJson(), exported);
    } finally {
      container.dispose();
      await dir.delete(recursive: true);
    }
  });
}
