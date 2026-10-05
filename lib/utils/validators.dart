import 'dart:io';

final _dnsLabel = RegExp(
  r'^[a-z\d\u00a1-\uffff](?:[a-z\d\u00a1-\uffff-]*[a-z\d\u00a1-\uffff])?$',
  caseSensitive: false,
);
final _digits = RegExp(r'^\d+$');
final _spaceOrControl = RegExp(r'[\s\x00-\x1f\x7f]');

bool _isHostname(String value) {
  final host = value.endsWith('.') ? value.substring(0, value.length - 1) : value;
  if (host.isEmpty || host.length > 253) return false;
  return host.split('.').every((label) => label.length <= 63 && _dnsLabel.hasMatch(label));
}

bool isUrl(String input) {
  final value = input.trim();
  if (value.isEmpty || _spaceOrControl.hasMatch(value)) return false;
  try {
    final uri = Uri.parse(value);
    if (!{'http', 'https', 'ftp'}.contains(uri.scheme.toLowerCase()) || !uri.hasAuthority || uri.host.isEmpty) {
      return false;
    }
    final host = uri.host.replaceAll('[', '').replaceAll(']', '');
    final address = InternetAddress.tryParse(host);
    if (address == null && (RegExp(r'^[\d.]+$').hasMatch(host) || !_isHostname(host))) return false;
    if (uri.hasPort && !isPort(uri.port.toString())) return false;
    return true;
  } on FormatException {
    return false;
  }
}

bool isPort(String input) {
  if (!_digits.hasMatch(input)) return false;
  final value = int.tryParse(input);
  return value != null && value >= 1 && value <= 65535;
}

bool isProcessName(String input) =>
    input.trim().isNotEmpty && !input.contains('/') && !input.contains('\\') && !input.contains('\x00');

bool isProcessPath(String input) {
  if (input.contains('\x00')) return false;
  // Unix paths, or absolute Windows paths. Platform-specific names may contain
  // spaces and Unicode; matching names should not accidentally accept paths.
  return input.startsWith('/') && input.length > 1 || RegExp(r'^[a-zA-Z]:\\.+$').hasMatch(input);
}

bool isPortOrPortRange(String input) {
  final parts = input.split(':');
  if (parts.length == 1) return isPort(parts.single);
  if (parts.length != 2 || !parts.every(isPort)) return false;
  return int.parse(parts.first) <= int.parse(parts.last);
}

bool isIpCidr(String input) {
  final parts = input.split('/');
  if (parts.isEmpty || parts.length > 2) return false;
  final address = InternetAddress.tryParse(parts.first);
  if (address == null) return false;
  if (parts.length == 1) return true;
  if (!_digits.hasMatch(parts.last)) return false;
  final prefix = int.tryParse(parts.last);
  final max = address.type == InternetAddressType.IPv6 ? 128 : 32;
  return prefix != null && prefix >= 0 && prefix <= max;
}

bool isDomain(String input) => _isHostname(input) && input.contains('.');

bool isDomainSuffix(String input) => _isHostname(input.startsWith('.') ? input.substring(1) : input);
