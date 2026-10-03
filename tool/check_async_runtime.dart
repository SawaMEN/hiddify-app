import 'dart:async';

import '../lib/core/utils/laststeam.dart';
import '../lib/core/utils/single_call.dart';

void check(bool condition, String message) {
  if (!condition) throw StateError(message);
}

Future<void> expectError<T extends Object>(Future<Object?> future) async {
  try {
    await future;
  } catch (error) {
    check(error is T, 'Expected $T, got ${error.runtimeType}');
    return;
  }
  throw StateError('Expected $T');
}

Future<void> main() async {
  final events = StreamController<int>(sync: true);
  final stream = LastStream(events.stream);
  final first = stream.get();
  final second = stream.get();
  events.add(1);
  check(await first == 1 && await second == 1, 'All waiters must receive the event');
  check(await stream.get() == 1, 'Latest value must be cached');

  stream.clean();
  await expectError<TimeoutException>(stream.get(timeout: const Duration(milliseconds: 5)));
  final afterTimeout = stream.get();
  events.add(2);
  check(await afterTimeout == 2, 'Timeout must not leave a stale waiter');

  stream.clean();
  final failed = expectError<StateError>(stream.get());
  events.addError(StateError('service failed'));
  await failed;
  final recovered = stream.get();
  events.add(3);
  check(await recovered == 3, 'Stream must recover after a source error');

  stream.clean();
  final closed = expectError<StateError>(stream.get());
  await events.close();
  await closed;
  await expectError<StateError>(stream.get());
  await stream.close();

  var cancellations = 0;
  final cachedEvents = StreamController<int>(sync: true, onCancel: () => cancellations++);
  final cached = LastStream(cachedEvents.stream);
  cachedEvents.add(4);
  await cached.close();
  await cached.close();
  await expectError<StateError>(cached.get());
  check(cancellations == 1, 'Dispose must cancel the source once');
  await cachedEvents.close();

  final single = SingleCall();
  final gate = Completer<int>();
  final running = single.run(() => gate.future, onIgnored: -1);
  var duplicateRan = false;
  final duplicate = await single.run(() async {
    duplicateRan = true;
    return 2;
  }, onIgnored: -1);
  check(duplicate == -1 && !duplicateRan, 'Concurrent start must be ignored');
  gate.complete(7);
  check(await running == 7, 'Caller must await the complete start operation');
  await expectError<StateError>(single.run<int>(() async => throw StateError('start failed'), onIgnored: -1));
  check(await single.run(() async => 9, onIgnored: -1) == 9, 'A failed start must release the guard');
  print('Async runtime regression checks passed.');
}
