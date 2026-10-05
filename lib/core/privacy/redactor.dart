/// Best-effort sanitization. Users must review a report before sharing it.
abstract class DiagnosticRedactor {
  static final _privateKey = RegExp(
    r'password|passwd|token|secret|authorization|uuid|private.?key|subscription|address|host|domain|path|email|username',
    caseSensitive: false,
  );
  static String text(String value) => value
      .replaceAll(RegExp(r'\b[a-z][a-z0-9+.-]*://[^\s\x22\x27<>]+', caseSensitive: false), '[link]')
      .replaceAll(
        RegExp(r'\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b', caseSensitive: false),
        '[id]',
      )
      .replaceAll(RegExp(r'\b(?:\d{1,3}\.){3}\d{1,3}\b'), '[ip]')
      .replaceAll(RegExp(r'(?:[0-9a-f]{0,4}:){2,}[0-9a-f:.]*', caseSensitive: false), '[ip]')
      .replaceAll(
        RegExp(r'(password|token|secret|authorization|uuid|private[_-]?key)\s*[:=]\s*[^\s,;}]+', caseSensitive: false),
        '[credential]',
      )
      .replaceAll(RegExp(r'(?:/home/|/data/|/storage/|[A-Z]:\\)[^\s\x22\x27]+', caseSensitive: false), '[path]')
      .replaceAll(RegExp(r'\b(?:[a-z0-9-]+\.)+[a-z]{2,63}\b', caseSensitive: false), '[host]')
      .replaceAll(RegExp(r'\b[A-Za-z0-9_+/=-]{32,}\b'), '[token]');
  static dynamic value(dynamic input, [int depth = 0]) {
    if (depth > 8) return '[depth limit]';
    if (input is Map)
      return {
        for (final entry in input.entries)
          entry.key.toString(): _privateKey.hasMatch(entry.key.toString())
              ? '[redacted]'
              : value(entry.value, depth + 1),
      };
    if (input is List) return input.take(100).map((e) => value(e, depth + 1)).toList();
    if (input is String) return text(input);
    if (input == null || input is bool || input is num) return input;
    return text(input.toString());
  }
}
