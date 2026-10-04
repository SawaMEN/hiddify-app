import 'dart:async';

class Throttler {
  Throttler(this.throttleFor);
  final Duration throttleFor;
  DateTime? _lastCall;
  bool _running = false;

  Future<void> call(FutureOr<void> Function() callback) async {
    if (_running || (_lastCall != null && DateTime.now().difference(_lastCall!) < throttleFor)) return;
    _running = true;
    _lastCall = DateTime.now();
    try {
      await callback();
    } finally {
      _running = false;
    }
  }
}
