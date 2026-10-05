import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  final catalogue = jsonDecode(
    File('android/app/src/main/assets/region_routing.json').readAsStringSync(),
  ) as Map<String, dynamic>;

  List<String> entries(String key) => (catalogue[key] as List<dynamic>).cast<String>();

  final token = RegExp(r'^[a-z0-9_-]+(?:\.[a-z0-9_-]+)+$');

  test('Russia VPN-aware catalogue contains the researched application set', () {
    expect(catalogue['version'], 2);
    final vpnAware = entries('ruVpnAwarePackages');
    expect(vpnAware, hasLength(22));
    expect(vpnAware.toSet(), hasLength(vpnAware.length));
    expect(
      vpnAware,
      containsAll(const [
        'com.yandex.browser',
        'com.idamob.tinkoff.android',
        'ru.sberbankmobile',
        'ru.vtb24.mobilebanking.android',
        'ru.sbcs.store',
        'ru.oneme.app',
        'ru.rutube.app',
      ]),
    );
  });

  test('VPN-aware and compatibility-only Russian package sets stay separate', () {
    final vpnAware = entries('ruVpnAwarePackages').toSet();
    final compatibility = entries('ruCompatibilityPackages').toSet();
    expect(vpnAware.intersection(compatibility), isEmpty);
    expect(
      compatibility,
      containsAll(const {
        'ru.rostel',
        'ru.nspk.mirpay',
        'ru.yandex.taxi',
      }),
    );
  });

  test('restricted-service catalogue covers major affected applications', () {
    final packages = entries('proxyPackages').toSet();
    final domains = entries('proxyDomains').toSet();

    expect(
      packages,
      containsAll(const {
        'com.discord',
        'com.facebook.katana',
        'com.google.android.youtube',
        'com.instagram.android',
        'com.roblox.client',
        'com.snapchat.android',
        'com.viber.voip',
        'com.whatsapp',
        'org.telegram.messenger',
        'org.thoughtcrime.securesms',
      }),
    );
    expect(
      domains,
      containsAll(const {
        'discord.com',
        'facebook.com',
        'instagram.com',
        'roblox.com',
        'signal.org',
        'telegram.org',
        'viber.com',
        'whatsapp.com',
        'youtube.com',
      }),
    );
  });

  test('all routing catalogue entries are unique normalized tokens', () {
    const keys = [
      'ruVpnAwarePackages',
      'ruCompatibilityPackages',
      'ruVpnAwareDomains',
      'ruCompatibilityDomains',
      'proxyPackages',
      'proxyDomains',
    ];

    for (final key in keys) {
      final values = entries(key);
      expect(values.toSet(), hasLength(values.length), reason: '$key contains duplicates');
      expect(values.every(token.hasMatch), isTrue, reason: '$key contains an invalid token');
      expect(values.every((value) => value == value.toLowerCase()), isTrue, reason: '$key is not normalized');
    }
  });

  test('bundled direct and proxy application lists do not conflict', () {
    final direct = {
      ...entries('ruVpnAwarePackages'),
      ...entries('ruCompatibilityPackages'),
    };
    final proxy = entries('proxyPackages').toSet();
    expect(direct.intersection(proxy), isEmpty);
  });

  test('root mode hides the Android VPN service enumeration signal and restores it', () {
    final visibility = File(
      'android/app/src/main/kotlin/com/hiddify/hiddify/privacy/VpnServiceVisibility.kt',
    ).readAsStringSync();
    final settings = File(
      'android/app/src/main/kotlin/com/hiddify/hiddify/Settings.kt',
    ).readAsStringSync();
    final application = File(
      'android/app/src/main/kotlin/com/hiddify/hiddify/Application.kt',
    ).readAsStringSync();
    final tile = File(
      'android/app/src/main/kotlin/com/hiddify/hiddify/bg/TileService.kt',
    ).readAsStringSync();
    final shortcut = File(
      'android/app/src/main/kotlin/com/hiddify/hiddify/ShortcutActivity.kt',
    ).readAsStringSync();

    expect(visibility, contains('COMPONENT_ENABLED_STATE_DISABLED'));
    expect(visibility, contains('COMPONENT_ENABLED_STATE_DEFAULT'));
    expect(visibility, contains('setComponentEnabledSetting'));
    expect(
      settings,
      contains('VpnServiceVisibility.sync(Application.application, rootMode)'),
    );
    expect(
      application,
      contains('VpnServiceVisibility.sync(this, Settings.privacyUseRoot)'),
    );
    expect(
      tile,
      contains('!Settings.privacyUseRoot && VpnService.prepare(this) != null'),
    );
    expect(
      shortcut,
      contains('!Settings.privacyUseRoot && VpnService.prepare(this) != null'),
    );
  });
}
