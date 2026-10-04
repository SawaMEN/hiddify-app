import 'dart:convert';

import 'package:hiddify/utils/validators.dart';

typedef ProfileLink = ({String url, String name});

abstract class LinkParser {
  static String generateSubShareLink(String url, [String? name]) {
    final uri = Uri.tryParse(url.trim());
    if (uri == null) return '';

    // Uri.replace preserves authority details such as non-standard ports,
    // credentials and IPv6 formatting while only changing the profile name.
    return uri.replace(fragment: name ?? uri.fragment).toString();
  }

  // Deep-link wrappers accepted by Hiddify for subscription imports.
  static const Set<String> protocols = {
    'hiddify',
    'v2ray',
    'v2rayn',
    'v2rayng',
    'clash',
    'clashmeta',
    'sing-box',
  };

  static ProfileLink? parse(String link) {
    final normalized = link.trim();
    if (normalized.isEmpty) return null;
    return simple(normalized) ?? deep(normalized);
  }

  static ProfileLink? simple(String link) {
    final normalized = link.trim();
    if (!isUrl(normalized)) return null;
    final uri = Uri.parse(normalized);
    return (url: uri.toString(), name: uri.queryParameters['name'] ?? '');
  }

  static ProfileLink? deep(String link) {
    final uri = Uri.tryParse(link.trim());
    if (uri == null || !uri.hasScheme || !uri.hasAuthority) return null;

    final scheme = uri.scheme.toLowerCase();
    if (!protocols.contains(scheme)) return null;

    final queryParams = uri.queryParameters;
    if (scheme == 'hiddify') {
      if (queryParams.containsKey('url')) {
        return (url: queryParams['url']!, name: queryParams['name'] ?? '');
      }
      if (uri.path.length <= 1) return null;
      return (url: uri.path.substring(1) + (uri.hasQuery ? '?${uri.query}' : ''), name: uri.fragment);
    }

    return queryParams.containsKey('url') ? (url: queryParams['url']!, name: queryParams['name'] ?? '') : null;
  }
}

String safeDecodeBase64(String str) {
  try {
    return utf8.decode(base64Decode(str));
  } catch (e) {
    return str;
  }
}
