import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/singbox/model/singbox_proxy_type.dart';
import 'package:hiddify/utils/link_parsers.dart';

void main() {
  group('ProxyType', () {
    test('recognizes modern sing-box and Hiddify protocol types', () {
      expect(ProxyType.fromJson('anytls'), ProxyType.anytls);
      expect(ProxyType.fromJson('snell'), ProxyType.snell);
      expect(ProxyType.fromJson('openconnect'), ProxyType.openconnect);
      expect(ProxyType.fromJson('openvpn-client'), ProxyType.openvpnClient);
      expect(ProxyType.fromJson('openvpn-server'), ProxyType.openvpnServer);
      expect(ProxyType.fromJson('tailscale'), ProxyType.tailscale);
      expect(ProxyType.fromJson('cloudflared'), ProxyType.cloudflared);
    });

    test('recognizes sing-box 1.15 MASQUE types', () {
      // `masque` is the optional outbound, while the 1.15 CONNECT-IP
      // implementation is exposed as client/server endpoints.
      expect(ProxyType.fromJson('masque'), ProxyType.masque);
      expect(ProxyType.fromJson('masque-client'), ProxyType.masqueClient);
      expect(ProxyType.fromJson('masque-server'), ProxyType.masqueServer);
    });

    test('keeps compatibility aliases', () {
      expect(ProxyType.fromJson('ssr'), ProxyType.shadowsocksr);
      expect(ProxyType.fromJson('wireguard_legacy'), ProxyType.wireguard);
      expect(ProxyType.fromJson('openvpn'), ProxyType.openvpnClient);
      expect(ProxyType.fromJson('hinvalid'), ProxyType.invalid);
      expect(ProxyType.fromJson('something-new'), ProxyType.unknown);
    });
  });

  group('LinkParser', () {
    test('preserves non-standard subscription ports', () {
      final result = LinkParser.generateSubShareLink(
        'https://example.com:2096/sub/path?token=abc#old',
        'new',
      );

      expect(result, 'https://example.com:2096/sub/path?token=abc#new');
    });

    test('keeps existing fragment when no replacement name is supplied', () {
      expect(
        LinkParser.generateSubShareLink('https://example.com:8443/sub#profile'),
        'https://example.com:8443/sub#profile',
      );
    });
  });
}
