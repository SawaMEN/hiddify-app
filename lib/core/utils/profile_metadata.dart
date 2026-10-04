import 'dart:convert';

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
