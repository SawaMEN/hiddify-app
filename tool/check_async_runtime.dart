import 'dart:async';

import '../lib/core/utils/laststeam.dart';
import '../lib/core/utils/single_call.dart';
import '../lib/core/utils/subscription_user_info.dart';
import '../lib/core/utils/profile_metadata.dart';

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
  for (final value in [null, 'null', '[]', '"text"', '{broken']) {
    check(decodeOptionalJsonObject(value) == null, 'Invalid optional JSON must not break startup: $value');
  }
  check(
    decodeOptionalJsonObject('{"profile-title":"test"}')?['profile-title'] == 'test',
    'Valid stored headers must survive',
  );
  check(parseProfileTitle('base64:%%%') == null, 'Malformed title must allow fallback naming');
  check(parseProfileTitle('base64:/w==') == null, 'Invalid UTF-8 title must allow fallback naming');
  check(parseProfileTitle('base64:SGVsbG8') == 'Hello', 'Unpadded base64 title must decode');
  check(parseProfileTitle('  hello  ') == 'hello', 'Title whitespace must be trimmed');
  check(parseProfileTitle('base64:IA==') == null, 'Blank title must allow fallback naming');
  for (final value in ['invalid', '', '0', '-1', '2562047789', '999999999999999999999']) {
    check(parseProfileUpdateInterval(value) == null, 'Invalid interval must be ignored: $value');
  }
  check(parseProfileUpdateInterval(' 12 ') == const Duration(hours: 12), 'Valid interval must survive');
  final metadata = parseSubscriptionUserInfo(
    'upload=12;download=34; total=0; expire=999; ; broken; total=invalid; download=-1; unknown=10',
  );
  check(metadata['upload'] == 12 && metadata['download'] == 34, 'Valid traffic metadata must survive malformed fields');
  check(metadata['total'] == 0 && metadata['expire'] == 999, 'Unlimited traffic and expiry must be preserved');
  check(!metadata.containsKey('unknown'), 'Unknown fields must be ignored');
  check(parseSubscriptionUserInfo('upload=;download=abc;expire=-5').isEmpty, 'Invalid metadata must not crash imports');
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
