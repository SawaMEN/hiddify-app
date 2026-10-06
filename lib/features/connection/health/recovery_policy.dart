class RecoveryPolicy {
  int attempts = 0;
  Duration? nextDelay({bool adaptive = false}) {
    final seconds = adaptive ? const [5, 10, 20, 40, 60, 90, 120, 180] : const [2, 4, 8, 16, 30];
    if (attempts >= seconds.length) return null;
    return Duration(seconds: seconds[attempts++]);
  }

  void reset() => attempts = 0;
}
