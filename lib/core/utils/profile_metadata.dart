import 'dart:convert';

Map<String, dynamic>? decodeOptionalJsonObject(String? value) {
  if (value == null) return null;
  try {
    final decoded = jsonDecode(value);
    return decoded is Map<String, dynamic> ? decoded : null;
  } on FormatException {
    return null;
  }
}

/// Optional subscription metadata must not invalidate usable proxy content.
String? parseProfileTitle(String value) {
  var title = value.trim();
  if (title.startsWith('base64:')) {
    try {
      title = utf8.decode(base64.decode(base64.normalize(title.substring(7).trim()))).trim();
    } on FormatException {
      return null;
    }
  }
  return title.isEmpty ? null : title;
}

Duration? parseProfileUpdateInterval(String value) {
  final hours = int.tryParse(value.trim());
  // Keep the multiplication inside Duration within the signed 64-bit range.
  if (hours == null || hours <= 0 || hours > 2562047788) return null;
  return Duration(hours: hours);
}

/// Prefer RFC 5987 UTF-8 filename*, then quoted or token filename.
String? parseContentDispositionFilename(String value) {
  final extended = RegExp(r"(?:^|;)\s*filename\*\s*=\s*([^;]+)", caseSensitive: false).firstMatch(value);
  if (extended != null) {
    final token = extended.group(1)!.trim().replaceAll('"', '');
    final parts = token.split("'");
    if (parts.length >= 3 && parts.first.toLowerCase() == 'utf-8') {
      try {
        final name = Uri.decodeComponent(parts.skip(2).join("'"));
        if (name.trim().isNotEmpty) return name;
      } on FormatException {
        /* fall back to filename */
      }
    }
  }
  final match = RegExp(
    r'(?:^|;)\s*filename\s*=\s*(?:"((?:\\.|[^"\\])*)"|([^;]+))',
    caseSensitive: false,
  ).firstMatch(value);
  final name = match?.group(1)?.replaceAllMapped(RegExp(r'\\(.)'), (m) => m.group(1)!) ?? match?.group(2)?.trim();
  return name == null || name.trim().isEmpty ? null : name;
}

/// Drift stores profile timestamps in seconds. Each update must change the
/// persisted value so observers apply even successive edits within one second.
DateTime nextProfileUpdateTime(DateTime previous, DateTime now) {
  final previousSeconds = previous.millisecondsSinceEpoch ~/ 1000;
  final nowSeconds = now.millisecondsSinceEpoch ~/ 1000;
  return nowSeconds > previousSeconds
      ? now
      : DateTime.fromMillisecondsSinceEpoch((previousSeconds + 1) * 1000, isUtc: previous.isUtc);
}
