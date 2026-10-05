import 'package:hiddify/core/utils/preferences_utils.dart';

/// Local device policy. Subscription overrides must never weaken these settings.
abstract class VpnPrivacyPreferences {
  static const manualPackagePrefix = 'manual:';

  static final routingMode = PreferencesNotifier.create<String, String>('privacy-routing-mode', 'ru-bypass');
  static final customDirectPackages = PreferencesNotifier.create<String, String>('privacy-direct-packages', '');
  static final customProxyPackages = PreferencesNotifier.create<String, String>('privacy-proxy-packages', '');
  static final customDirectDomains = PreferencesNotifier.create<String, String>('privacy-direct-domains', '');
  static final customProxyDomains = PreferencesNotifier.create<String, String>('privacy-proxy-domains', '');
  static final copyStyle = PreferencesNotifier.create<String, String>('privacy-copy-style', 'meet');
  static final useRoot = PreferencesNotifier.create<bool, bool>('privacy-use-root', false);
  static final fullTunnel = PreferencesNotifier.create<bool, bool>('privacy-full-tunnel', false);
  static final hideLocalProxy = PreferencesNotifier.create<bool, bool>('privacy-hide-local-proxy', true);
  static final hideClashApi = PreferencesNotifier.create<bool, bool>('privacy-hide-clash-api', true);
  static final disableSystemProxy = PreferencesNotifier.create<bool, bool>('privacy-disable-system-proxy', true);
  static final publicDns = PreferencesNotifier.create<bool, bool>('privacy-public-dns', false);
  static final encryptedDns = PreferencesNotifier.create<bool, bool>('privacy-encrypted-dns', true);

  // Experimental network anonymization switches are intentionally off by default.
  // They can reduce network-level metadata but may break protocols and local services.
  static final anonymizationBlockQuic = PreferencesNotifier.create<bool, bool>(
    'privacy-anonymization-block-quic',
    false,
  );
  static final anonymizationBlockStun = PreferencesNotifier.create<bool, bool>(
    'privacy-anonymization-block-stun',
    false,
  );
  static final anonymizationBlockPlainHttp = PreferencesNotifier.create<bool, bool>(
    'privacy-anonymization-block-plain-http',
    false,
  );
  static final anonymizationIsolateLan = PreferencesNotifier.create<bool, bool>(
    'privacy-anonymization-isolate-lan',
    false,
  );

  static String encodeManualPackages(Iterable<String> packages) {
    final normalized = packages.map((value) => value.trim()).where((value) => value.isNotEmpty).toSet().toList()..sort();
    return '$manualPackagePrefix${normalized.join(',')}';
  }

  static bool hasManualPackages(String value) => value.startsWith(manualPackagePrefix);

  static Set<String> decodeManualPackages(String value) {
    if (!hasManualPackages(value)) return const <String>{};
    return value
        .substring(manualPackagePrefix.length)
        .split(RegExp(r'[\s,;]+'))
        .map((entry) => entry.trim())
        .where((entry) => entry.isNotEmpty)
        .toSet();
  }

  /// Snapshot captured before the first one-tap automatic setup. It lets the user
  /// restore the exact previous state instead of guessing which defaults they used.
  static final automaticSetupBackup = PreferencesNotifier.create<String, String>('privacy-auto-setup-backup', '');
}
