import 'dart:convert';

class ServerEndpoint {
  const ServerEndpoint(this.tag, this.type, this.host, this.port);
  final String tag, type, host;
  final int port;
  bool get udp => const {
    'tuic',
    'hysteria',
    'hysteria2',
    'hy2',
    'wireguard',
    'wg',
    'awg',
    'masque',
    'masque-client',
  }.contains(type);
}

class ImportSummary {
  ImportSummary(this.servers, this.duplicates, this.unknownTypes, this.overrides);
  final List<ServerEndpoint> servers;
  final int duplicates, unknownTypes;
  final List<String> overrides;
  static const known = {
    'ss',
    'shadowsocks',
    'ssr',
    'vmess',
    'vless',
    'trojan',
    'tuic',
    'hy',
    'hysteria',
    'hy2',
    'hysteria2',
    'wg',
    'wireguard',
    'wireguard_legacy',
    'awg',
    'ssh',
    'socks',
    'http',
    'shadowtls',
    'anytls',
    'snell',
    'mieru',
    'warp',
    'psiphon',
    'openvpn',
    'openvpn-client',
    'openvpn-server',
    'openconnect',
    'tailscale',
    'tailcat',
    'cloudflared',
    'masque',
    'masque-client',
    'masque-server',
    'trusttunnel',
    'hiddify',
    'tunnel_client',
    'tunnel_server',
    'hysteria2_legacy',
  };
  static ImportSummary parse(String raw, [Map<String, dynamic> headers = const {}]) {
    var content = raw.trim();
    if (!content.contains('://') && !content.startsWith('{') && !content.contains(':')) {
      try {
        content = utf8.decode(base64.decode(base64.normalize(content)));
      } catch (_) {}
    }
    final servers = <ServerEndpoint>[];
    final seen = <String>{};
    var duplicates = 0, unknown = 0;
    final overrides = <String>{
      ...headers.keys.where(
        (k) =>
            k.contains('dns') ||
            k.contains('chain') ||
            k.contains('security') ||
            k.contains('tls') ||
            k.startsWith('enable-'),
      ),
    };
    void add(String tag, String type, String host, int port, String fingerprint) {
      if (const {'direct', 'block', 'dns', 'selector', 'urltest', 'url-test', 'reject'}.contains(type)) return;
      if (!seen.add(fingerprint)) duplicates++;
      if (!known.contains(type)) unknown++;
      servers.add(ServerEndpoint(tag, type, host, port));
    }

    try {
      final data = jsonDecode(content);
      if (data is Map) {
        for (final key in ['dns', 'route', 'routing']) {
          if (data.containsKey(key)) overrides.add(key);
        }
        for (final key in ['outbounds', 'endpoints']) {
          if (data[key] is! List) continue;
          for (final item in data[key] as List) {
            if (item is! Map) continue;
            final type = (item['type'] ?? item['protocol'] ?? 'unknown').toString();
            var host = (item['server'] ?? item['address'] ?? '').toString();
            var port = int.tryParse((item['server_port'] ?? item['port'] ?? '0').toString()) ?? 0;
            final settings = item['settings'];
            if (settings is Map) {
              final next = settings['vnext'] ?? settings['servers'];
              if (next is List && next.isNotEmpty && next.first is Map) {
                host = (next.first['address'] ?? host).toString();
                port = int.tryParse((next.first['port'] ?? port).toString()) ?? 0;
              }
            }
            final fingerprint = Map<String, dynamic>.from(item)
              ..remove('tag')
              ..remove('name');
            add((item['tag'] ?? item['name'] ?? type).toString(), type, host, port, jsonEncode(fingerprint));
          }
        }
        return ImportSummary(servers, duplicates, unknown, overrides.toList());
      }
    } catch (_) {}
    for (final line in content.split('\n')) {
      final uri = Uri.tryParse(line.trim());
      if (uri == null || !line.contains('://') || const {'http', 'https', 'hiddify'}.contains(uri.scheme)) continue;
      var host = '', port = 0, tag = uri.fragment, type = uri.scheme;
      try {
        host = uri.host;
        port = uri.port;
      } catch (_) {}
      if (type == 'vmess') {
        try {
          final data = jsonDecode(
            utf8.decode(base64.decode(base64.normalize(line.trim().substring(8).split('#').first))),
          );
          host = (data['add'] ?? '').toString();
          port = int.tryParse(data['port'].toString()) ?? 0;
          tag = (data['ps'] ?? tag).toString();
        } catch (_) {}
      }
      add(tag.isEmpty ? type : tag, type, host, port, uri.replace(fragment: '').toString());
    }
    if (servers.isEmpty) {
      for (final match in RegExp(r'^\s*(?:-\s*)?type:\s*([a-zA-Z0-9_-]+)', multiLine: true).allMatches(content)) {
        final type = match.group(1)!;
        add(type, type, '', 0, 'yaml-${match.start}');
      }
      for (final key in ['dns', 'rules', 'rule-providers']) {
        if (RegExp('^$key:', multiLine: true).hasMatch(content)) overrides.add(key);
      }
    }
    return ImportSummary(servers, duplicates, unknown, overrides.toList());
  }
}

typedef ImportPreview = Future<bool> Function(ImportSummary summary, bool insecureHttp);
