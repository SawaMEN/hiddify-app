import 'dart:convert';

import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/features/settings/notifier/config_option/config_option_notifier.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
import 'package:hiddify/singbox/model/singbox_config_enum.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

/// High-level actions used by the simple VPN privacy screen.
///
/// Automatic setup is deliberately transactional from the user's point of view:
/// it captures the previous values once, updates all related preferences together,
/// and waits for the active Android service to consume the new native policy.
abstract class VpnPrivacyActions {
  static Map<String, Object> _snapshot(WidgetRef ref) => {
    'serviceMode': ref.read(ConfigOptions.serviceMode).key,
    'ipv6Mode': ref.read(ConfigOptions.ipv6Mode).key,
    'mtu': ref.read(ConfigOptions.mtu),
    'autoReconnect': ref.read(Preferences.autoReconnect),
    'routingMode': ref.read(VpnPrivacyPreferences.routingMode),
    'russianNetworkBypass': ref.read(VpnPrivacyPreferences.russianNetworkBypass),
    'russianAppsBypass': ref.read(VpnPrivacyPreferences.russianAppsBypass),
    'restrictedServicesProxy': ref.read(VpnPrivacyPreferences.restrictedServicesProxy),
    'customDirectPackages': ref.read(VpnPrivacyPreferences.customDirectPackages),
    'customProxyPackages': ref.read(VpnPrivacyPreferences.customProxyPackages),
    'customDirectDomains': ref.read(VpnPrivacyPreferences.customDirectDomains),
    'customProxyDomains': ref.read(VpnPrivacyPreferences.customProxyDomains),
    'useRoot': ref.read(VpnPrivacyPreferences.useRoot),
    'fullTunnel': ref.read(VpnPrivacyPreferences.fullTunnel),
    'hideLocalProxy': ref.read(VpnPrivacyPreferences.hideLocalProxy),
    'hideClashApi': ref.read(VpnPrivacyPreferences.hideClashApi),
    'disableSystemProxy': ref.read(VpnPrivacyPreferences.disableSystemProxy),
    'publicDns': ref.read(VpnPrivacyPreferences.publicDns),
    'encryptedDns': ref.read(VpnPrivacyPreferences.encryptedDns),
  };

  static bool isConfigured(WidgetRef ref) =>
      ref.watch(ConfigOptions.serviceMode) == ServiceMode.tun &&
      ref.watch(VpnPrivacyPreferences.routingMode) == 'ru-bypass' &&
      ref.watch(VpnPrivacyPreferences.russianNetworkBypass) &&
      ref.watch(VpnPrivacyPreferences.russianAppsBypass) &&
      ref.watch(VpnPrivacyPreferences.restrictedServicesProxy) &&
      ref.watch(VpnPrivacyPreferences.hideLocalProxy) &&
      ref.watch(VpnPrivacyPreferences.hideClashApi) &&
      ref.watch(VpnPrivacyPreferences.disableSystemProxy) &&
      ref.watch(VpnPrivacyPreferences.publicDns) &&
      ref.watch(VpnPrivacyPreferences.encryptedDns) &&
      !ref.watch(VpnPrivacyPreferences.fullTunnel) &&
      ref.watch(ConfigOptions.ipv6Mode) == IPv6Mode.disable &&
      ref.watch(ConfigOptions.mtu) == 1400 &&
      ref.watch(Preferences.autoReconnect);

  static Future<void> _setRoutingMode(WidgetRef ref, String target, {required bool forceRevision}) async {
    final current = ref.read(VpnPrivacyPreferences.routingMode);
    if (forceRevision && current == target) {
      await ref
          .read(VpnPrivacyPreferences.routingMode.notifier)
          .update(target == 'off' ? 'ru-bypass' : 'off');
    }
    await ref.read(VpnPrivacyPreferences.routingMode.notifier).update(target);
  }

  static Future<void> configureAutomatically(WidgetRef ref) async {
    final backup = ref.read(VpnPrivacyPreferences.automaticSetupBackup);
    if (backup.isEmpty) {
      await ref.read(VpnPrivacyPreferences.automaticSetupBackup.notifier).update(jsonEncode(_snapshot(ref)));
    }

    await ref.read(configOptionNotifierProvider.notifier).updateTogether(
      () async {
        await ref.read(ConfigOptions.serviceMode.notifier).update(ServiceMode.tun);
        await ref.read(VpnPrivacyPreferences.russianNetworkBypass.notifier).update(true);
        await ref.read(VpnPrivacyPreferences.russianAppsBypass.notifier).update(true);
        await ref.read(VpnPrivacyPreferences.restrictedServicesProxy.notifier).update(true);
        await ref.read(VpnPrivacyPreferences.hideLocalProxy.notifier).update(true);
        await ref.read(VpnPrivacyPreferences.hideClashApi.notifier).update(true);
        await ref.read(VpnPrivacyPreferences.disableSystemProxy.notifier).update(true);
        await ref.read(VpnPrivacyPreferences.publicDns.notifier).update(true);
        await ref.read(VpnPrivacyPreferences.encryptedDns.notifier).update(true);
        await ref.read(VpnPrivacyPreferences.fullTunnel.notifier).update(false);
        await _setRoutingMode(ref, 'ru-bypass', forceRevision: true);
        await ref.read(ConfigOptions.ipv6Mode.notifier).update(IPv6Mode.disable);
        await ref.read(ConfigOptions.mtu.notifier).update(1400);
        await ref.read(Preferences.autoReconnect.notifier).update(true);
      },
      applyImmediately: true,
    );
  }

