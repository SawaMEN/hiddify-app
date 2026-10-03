import 'dart:async';

class LastStream<T> {
  T? _latest;
  bool _hasValue = false;
  bool _closed = false;
  late final StreamSubscription<T> _sub;
  final List<Completer<T>> _waiters = [];

  LastStream(Stream<T> source) {
    _sub = source.listen(
      (event) {
        _latest = event;
        _hasValue = true;
        for (final completer in _waiters) {
          if (!completer.isCompleted) completer.complete(event);
        }
        _waiters.clear();
      },
      onError: (Object error, StackTrace stackTrace) {
        clean();
        _completeWaitersWithError(error, stackTrace);
      },
      onDone: () {
        _closed = true;
        clean();
        _completeWaitersWithError(StateError('Stream closed'), StackTrace.current);
      },
    );
  }

  void clean() {
    _latest = null;
    _hasValue = false;
  }

  /// Returns the latest value, or waits up to [timeout] for one.
  Future<T> get({Duration? timeout}) async {
    if (_closed) throw StateError('Stream closed');
    if (_hasValue) return _latest as T;

    final completer = Completer<T>();
    _waiters.add(completer);
    try {
      return await (timeout == null ? completer.future : completer.future.timeout(timeout));
    } finally {
      _waiters.remove(completer);
    }
  }

  void _completeWaitersWithError(Object error, StackTrace stackTrace) {
    for (final completer in _waiters) {
      if (!completer.isCompleted) completer.completeError(error, stackTrace);
    }
    _waiters.clear();
  }

  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    clean();
    _completeWaitersWithError(StateError('Stream closed'), StackTrace.current);
    await _sub.cancel();
  }
}
