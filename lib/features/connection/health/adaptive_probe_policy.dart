/// A few fast responses cannot erase a recent timeout on a variable link.
class AdaptiveProbePolicy {
  AdaptiveProbePolicy({required this.enabled});
  final bool enabled;
  int failures = 0, _successes = 0;
  double _averageMs = 0;

  Duration get timeout {
    if (!enabled) return const Duration(seconds: 12);
    if (failures > 0 || _successes < 3) return const Duration(seconds: 30);
    return Duration(seconds: (8 + (_averageMs * 4 / 1000).ceil()).clamp(12, 30));
  }

  Duration get interval => Duration(seconds: enabled && failures == 0 ? 60 : 30);
  int get recoveryThreshold => enabled ? 5 : 3;

  void record({required bool healthy, required Duration elapsed}) {
    if (!healthy) {
      failures++;
      _successes = 0;
      return;
    }
    failures = 0;
    _successes++;
    final ms = elapsed.inMilliseconds.toDouble();
    _averageMs = _averageMs == 0 ? ms : _averageMs * .7 + ms * .3;
  }
}
