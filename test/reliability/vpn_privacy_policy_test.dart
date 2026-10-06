import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_policy.dart';

Map<String, dynamic> protected(Map<String, dynamic> profile, {bool android = true}) => applyVpnPrivacyPolicy(
  profile,
  android: android,
  fullTunnel: true,
  hideLocalProxy: true,
  hideClashApi: true,
  disableSystemProxy: true,
  encryptedDns: false,
);

void main() {
  test('device protocol and network switches override imported values without changing the profile', () {
    final profile = <String, dynamic>{'privacy-modern-protocols-only': false, 'adaptive-network': false};
    final on = applyVpnPrivacyPolicy(
      profile,
      android: true,
      fullTunnel: false,
      hideLocalProxy: false,
      hideClashApi: false,
      disableSystemProxy: false,
      encryptedDns: false,
      modernProtocolsOnly: true,
      modernAllowUDP: true,
      adaptiveNetwork: true,
    );
    expect(on['privacy-modern-protocols-only'], true);
    expect(on['adaptive-network'], true);
    expect(on['privacy-modern-allow-udp'], true);
    expect(profile['privacy-modern-protocols-only'], false);
    final off = applyVpnPrivacyPolicy(
      on,
      android: true,
      fullTunnel: false,
      hideLocalProxy: false,
      hideClashApi: false,
      disableSystemProxy: false,
      encryptedDns: false,
    );
    expect(off['privacy-modern-protocols-only'], false);
    expect(off['adaptive-network'], false);
    expect(off['privacy-modern-allow-udp'], false);
  });
  test('installation policy overrides a permissive profile without modifying it', () {
    final profile = <String, dynamic>{
      'enable-tun': true,
      'enable-clash-api': true,
      'set-system-proxy': true,
      'bypass-lan': true,
      'strict-route': false,
      'remote-dns-address': 'local',
    };
    final result = protected(profile);
    expect(result['privacy-full-tunnel'], true);
    expect(result['privacy-hide-local-proxy'], true);
    expect(result['enable-clash-api'], false);
    expect(result['set-system-proxy'], false);
    expect(result['bypass-lan'], false);
    expect(result['strict-route'], true);
    expect(result['remote-dns-address'], 'https://1.1.1.1/dns-query');
    expect(profile['enable-clash-api'], true);
    expect(profile['remote-dns-address'], 'local');
  });

  test('proxy-only mode retains its required listener and system proxy', () {
    final result = protected({'enable-tun': false, 'set-system-proxy': true});
    expect(result['privacy-full-tunnel'], false);
    expect(result['privacy-hide-local-proxy'], false);
    expect(result['set-system-proxy'], true);
  });

  test('Android full tunnel does not claim support on another platform', () {
    expect(protected({'enable-tun': true}, android: false)['privacy-full-tunnel'], false);
  });
}
