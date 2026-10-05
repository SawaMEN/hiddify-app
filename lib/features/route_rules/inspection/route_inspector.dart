import 'dart:io';

import 'package:hiddify/hiddifycore/generated/v2/config/route_rule.pb.dart';

enum MatchResult { yes, no, unknown }

class RouteQuery {
  const RouteQuery(this.host, {this.package = '', this.port, this.network = Network.all});
  final String host, package;
  final int? port;
  final Network network;
  static String? normalize(String input) {
    final value = input.trim().toLowerCase();
    if (InternetAddress.tryParse(value) != null) return value;
    if (value.length > 253 || value.isEmpty || value.contains('://')) return null;
    final trimmed = value.endsWith('.') ? value.substring(0, value.length - 1) : value;
    final valid = trimmed.split('.').every((l) => RegExp(r'^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$').hasMatch(l));
    return valid ? trimmed : null;
  }
}

class RouteDecision {
  const RouteDecision(this.rule, this.match);
  final Rule? rule;
  final MatchResult match;
}

abstract class RouteInspector {
  static RouteDecision inspect(List<Rule> rules, RouteQuery query) {
    final sorted = rules.where((r) => r.enabled).toList()..sort((a, b) => a.listOrder.compareTo(b.listOrder));
    for (final rule in sorted) {
      final result = matches(rule, query);
      // An uncertain earlier rule may take precedence over any later definite match.
      if (result != MatchResult.no) return RouteDecision(rule, result);
    }
    return const RouteDecision(null, MatchResult.no);
  }

  static MatchResult matches(Rule r, RouteQuery q) {
    final groups = <MatchResult>[];
    final destination = <MatchResult>[];
    if (r.domains.isNotEmpty) destination.add(_bool(r.domains.any((d) => d.toLowerCase() == q.host)));
    if (r.domainSuffixes.isNotEmpty)
      destination.add(
        _bool(
          r.domainSuffixes.any((d) {
            final suffix = d.toLowerCase();
            return suffix.startsWith('.') ? q.host.endsWith(suffix) : q.host == suffix || q.host.endsWith('.$suffix');
          }),
        ),
      );
    if (r.domainKeywords.isNotEmpty)
      destination.add(_bool(r.domainKeywords.any((d) => q.host.contains(d.toLowerCase()))));
    if (r.domainRegexes.isNotEmpty) destination.add(_or(r.domainRegexes.map((p) => _regex(p, q.host))));
    if (r.ipCidrs.isNotEmpty) {
      final ip = InternetAddress.tryParse(q.host);
      destination.add(ip == null ? MatchResult.unknown : _or(r.ipCidrs.map((cidr) => _cidr(cidr, ip))));
    }
    // Rule sets merge into default matching criteria; their contents are opaque here.
    if (r.ruleSets.isNotEmpty) return MatchResult.unknown;
    if (destination.isNotEmpty) groups.add(_or(destination));
    if (r.packageNames.isNotEmpty)
      groups.add(q.package.isEmpty ? MatchResult.unknown : _bool(r.packageNames.contains(q.package)));
    if (r.portRanges.isNotEmpty)
      groups.add(q.port == null ? MatchResult.unknown : _or(r.portRanges.map((p) => _port(p, q.port!))));
    if (r.network != Network.all)
      groups.add(q.network == Network.all ? MatchResult.unknown : _bool(r.network == q.network));
    if (r.processNames.isNotEmpty ||
        r.processPaths.isNotEmpty ||
        r.protocols.isNotEmpty ||
        r.sourceIpCidrs.isNotEmpty ||
        r.sourcePortRanges.isNotEmpty)
      groups.add(MatchResult.unknown);
    if (groups.contains(MatchResult.no)) return MatchResult.no;
    if (groups.contains(MatchResult.unknown)) return MatchResult.unknown;
    return MatchResult.yes;
  }

  static MatchResult _bool(bool value) => value ? MatchResult.yes : MatchResult.no;
  static MatchResult _or(Iterable<MatchResult> values) => values.contains(MatchResult.yes)
      ? MatchResult.yes
      : values.contains(MatchResult.unknown)
      ? MatchResult.unknown
      : MatchResult.no;
  static MatchResult _regex(String pattern, String host) {
    // Avoid pathological Dart backtracking and unsupported RE2 syntax.
    if (pattern.length > 128 ||
        RegExp(r'[(){}|]|\\[1-9]').hasMatch(pattern) ||
        RegExp(r'[+*?]').allMatches(pattern).length > 2)
      return MatchResult.unknown;
    try {
      return _bool(RegExp(pattern).hasMatch(host));
    } catch (_) {
      return MatchResult.unknown;
    }
  }

  static MatchResult _port(String range, int port) {
    final parts = range.split(':');
    if (parts.length == 1) {
      final p = int.tryParse(range);
      return p == null ? MatchResult.unknown : _bool(p == port);
    }
    if (parts.length != 2) return MatchResult.unknown;
    final first = parts.first.isEmpty ? 0 : int.tryParse(parts.first),
        last = parts.last.isEmpty ? 65535 : int.tryParse(parts.last);
    return first == null || last == null ? MatchResult.unknown : _bool(port >= first && port <= last);
  }

  static MatchResult _cidr(String cidr, InternetAddress address) {
    final parts = cidr.split('/'), network = InternetAddress.tryParse(cidr.split('/').first);
    if (network == null) return MatchResult.unknown;
    final bits = parts.length == 1 ? network.rawAddress.length * 8 : int.tryParse(parts.last);
    if (parts.length > 2 || bits == null || bits < 0 || bits > network.rawAddress.length * 8)
      return MatchResult.unknown;
    if (network.type != address.type) return MatchResult.no;
    for (var i = 0; i < bits; i++) {
      final mask = 1 << (7 - i % 8);
      if (network.rawAddress[i ~/ 8] & mask != address.rawAddress[i ~/ 8] & mask) return MatchResult.no;
    }
    return MatchResult.yes;
  }
}
