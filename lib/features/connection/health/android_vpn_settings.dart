import 'dart:io';

import 'package:flutter/services.dart';

abstract class AndroidVpnSettings {
  static const _channel = MethodChannel('com.hiddify.app/method');
  static Future<Map<dynamic, dynamic>> protection() async =>
      Platform.isAndroid ? (await _channel.invokeMapMethod<dynamic, dynamic>('get_vpn_protection') ?? {}) : {};
  static Future<void> open() async {
    if (Platform.isAndroid) await _channel.invokeMethod<void>('open_vpn_settings');
  }

  static Future<bool> wantsConnection() async =>
      !Platform.isAndroid || (await _channel.invokeMethod<bool>('get_connection_intent') ?? false);
  static Future<bool> serviceRunning() async =>
      Platform.isAndroid && (await _channel.invokeMethod<bool>('get_service_running') ?? false);
  static Future<bool?> networkAvailable() async =>
      Platform.isAndroid ? _channel.invokeMethod<bool>('get_network_status') : null;
}
