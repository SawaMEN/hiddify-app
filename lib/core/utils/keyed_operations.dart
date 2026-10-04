/// Serialize operations touching the same resource without blocking unrelated resources.
class KeyedOperations {
  final Map<String, Future<void>> _tails = {};

  Future<T> run<T>(String key, Future<T> Function() operation) async {
    final previous = _tails[key] ?? Future<void>.value();
    final next = previous.then((_) => operation());
    final tail = next.then<void>((_) {}, onError: (Object e, StackTrace st) {});
    _tails[key] = tail;
    try {
      return await next;
    } finally {
      if (identical(_tails[key], tail)) _tails.remove(key);
    }
  }
}
