import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:fpdart/fpdart.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/features/connection/data/connection_data_providers.dart';
import 'package:hiddify/features/connection/data/connection_repository.dart';
import 'package:hiddify/features/connection/model/connection_failure.dart';
import 'package:hiddify/features/connection/model/connection_status.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/singbox/model/singbox_config_option.dart';

class _Profile extends ActiveProfile {
  @override
  Stream<ProfileEntity?> build() => Stream.value(
    ProfileEntity.local(
      id: 'test',
      active: true,
      name: 'test',
      lastUpdate: DateTime(2026),
    ),
  );
}

class _Repository implements ConnectionRepository {
  final events = StreamController<ConnectionStatus>.broadcast();
  int connections = 0, disconnections = 0;
  bool fail = true;
  @override
  SingboxConfigOption? get configOptionsSnapshot => null;
  @override
  Stream<ConnectionStatus> watchConnectionStatus() => events.stream;
  @override
  TaskEither<ConnectionFailure, Unit> setup() => TaskEither.of(unit);
  @override
  TaskEither<ConnectionFailure, Unit> connect(ProfileEntity p, bool disabled) =>
      TaskEither(
        () => Future.value(
          (++connections > 0 && fail)
              ? left(const ConnectionFailure.backgroundCoreNotAvailable())
              : right(unit),
        ),
      );
  @override
  TaskEither<ConnectionFailure, Unit> disconnect() => TaskEither(
    () => Future.value((++disconnections > 0) ? right(unit) : right(unit)),
  );
  @override
  TaskEither<ConnectionFailure, Unit> reconnect(ProfileEntity p, bool d) =>
      connect(p, d);
}

void main() {
  for (final (cancel, plainStop) in [
    (true, false),
    (false, false),
    (false, true),
  ]) {
    testWidgets(
      cancel
          ? 'Manual abort cancels pending recovery'
          : 'Recovery stops after five failed attempts (plainStop=$plainStop)',
      (tester) async {
        SharedPreferences.setMockInitialValues({
          'started_by_user': true,
          'haptic_feedback': false,
        });
        final preferences = await SharedPreferences.getInstance();
        final repo = _Repository();
        final container = ProviderContainer(
          overrides: [
            sharedPreferencesProvider.overrideWith((ref) async => preferences),
            connectionRepositoryProvider.overrideWithValue(repo),
            activeProfileProvider.overrideWith(_Profile.new),
          ],
        );
        await container.read(sharedPreferencesProvider.future);
        final subscription = container.listen(
          connectionNotifierProvider,
          (_, __) {},
        );
        await tester.pump();
        await tester.pump();
        if (plainStop) {
          repo.events.add(const Connected());
          await tester.pump();
          await tester.pump();
        }
        repo.events.add(
          plainStop
              ? const Disconnected()
              : const Disconnected(
                  ConnectionFailure.backgroundCoreNotAvailable(),
                ),
        );
        await tester.pump();
        await tester.pump();
        expect(container.read(recoveryStatusProvider), 1);
        if (cancel) {
          await container
              .read(connectionNotifierProvider.notifier)
              .abortConnection();
          expect(container.read(Preferences.startedByUser), false);
          await tester.pump(const Duration(seconds: 60));
          expect(repo.connections, 0);
        } else {
          for (final seconds in [2, 4, 8, 16, 30]) {
            await tester.pump(Duration(seconds: seconds));
            await tester.pump();
          }
          expect(repo.connections, 5);
          expect(container.read(recoveryStatusProvider), 0);
          await tester.pump(const Duration(minutes: 2));
          expect(repo.connections, 5);
        }
        subscription.close();
        container.dispose();
        await repo.events.close();
        await tester.pump();
      },
    );
  }
  testWidgets(
    'Stable duplicate status events reset recovery without postponing the timer',
    (tester) async {
      SharedPreferences.setMockInitialValues({
        'started_by_user': true,
        'haptic_feedback': false,
      });
      final preferences = await SharedPreferences.getInstance();
      final repo = _Repository();
      final container = ProviderContainer(
        overrides: [
          sharedPreferencesProvider.overrideWith((ref) async => preferences),
          connectionRepositoryProvider.overrideWithValue(repo),
          activeProfileProvider.overrideWith(_Profile.new),
        ],
      );
      await container.read(sharedPreferencesProvider.future);
      final subscription = container.listen(
        connectionNotifierProvider,
        (_, __) {},
      );
      await tester.pump();
      await tester.pump();
      repo.events.add(
        const Disconnected(ConnectionFailure.backgroundCoreNotAvailable()),
      );
      await tester.pump();
      await tester.pump();
      await tester.pump(const Duration(seconds: 2));
      await tester.pump();
      await tester.pump(const Duration(seconds: 4));
      await tester.pump();
      expect(repo.connections, 2);
      repo.events.add(const Connected());
      await tester.pump();
      await tester.pump();
      await tester.pump(const Duration(seconds: 30));
      repo.events.add(const Connected());
      await tester.pump();
      await tester.pump(const Duration(seconds: 31));
      repo.events.add(
        const Disconnected(ConnectionFailure.backgroundCoreNotAvailable()),
      );
      await tester.pump();
      await tester.pump();
      expect(container.read(recoveryStatusProvider), 1);
      await tester.pump(const Duration(seconds: 2));
      await tester.pump();
      expect(repo.connections, 3);
      subscription.close();
      container.dispose();
      await repo.events.close();
      await tester.pump();
    },
  );
}
