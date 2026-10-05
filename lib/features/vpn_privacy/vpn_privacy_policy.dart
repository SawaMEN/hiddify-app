/// Applies installation policy after profile overrides, without mutating the profile.
Map<String, dynamic> applyVpnPrivacyPolicy(
  Map<String, dynamic> profile, {
  required bool android,
  required bool fullTunnel,
  required bool hideLocalProxy,
  required bool hideClashApi,
  required bool disableSystemProxy,
  required bool encryptedDns,
}) {
  final json = Map<String, dynamic>.of(profile);
  final tun = json['enable-tun'] == true;
  final full = android && tun && fullTunnel;
  json['privacy-full-tunnel'] = full;
  json['privacy-hide-local-proxy'] = tun && hideLocalProxy;
  if (hideClashApi) json['enable-clash-api'] = false;
  if (disableSystemProxy && tun) json['set-system-proxy'] = false;
  if (full) {
    json['strict-route'] = true;
    json['bypass-lan'] = false;
  }
  if (encryptedDns || (full && json['remote-dns-address'] == 'local')) {
    json['remote-dns-address'] = 'https://1.1.1.1/dns-query';
  }
  return json;
}