  static Future<void> restorePrevious(WidgetRef ref) async {
    final encoded = ref.read(VpnPrivacyPreferences.automaticSetupBackup);
    Map<String, dynamic>? snapshot;
    if (encoded.isNotEmpty) {
      try {
        final decoded = jsonDecode(encoded);
        if (decoded is Map<String, dynamic>) snapshot = decoded;
      } catch (_) {
        // Corrupted/legacy backup falls back to the feature's declared defaults below.
      }
    }

    T enumByKey<T>(Iterable<T> values, String? key, String Function(T) keyOf, T fallback) {
      for (final value in values) {
        if (keyOf(value) == key) return value;
      }
      return fallback;
    }

    final serviceMode = enumByKey(
      ServiceMode.values,
      snapshot?['serviceMode'] as String?,
      (value) => value.key,
      ServiceMode.defaultMode,
    );
    final ipv6Mode = enumByKey(
      IPv6Mode.values,
      snapshot?['ipv6Mode'] as String?,
      (value) => value.key,
      IPv6Mode.disable,
    );

    T value<T>(String key, T fallback) {
      final candidate = snapshot?[key];
      return candidate is T ? candidate : fallback;
    }

    final routingMode = value<String>('routingMode', 'ru-bypass');
    await ref.read(configOptionNotifierProvider.notifier).updateTogether(
      () async {
        await ref.read(ConfigOptions.serviceMode.notifier).update(serviceMode);
        await ref.read(ConfigOptions.ipv6Mode.notifier).update(ipv6Mode);
        await ref.read(ConfigOptions.mtu.notifier).update(value<int>('mtu', 9000));
        await ref.read(Preferences.autoReconnect.notifier).update(value<bool>('autoReconnect', true));
        await _setRoutingMode(ref, routingMode, forceRevision: true);
        await ref
            .read(VpnPrivacyPreferences.russianNetworkBypass.notifier)
            .update(value<bool>('russianNetworkBypass', true));
        await ref
            .read(VpnPrivacyPreferences.russianAppsBypass.notifier)
            .update(value<bool>('russianAppsBypass', true));
        await ref
            .read(VpnPrivacyPreferences.restrictedServicesProxy.notifier)
            .update(value<bool>('restrictedServicesProxy', true));
        await ref
            .read(VpnPrivacyPreferences.customDirectPackages.notifier)
            .update(value<String>('customDirectPackages', ''));
        await ref
            .read(VpnPrivacyPreferences.customProxyPackages.notifier)
            .update(value<String>('customProxyPackages', ''));
        await ref
            .read(VpnPrivacyPreferences.customDirectDomains.notifier)
            .update(value<String>('customDirectDomains', ''));
        await ref
            .read(VpnPrivacyPreferences.customProxyDomains.notifier)
            .update(value<String>('customProxyDomains', ''));
        await ref.read(VpnPrivacyPreferences.useRoot.notifier).update(value<bool>('useRoot', false));
        await ref.read(VpnPrivacyPreferences.fullTunnel.notifier).update(value<bool>('fullTunnel', false));
        await ref.read(VpnPrivacyPreferences.hideLocalProxy.notifier).update(value<bool>('hideLocalProxy', true));
        await ref.read(VpnPrivacyPreferences.hideClashApi.notifier).update(value<bool>('hideClashApi', true));
        await ref
            .read(VpnPrivacyPreferences.disableSystemProxy.notifier)
            .update(value<bool>('disableSystemProxy', true));
        await ref.read(VpnPrivacyPreferences.publicDns.notifier).update(value<bool>('publicDns', false));
        await ref.read(VpnPrivacyPreferences.encryptedDns.notifier).update(value<bool>('encryptedDns', true));
      },
      applyImmediately: true,
    );

    await ref.read(VpnPrivacyPreferences.automaticSetupBackup.notifier).update('');
  }
}
