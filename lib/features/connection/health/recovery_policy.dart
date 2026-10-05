class RecoveryPolicy {
  int attempts = 0;
  Duration? nextDelay() {
    const seconds = [2, 4, 8, 16, 30];
    if (attempts >= seconds.length) return null;
    return Duration(seconds: seconds[attempts++]);
  }

  void reset() => attempts = 0;
}
