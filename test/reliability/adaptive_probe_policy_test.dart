import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/features/connection/health/adaptive_probe_policy.dart';
import 'package:hiddify/features/connection/health/recovery_policy.dart';
import 'package:hiddify/features/connection/model/connection_failure.dart';
import 'package:hiddify/singbox/model/core_status.dart';

void main() {
  test('variable links get a longer deadline after slow replies or a timeout', () {
    final policy = AdaptiveProbePolicy(enabled: true);
    for (var i = 0; i < 3; i++) {
      policy.record(healthy: true, elapsed: const Duration(milliseconds: 200));
    }
    expect(policy.timeout, const Duration(seconds: 12));
    policy.record(healthy: false, elapsed: const Duration(seconds: 12));
    expect(policy.timeout, const Duration(seconds: 30));
    for (var i = 0; i < 3; i++) {
      policy.record(healthy: true, elapsed: const Duration(seconds: 9));
    }
    expect(policy.timeout, greaterThan(const Duration(seconds: 12)));
    expect(policy.interval, const Duration(seconds: 60));
    expect(policy.recoveryThreshold, 5);
  });

  test('normal mode keeps the previous deadlines and retry limit', () {
    final policy = AdaptiveProbePolicy(enabled: false);
    policy.record(healthy: false, elapsed: const Duration(seconds: 20));
    expect(policy.timeout, const Duration(seconds: 12));
    expect(policy.interval, const Duration(seconds: 30));
    final recovery = RecoveryPolicy();
    for (final value in [2, 4, 8, 16, 30]) {
      expect(recovery.nextDelay(), Duration(seconds: value));
    }
    expect(recovery.nextDelay(), isNull);
  });

  test('adaptive backoff is bounded and reset after a stable connection', () {
    final policy = RecoveryPolicy();
    for (final value in [5, 10, 20, 40, 60, 90, 120, 180]) {
      expect(policy.nextDelay(adaptive: true), Duration(seconds: value));
    }
    expect(policy.nextDelay(adaptive: true), isNull);
    policy.reset();
    expect(policy.nextDelay(adaptive: true), const Duration(seconds: 5));
  });

  test('missing modern servers is a configuration failure rather than automatic retries', () {
    const status = CoreStopped(
      alert: CoreAlert.startFailed,
      message: 'modern protocols only: no compatible Hysteria 2 or TUIC servers',
    );
    expect(status.getCoreAlert(), isA<InvalidConfig>());
  });
}
