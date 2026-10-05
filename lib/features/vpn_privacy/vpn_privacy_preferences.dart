import 'package:hiddify/core/utils/preferences_utils.dart';

/// Local device policy. Subscription overrides must never weaken these settings.
abstract class VpnPrivacyPreferences {
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
}
