import 'dart:io';

import 'package:flutter/services.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

final androidVpnRuntimeProvider = Provider<AndroidVpnRuntime>((ref) => const AndroidVpnRuntime());

/// Native lifecycle queries used by the connection coordinator.
class AndroidVpnRuntime {
  const AndroidVpnRuntime();

  bool get isAndroid => Platform.isAndroid;
  Future<bool> wantsConnection() => AndroidVpnSettings.wantsConnection();
  Future<bool> serviceRunning() => AndroidVpnSettings.serviceRunning();
  Future<bool> stopService() => AndroidVpnSettings.stopService();
  Future<bool?> networkAvailable() => AndroidVpnSettings.networkAvailable();
}

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
  static Future<bool> stopService() async =>
      !Platform.isAndroid || (await _channel.invokeMethod<bool>('stop').timeout(const Duration(seconds: 24)) ?? false);
  static Future<bool?> networkAvailable() async =>
      Platform.isAndroid ? _channel.invokeMethod<bool>('get_network_status') : null;
}
