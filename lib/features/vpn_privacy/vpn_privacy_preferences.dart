import 'package:hiddify/core/utils/preferences_utils.dart';

/// Local device policy. Subscription overrides must never weaken these settings.
abstract class VpnPrivacyPreferences {
  static final fullTunnel = PreferencesNotifier.create<bool, bool>('privacy-full-tunnel', false);
  static final hideLocalProxy = PreferencesNotifier.create<bool, bool>('privacy-hide-local-proxy', false);
  static final hideClashApi = PreferencesNotifier.create<bool, bool>('privacy-hide-clash-api', false);
  static final disableSystemProxy = PreferencesNotifier.create<bool, bool>('privacy-disable-system-proxy', true);
  static final publicDns = PreferencesNotifier.create<bool, bool>('privacy-public-dns', false);
  static final encryptedDns = PreferencesNotifier.create<bool, bool>('privacy-encrypted-dns', false);
}
