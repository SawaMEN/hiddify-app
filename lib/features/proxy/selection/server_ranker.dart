class ServerSample {
  ServerSample({this.average = 0, this.successes = 0, this.failures = 0, this.updated = 0});
  double average;
  int successes, failures, updated;
  void observe(int delay, int timestamp) {
    if (timestamp <= updated) return;
    updated = timestamp;
    if (delay > 0 && delay < 65000) {
      average = successes == 0 ? delay.toDouble() : average * .7 + delay * .3;
      successes = (successes + 1).clamp(0, 100);
      failures = 0;
    } else {
      failures = (failures + 1).clamp(0, 20);
    }
  }

  Map<String, dynamic> toJson() => {
    'average': average,
    'successes': successes,
    'failures': failures,
    'updated': updated,
  };
  factory ServerSample.fromJson(Map<String, dynamic> m) => ServerSample(
    average: (m['average'] as num).toDouble(),
    successes: m['successes'] as int,
    failures: m['failures'] as int,
    updated: m['updated'] as int,
  );
}

class ServerRanker {
  final Map<String, ServerSample> samples = {};
  String? _candidate;
  int _wins = 0, _evaluated = 0, _lastSwitch = 0;
  String? recommend(String current, Set<String> eligible, int now) {
    if (_lastSwitch != 0 && now - _lastSwitch < 120000) return null;
    final candidates =
        samples.entries
            .where(
              (e) =>
                  eligible.contains(e.key) &&
                  e.value.successes >= 3 &&
                  e.value.failures == 0 &&
                  now - e.value.updated >= 0 &&
                  now - e.value.updated <= 600000,
            )
            .toList()
          ..sort((a, b) => a.value.average.compareTo(b.value.average));
    if (candidates.isEmpty || candidates.first.key == current) {
      _wins = 0;
      _candidate = null;
      return null;
    }
    final best = candidates.first;
    final selected = samples[current];
    if (selected == null ||
        (selected.failures < 3 &&
            (selected.successes == 0 ||
                selected.average - best.value.average < 50 ||
                best.value.average > selected.average * .8))) {
      _wins = 0;
      _candidate = null;
      return null;
    }
    final stamp = best.value.updated > selected.updated ? best.value.updated : selected.updated;
    if (stamp <= _evaluated) return null;
    _evaluated = stamp;
    if (_candidate == best.key) {
      _wins++;
    } else {
      _candidate = best.key;
      _wins = 1;
    }
    return _wins >= 3 ? best.key : null;
  }

  void switched(int now) {
    _lastSwitch = now;
    _wins = 0;
    _candidate = null;
  }
}

Future<void> _selectionTail = Future<void>.value();
Future<T> serializedProxySelection<T>(Future<T> Function() operation) {
  final next = _selectionTail.then((_) => operation());
  _selectionTail = next.then<void>((_) {}, onError: (Object e, StackTrace s) {});
  return next;
}
