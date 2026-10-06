import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

final _serviceProvider = Provider<HiddifyCoreService>(HiddifyCoreService.new);

void main() {
  for (final emitsData in [false, true]) {
    testWidgets('Control streams continue slowly after five retries (emitsData=$emitsData)', (tester) async {
      final container = ProviderContainer();
      final service = container.read(_serviceProvider);
      var subscriptions = 0;
      Stream<int> source() {
        subscriptions++;
        // An initial status/log event is not evidence of a stable connection.
        return emitsData ? Stream.value(1) : Stream.error(StateError('control server unavailable'));
      }

      await service.listenSingle<int>('test', source, reconnect: true);
      await tester.pump();
      for (final seconds in [2, 4, 8, 16, 30]) {
        await tester.pump(Duration(seconds: seconds));
        await tester.pump();
      }
      expect(subscriptions, 6);
      await tester.pump(const Duration(minutes: 5));
      expect(subscriptions, 7);

      // An explicit new lifecycle gives the channel a fresh recovery budget.
      await service.stopListenSingle('test');
      await service.listenSingle<int>('test', source, reconnect: true);
      await tester.pump();
      await tester.pump(const Duration(seconds: 2));
      await tester.pump();
      expect(subscriptions, 9);
      await service.dispose();
      container.dispose();
      await tester.pump(const Duration(minutes: 1));
      expect(subscriptions, 9);
    });
  }

  testWidgets('Stopping listeners cancels an outstanding retry', (tester) async {
    final container = ProviderContainer();
    final service = container.read(_serviceProvider);
    var subscriptions = 0;
    await service.listenSingle<int>('test', () {
      subscriptions++;
      return Stream.error(StateError('unavailable'));
    }, reconnect: true);
    await tester.pump();
    await service.stopListenSingle('test');
    await tester.pump(const Duration(minutes: 1));
    expect(subscriptions, 1);
    await service.dispose();
    container.dispose();
  });
}
