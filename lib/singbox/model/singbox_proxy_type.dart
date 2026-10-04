enum ProxyType {
  tun("TUN"),
  redirect("Redirect"),
  tproxy("TProxy"),
  direct("Direct"),
  bridge("Bridge"),
  block("Block"),
  dns("DNS"),
  socks("SOCKS"),
  http("HTTP"),
  mixed("Mixed"),
  shadowsocks("Shadowsocks"),
  snell("Snell"),
  vmess("VMess"),
  trojan("Trojan"),
  naive("Naive"),
  wireguard("WireGuard"),
  awg("AWG"),
  hysteria("Hysteria"),
  tor("Tor"),
  ssh("SSH"),
  shadowtls("ShadowTLS"),
  anytls("AnyTLS"),
  shadowsocksr("ShadowsocksR"),
  vless("VLESS"),
  tuic("TUIC"),
  hysteria2("Hysteria2"),
  openconnect("OpenConnect"),
  openvpnClient("OpenVPN Client", "openvpn-client"),
  openvpnServer("OpenVPN Server", "openvpn-server"),
  tailscale("Tailscale"),
  cloudflared("Cloudflared"),
  masque("MASQUE"),
  masqueClient("MASQUE Client", "masque-client"),
  masqueServer("MASQUE Server", "masque-server"),
  psiphon("Psiphon"),
  trusttunnel("TrustTunnel"),
  tunnelClient("Tunnel Client", "tunnel_client"),
  tunnelServer("Tunnel Server", "tunnel_server"),
  dnstt("DNSTT"),
  gooserelay("GooseRelay"),
  xray("xray"),
  custom("custom"),

  selector("Selector"),
  urltest("URLTest"),
  balancer("Balancer"),
  warp("Warp"),

  xvless("xVLESS"),
  xvmess("xVMess"),
  xtrojan("xTrojan"),
  xfreedom("xFragment"),
  xshadowsocks("xShadowsocks"),
  xsocks("xSocks"),
  invalid("Invalid", "hinvalid"),
  unknown("Unknown");

  const ProxyType(this.label, [this._key]);

  final String label;
  final String? _key;

  String get key => _key ?? name;

  static const Set<ProxyType> groupValues = {selector, urltest, balancer};

  bool get isGroup => groupValues.contains(this);

  static final Map<String, ProxyType> _keyMap = <String, ProxyType>{
    for (final value in ProxyType.values) value.key: value,
    // Compatibility aliases used by older Hiddify/sing-box configurations.
    'ssr': shadowsocksr,
    'wireguard_legacy': wireguard,
    'openvpn': openvpnClient,
    'invalid': invalid,
  };

  static ProxyType fromJson(dynamic type) {
    if (type is! String) return ProxyType.unknown;
    return _keyMap[type.trim().toLowerCase()] ?? ProxyType.unknown;
  }
}
