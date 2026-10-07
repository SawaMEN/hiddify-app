import 'dart:async';

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
