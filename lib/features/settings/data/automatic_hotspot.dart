import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/features/connection/model/connection_status.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/settings/notifier/config_option/config_option_notifier.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/singbox/model/singbox_config_enum.dart';

import 'dart:async';
import 'dart:math';

import 'package:flutter/services.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

final automaticHotspotProvider = NotifierProvider<AutomaticHotspot, Map<String, dynamic>>(AutomaticHotspot.new);

/// Android owns the AP reservation. Preferences alone never mean the AP is running.
class AutomaticHotspot extends Notifier<Map<String, dynamic>> {
  static const _methods = MethodChannel('vetroff/hotspot');
  static const _events = EventChannel('vetroff/hotspot/events');

  @override
  Map<String, dynamic> build() {
    if (PlatformUtils.isAndroid) {
      final subscription = _events.receiveBroadcastStream().listen(
        (event) => state = Map<String, dynamic>.from(event as Map),
        onError: (Object error) => state = {'active': false, 'error': '$error'},
      );
      ref.onDispose(() => unawaited(subscription.cancel()));
    }
    return {'active': false};
  }

  Future<void> start({required bool root}) async {
    final result = await _methods.invokeMapMethod<String, dynamic>('start', {'root': root});
    state = result ?? {'active': false};
  }

  Future<void> activate() async {
    final result = await _methods.invokeMapMethod<String, dynamic>('activate');
    state = result ?? {'active': false};
  }

  Future<void> stop() async {
    await _methods.invokeMethod<void>('stop');
    state = {'active': false};
  }

  static String wifiQr(String ssid, String password) {
    String escape(String value) => value.replaceAllMapped(RegExp(r'[\\;,:"]'), (match) => '\\${match[0]}');
    return 'WIFI:T:WPA;S:${escape(ssid)};P:${escape(password)};;';
  }
}

final hotspotSharingControllerProvider = NotifierProvider<HotspotSharingController, bool>(HotspotSharingController.new);

/// Keep the operation alive if the user leaves Settings during an Android permission dialog.
class HotspotSharingController extends Notifier<bool> {
  @override
  bool build() {
    ref.listen(automaticHotspotProvider, (previous, next) {
      if (PlatformUtils.isAndroid &&
          !state &&
          previous?['active'] == true &&
          next['active'] != true &&
          ref.read(VpnPrivacyPreferences.wifiSharing)) {
        unawaited(update(false).catchError((Object error) {}));
      }
    });
    return false;
  }

  Future<void> update(bool enabled) async {
    if (state) return;
    state = true;
    try {
      if (PlatformUtils.isAndroid) {
        if (enabled) {
          if (await ref.read(activeProfileProvider.future) == null) {
            throw StateError('Сначала выберите VPN-профиль / Select a VPN profile first');
          }
          await ref.read(automaticHotspotProvider.notifier).start(root: ref.read(VpnPrivacyPreferences.useRoot));
        } else {
          await ref.read(automaticHotspotProvider.notifier).stop();
        }
      }
      await ref.read(configOptionNotifierProvider.notifier).updateTogether(() async {
        if (enabled && PlatformUtils.isAndroid) {
          await ref.read(ConfigOptions.serviceMode.notifier).update(ServiceMode.tun);
        }
        if (enabled && ref.read(ConfigOptions.lanSharingPassword).isEmpty) {
          final random = Random.secure();
          final password = List.generate(24, (_) => random.nextInt(16).toRadixString(16)).join();
          await ref.read(ConfigOptions.lanSharingPassword.notifier).update(password);
        }
        await ref.read(VpnPrivacyPreferences.wifiSharing.notifier).update(enabled);
      }, applyImmediately: true);
      if (PlatformUtils.isAndroid && enabled) {
        final connection = await ref.read(connectionNotifierProvider.future);
        if (connection is Disconnected) await ref.read(connectionNotifierProvider.notifier).toggleConnection();
        await ref.read(automaticHotspotProvider.notifier).activate();
      }
    } catch (error) {
      if (PlatformUtils.isAndroid) {
        // A failed VPN startup must never leave an apparently working AP.
        try {
          await ref.read(automaticHotspotProvider.notifier).stop();
        } catch (_) {}
        try {
          await ref
              .read(configOptionNotifierProvider.notifier)
              .updateTogether(
                () => ref.read(VpnPrivacyPreferences.wifiSharing.notifier).update(false),
                applyImmediately: true,
              );
        } catch (_) {}
      }
      rethrow;
    } finally {
      state = false;
    }
  }
}
