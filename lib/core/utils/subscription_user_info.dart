/// Parses optional subscription metadata without rejecting a usable profile.
Map<String, int> parseSubscriptionUserInfo(String value) {
  final result = <String, int>{};
  for (final entry in value.split(';')) {
    final separator = entry.indexOf('=');
    if (separator < 0) continue;
    final key = entry.substring(0, separator).trim();
    if (!const {'upload', 'download', 'total', 'expire'}.contains(key)) continue;
    final number = int.tryParse(entry.substring(separator + 1).trim());
    if (number != null && number >= 0) result[key] = number;
  }
  return result;
}
