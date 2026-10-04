import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/preferences/preferences_migration.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('Negative or mistyped schema versions do not block startup', () async {
    for (final version in [-1, 'invalid']) {
      SharedPreferences.setMockInitialValues({PreferencesMigration.versionKey: version, 'service-mode': 'tun'});
      final preferences = await SharedPreferences.getInstance();
      await PreferencesMigration(sharedPreferences: preferences).migrate();
      expect(preferences.getInt(PreferencesMigration.versionKey), 1);
      expect(preferences.getString('service-mode'), 'vpn');
    }
  });

  test('An unknown newer schema version is preserved', () async {
    SharedPreferences.setMockInitialValues({PreferencesMigration.versionKey: 99, 'service-mode': 'tun'});
    final preferences = await SharedPreferences.getInstance();
    await PreferencesMigration(sharedPreferences: preferences).migrate();
    expect(preferences.getInt(PreferencesMigration.versionKey), 99);
    expect(preferences.getString('service-mode'), 'tun');
  });

  test('Migration preserves existing IPv6 preference and DNS strategies', () async {
    SharedPreferences.setMockInitialValues({
      'ipv6-mode': 'prefer_ipv6',
      'remote-domain-dns-strategy': 'prefer_ipv6',
      'direct-domain-dns-strategy': 'prefer_ipv6',
    });
    final preferences = await SharedPreferences.getInstance();
    await PreferencesMigration(sharedPreferences: preferences).migrate();
    expect(preferences.getString('ipv6-mode'), 'prefer_ipv6');
    expect(preferences.getString('remote-dns-domain-strategy'), 'prefer_ipv6');
    expect(preferences.getString('direct-dns-domain-strategy'), 'prefer_ipv6');
  });

  test('Mistyped optional legacy values do not erase unrelated preferences', () async {
    SharedPreferences.setMockInitialValues({'service-mode': 42, 'ipv6-mode': false, 'saved-value': 'keep'});
    final preferences = await SharedPreferences.getInstance();
    await PreferencesMigration(sharedPreferences: preferences).migrate();
    expect(preferences.getString('saved-value'), 'keep');
    expect(preferences.containsKey('service-mode'), isFalse);
    expect(preferences.containsKey('ipv6-mode'), isFalse);
    expect(preferences.getInt(PreferencesMigration.versionKey), 1);
  });
}
