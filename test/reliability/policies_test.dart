import 'dart:convert';

import 'package:hiddify/features/connection/health/connection_health.dart';

import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/privacy/redactor.dart';
import 'package:hiddify/features/connection/health/recovery_policy.dart';
import 'package:hiddify/features/profile/import/import_summary.dart';
import 'package:hiddify/features/proxy/selection/server_ranker.dart';

void main() {
  test('A captive portal 200 cannot satisfy a 204 probe', () {
    expect(
      validProbeStatus('https://cp.cloudflare.com/generate_204', 200),
      false,
    );
    expect(
      validProbeStatus('https://cp.cloudflare.com/generate_204', 204),
      true,
    );
    expect(validProbeStatus('https://example.org/check', 200), true);
    expect(validProbeStatus('https://example.org/check', 500), false);
  });
  test('Recovery is bounded and resets only explicitly', () {
    final p = RecoveryPolicy();
    expect(List.generate(5, (_) => p.nextDelay()!.inSeconds), [
      2,
      4,
      8,
      16,
      30,
    ]);
    expect(p.nextDelay(), isNull);
    expect(p.nextDelay(), isNull);
    p.reset();
    expect(p.nextDelay()!.inSeconds, 2);
  });
  test('Repeated snapshots do not create successful checks', () {
    final s = ServerSample();
    s.observe(100, 1000);
    s.observe(10, 1000);
    expect(s.successes, 1);
    expect(s.average, 100);
    s.observe(65000, 2000);
    expect(s.failures, 1);
  });
  test(
    'Smart selection waits for stable improvement and respects cooldown',
    () {
      final r = ServerRanker();
      for (var i = 1; i <= 6; i++) {
        (r.samples['slow'] ??= ServerSample()).observe(300, i * 1000);
        (r.samples['fast'] ??= ServerSample()).observe(100, i * 1000);
        final chosen = r.recommend('slow', {'slow', 'fast'}, i * 1000);
        expect(chosen, i >= 5 ? 'fast' : isNull);
      }
      r.switched(6000);
      expect(r.recommend('slow', {'slow', 'fast'}, 7000), isNull);
    },
  );
  test('A small latency change and stale measurements do not switch', () {
    final r = ServerRanker();
    for (var i = 1; i <= 8; i++) {
      (r.samples['a'] ??= ServerSample()).observe(100, i * 1000);
      (r.samples['b'] ??= ServerSample()).observe(90, i * 1000);
      expect(r.recommend('a', {'a', 'b'}, i * 1000), isNull);
    }
    expect(r.recommend('a', {'a', 'b'}, 900000), isNull);
  });
  test(
    'JSON preview counts duplicates without server names and detects overrides',
    () {
      final s = ImportSummary.parse(
        jsonEncode({
          'outbounds': [
            {
              'type': 'vless',
              'tag': 'a',
              'server': 'example.org',
              'server_port': 443,
            },
            {
              'type': 'vless',
              'tag': 'b',
              'server': 'example.org',
              'server_port': 443,
            },
            {'type': 'future-protocol', 'tag': 'future'},
            {'type': 'direct'},
          ],
          'dns': {},
        }),
      );
      expect(s.servers.length, 3);
      expect(s.duplicates, 1);
      expect(s.unknownTypes, 1);
      expect(s.overrides, ['dns']);
    },
  );
  test('Base64 subscription preview detects link duplicates', () {
    final s = ImportSummary.parse(
      base64.encode(
        utf8.encode(
          'vless://id@example.org:443#a\nvless://id@example.org:443#b',
        ),
      ),
    );
    expect(s.servers.length, 2);
    expect(s.duplicates, 1);
  });
  test('Reports remove URLs, credentials, IPs, IDs and paths recursively', () {
    final result = jsonEncode(
      DiagnosticRedactor.value({
        'password': 'secret-value',
        'message': 'vless://abc@vpn.example.org:443 192.0.2.1 2001:db8::1 /home/alice/a 01234567-abcd-1234-9876-0123456789ab',
        'token': 'secret-token',
      }),
    );
    for (final secret in [
      'secret-value',
      'secret-token',
      '192.0.2.1',
      '2001:db8::1',
      'vpn.example.org',
      'alice',
      '01234567-abcd',
    ])
      expect(result, isNot(contains(secret)));
  });
}
