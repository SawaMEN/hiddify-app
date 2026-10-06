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
import 'package:hiddify/hiddifycore/init_signal.dart';
import 'package:hiddify/features/connection/health/android_vpn_settings.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service_provider.dart';
import 'package:hiddify/singbox/model/core_status.dart';

class _Profile extends ActiveProfile {
  @override
  Stream<ProfileEntity?> build() =>
      Stream.value(ProfileEntity.local(id: 'test', active: true, name: 'test', lastUpdate: DateTime(2026)));
}

class _Repository implements ConnectionRepository {
  final events = StreamController<ConnectionStatus>.broadcast();
  int connections = 0, disconnections = 0;
  bool fail = true;
  bool profileChanged = true;
  void Function()? onConnect;
  Completer<void>? connectGate;
  @override
  SingboxConfigOption? get configOptionsSnapshot => null;
  @override
  Future<bool> profileRequiresReconnect(ProfileEntity profile) async => profileChanged;
  @override
  Stream<ConnectionStatus> watchConnectionStatus() => events.stream;
  @override
  TaskEither<ConnectionFailure, Unit> setup() => TaskEither.of(unit);
  @override
  TaskEither<ConnectionFailure, Unit> connect(ProfileEntity p, bool disabled) => TaskEither(() async {
    connections++;
    onConnect?.call();
    await connectGate?.future;
    return fail ? left(const ConnectionFailure.backgroundCoreNotAvailable()) : right(unit);
  });
  @override
  TaskEither<ConnectionFailure, Unit> disconnect() =>
      TaskEither(() => Future.value((++disconnections > 0) ? right(unit) : right(unit)));
  @override
  TaskEither<ConnectionFailure, Unit> reconnect(ProfileEntity p, bool d) => connect(p, d);
}

class _NativeRuntime extends AndroidVpnRuntime {
  bool? online;
  @override
  Future<bool?> networkAvailable() async => online;
  bool running = true;
  int stops = 0;

  @override
  bool get isAndroid => true;
  @override
  Future<bool> wantsConnection() async => true;
  @override
  Future<bool> serviceRunning() async => running;
  @override
  Future<bool> stopService() async {
    stops++;
    running = false;
    return true;
  }
}

class _Core extends HiddifyCoreService {
  _Core(super.ref);
  CoreStatus? status = const CoreStarted();
  Completer<void>? probeGate;
  int queries = 0;

  @override
  Future<CoreStatus?> backgroundCoreStatus() async {
    queries++;
    await probeGate?.future;
    return status;
  }
}

void main() {
  for (final abort in [false, true]) {
    testWidgets('Adaptive recovery waits for a physical network and respects abort=$abort', (tester) async {
      SharedPreferences.setMockInitialValues({
        'started_by_user': true,
        'auto_reconnect': false,
        'adaptive_network': true,
        'haptic_feedback': false,
      });
      final preferences = await SharedPreferences.getInstance();
      final repo = _Repository()..fail = false;
      final native = _NativeRuntime()
        ..running = false
        ..online = false;
      final container = ProviderContainer(
        overrides: [
          sharedPreferencesProvider.overrideWith((_) async => preferences),
          connectionRepositoryProvider.overrideWith((_) => repo),
          activeProfileProvider.overrideWith(_Profile.new),
          androidVpnRuntimeProvider.overrideWithValue(native),
          hiddifyCoreServiceProvider.overrideWith((ref) => _Core(ref)),
        ],
      );
      await container.read(sharedPreferencesProvider.future);
      final subscription = container.listen(connectionNotifierProvider, (_, __) {});
      await tester.pump();
      await tester.pump();
      final notifier = container.read(connectionNotifierProvider.notifier);
      await notifier.restoreOnResume();
      for (var i = 0; i < 8; i++) {
        await tester.pump(const Duration(seconds: 15));
        await tester.pump();
      }
      expect(repo.connections, 0);
      expect(native.stops, 0);
      expect(container.read(Preferences.startedByUser), true);
      if (abort) await notifier.abortConnection();
      native.online = true;
      await tester.pump(const Duration(seconds: 15));
      await tester.pump();
      expect(repo.connections, abort ? 0 : 1);
      subscription.close();
      container.dispose();
      await repo.events.close();
    });
  }
  for (final status in [const CoreStarted(), const CoreStarting(), const CoreStopping(), null]) {
    testWidgets('Resume preserves an existing service with daemon status $status', (tester) async {
      SharedPreferences.setMockInitialValues({
        'started_by_user': true,
        'auto_reconnect': true,
        'haptic_feedback': false,
      });
      final preferences = await SharedPreferences.getInstance();
      final repo = _Repository()..fail = false;
      final native = _NativeRuntime();
      late _Core core;
      final container = ProviderContainer(
        overrides: [
          sharedPreferencesProvider.overrideWith((ref) async => preferences),
          connectionRepositoryProvider.overrideWithValue(repo),
          activeProfileProvider.overrideWith(_Profile.new),
          androidVpnRuntimeProvider.overrideWithValue(native),
          hiddifyCoreServiceProvider.overrideWith((ref) => core = _Core(ref)..status = status),
        ],
      );
      await container.read(sharedPreferencesProvider.future);
      final subscription = container.listen(connectionNotifierProvider, (_, __) {});
      await tester.pump();
      await tester.pump();
      await container.read(connectionNotifierProvider.notifier).restoreOnResume();
      expect(native.stops, 0);
      expect(repo.connections, 0);
      expect(core.queries, 1);
      expect(container.read(Preferences.startedByUser), isTrue);
      // An unanswered/stopping core is observed again instead of restarted.
      if (status == null || status is CoreStopping) {
        core.status = const CoreStarted();
        await tester.pump(const Duration(seconds: 2));
        await tester.pump();
        expect(core.queries, 2);
        expect(native.stops, 0);
        expect(repo.connections, 0);
      }
      subscription.close();
      container.dispose();
      await repo.events.close();
      await tester.pump();
    });
  }

  testWidgets('Concurrent resume requests share one daemon probe', (tester) async {
    SharedPreferences.setMockInitialValues({'started_by_user': true, 'haptic_feedback': false});
    final preferences = await SharedPreferences.getInstance();
    final repo = _Repository()..fail = false;
    final native = _NativeRuntime();
    final gate = Completer<void>();
    late _Core core;
    final container = ProviderContainer(
      overrides: [
        sharedPreferencesProvider.overrideWith((ref) async => preferences),
        connectionRepositoryProvider.overrideWithValue(repo),
        activeProfileProvider.overrideWith(_Profile.new),
        androidVpnRuntimeProvider.overrideWithValue(native),
        hiddifyCoreServiceProvider.overrideWith((ref) => core = _Core(ref)..probeGate = gate),
      ],
    );
    await container.read(sharedPreferencesProvider.future);
    final subscription = container.listen(connectionNotifierProvider, (_, __) {});
    await tester.pump();
    await tester.pump();
    final notifier = container.read(connectionNotifierProvider.notifier);
    final first = notifier.restoreOnResume();
    await tester.pump();
    final second = notifier.restoreOnResume();
    await tester.pump();
    expect(core.queries, 1);
    gate.complete();
    await Future.wait([first, second]);
    expect(native.stops, 0);
    expect(repo.connections, 0);
    subscription.close();
    container.dispose();
    await repo.events.close();
    await tester.pump();
  });

  testWidgets('Queued resume reuses the connection that completed before it', (tester) async {
    SharedPreferences.setMockInitialValues({'started_by_user': true, 'haptic_feedback': false});
    final preferences = await SharedPreferences.getInstance();
    final gate = Completer<void>();
    final repo = _Repository()
      ..fail = false
      ..connectGate = gate;
    final native = _NativeRuntime()..running = false;
    late _Core core;
    final container = ProviderContainer(
      overrides: [
        sharedPreferencesProvider.overrideWith((ref) async => preferences),
        connectionRepositoryProvider.overrideWithValue(repo),
        activeProfileProvider.overrideWith(_Profile.new),
        androidVpnRuntimeProvider.overrideWithValue(native),
        hiddifyCoreServiceProvider.overrideWith((ref) => core = _Core(ref)),
      ],
    );
    await container.read(sharedPreferencesProvider.future);
    final subscription = container.listen(connectionNotifierProvider, (_, __) {});
    await tester.pump();
    await tester.pump();
    repo.events.add(const Disconnected());
    await tester.pump();
    final notifier = container.read(connectionNotifierProvider.notifier);
    final connection = notifier.mayConnect();
    await tester.pump();
    expect(repo.connections, 1);
    final restore = notifier.restoreOnResume();
    await tester.pump();
    native.running = true;
    gate.complete();
    await Future.wait([connection, restore]);
    expect(core.queries, 1);
    expect(repo.connections, 1, reason: 'Resume must not start a second connection');
    expect(native.stops, 0, reason: 'Resume must not tear down the completed connection');
    subscription.close();
    container.dispose();
    await repo.events.close();
    await tester.pump();
  });

  testWidgets('Recovery restarts a service only after the daemon confirms Stopped', (tester) async {
    SharedPreferences.setMockInitialValues({'started_by_user': true, 'haptic_feedback': false});
    final preferences = await SharedPreferences.getInstance();
    final repo = _Repository()..fail = false;
    final native = _NativeRuntime();
    final container = ProviderContainer(
      overrides: [
        sharedPreferencesProvider.overrideWith((ref) async => preferences),
        connectionRepositoryProvider.overrideWithValue(repo),
        activeProfileProvider.overrideWith(_Profile.new),
        androidVpnRuntimeProvider.overrideWithValue(native),
        hiddifyCoreServiceProvider.overrideWith((ref) => _Core(ref)..status = const CoreStopped()),
      ],
    );
    await container.read(sharedPreferencesProvider.future);
    final subscription = container.listen(connectionNotifierProvider, (_, __) {});
    await tester.pump();
    await tester.pump();
    await container.read(connectionNotifierProvider.notifier).restoreOnResume();
    expect(native.stops, 1);
    expect(repo.connections, 1);
    subscription.close();
    container.dispose();
    await repo.events.close();
    await tester.pump();
  });

  testWidgets('Queued duplicate profile refreshes restart the core once', (tester) async {
    SharedPreferences.setMockInitialValues({'started_by_user': true, 'haptic_feedback': false});
    final preferences = await SharedPreferences.getInstance();
    final gate = Completer<void>();
    final repo = _Repository()
      ..fail = false
      ..connectGate = gate;
    repo.onConnect = () => repo.profileChanged = false;
    final container = ProviderContainer(
      overrides: [
        sharedPreferencesProvider.overrideWith((ref) async => preferences),
        connectionRepositoryProvider.overrideWithValue(repo),
        activeProfileProvider.overrideWith(_Profile.new),
      ],
    );
    await container.read(sharedPreferencesProvider.future);
    final subscription = container.listen(connectionNotifierProvider, (_, __) {});
    await tester.pump();
    await tester.pump();
    repo.events.add(const Connected());
    await tester.pump();
    await tester.pump();
    final profile = await container.read(activeProfileProvider.future);
    final notifier = container.read(connectionNotifierProvider.notifier);
    final first = notifier.reconnect(profile, onlyIfProfileChanged: true);
    final second = notifier.reconnect(profile, onlyIfProfileChanged: true);
    await tester.pump();
    expect(repo.connections, 1);
    gate.complete();
    await Future.wait([first, second]);
    expect(repo.connections, 1);
    subscription.close();
    container.dispose();
    await repo.events.close();
    await tester.pump();
  });

  for (final (cancel, plainStop) in [(true, false), (false, false), (false, true)]) {
    testWidgets(
      cancel
          ? 'Manual abort cancels pending recovery'
          : 'Recovery stops after five failed attempts (plainStop=$plainStop)',
      (tester) async {
        SharedPreferences.setMockInitialValues({'started_by_user': true, 'haptic_feedback': false});
        final preferences = await SharedPreferences.getInstance();
        final repo = _Repository();
        final container = ProviderContainer(
          overrides: [
            sharedPreferencesProvider.overrideWith((ref) async => preferences),
            connectionRepositoryProvider.overrideWithValue(repo),
            activeProfileProvider.overrideWith(_Profile.new),
          ],
        );
        repo.onConnect = () => container.read(coreRestartSignalProvider.notifier).restart();
        await container.read(sharedPreferencesProvider.future);
        final subscription = container.listen(connectionNotifierProvider, (_, __) {});
        await tester.pump();
        await tester.pump();
        if (plainStop) {
          repo.events.add(const Connected());
          await tester.pump();
          await tester.pump();
        }
        repo.events.add(
          plainStop ? const Disconnected() : const Disconnected(ConnectionFailure.backgroundCoreNotAvailable()),
        );
        await tester.pump();
        await tester.pump();
        expect(container.read(recoveryStatusProvider), 1);
        if (cancel) {
          await container.read(connectionNotifierProvider.notifier).abortConnection();
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
          expect(container.read(Preferences.startedByUser), false);
          for (var i = 0; i < 3; i++) {
            repo.events.add(const Disconnected(ConnectionFailure.backgroundCoreNotAvailable()));
            await tester.pump();
          }
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
  for (final fromError in [false, true]) {
    for (final disposeWhileStopping in [false, true]) {
      testWidgets('Abort drains an in-flight recovery (error=$fromError, dispose=$disposeWhileStopping)', (
        tester,
      ) async {
        SharedPreferences.setMockInitialValues({'started_by_user': true, 'haptic_feedback': false});
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
        final subscription = container.listen(connectionNotifierProvider, (_, __) {});
        await tester.pump();
        await tester.pump();
        repo.events.add(const Disconnected(ConnectionFailure.backgroundCoreNotAvailable()));
        await tester.pump();
        await tester.pump();
        if (fromError) {
          await tester.pump(const Duration(seconds: 2));
          await tester.pump();
          expect(container.read(connectionNotifierProvider), isA<AsyncError<ConnectionStatus>>());
          expect(repo.connections, 1);
        }
        repo.fail = false;
        final gate = Completer<void>();
        repo.connectGate = gate;
        await tester.pump(Duration(seconds: fromError ? 4 : 2));
        await tester.pump();
        expect(repo.connections, fromError ? 2 : 1);
        final stopped = container.read(connectionNotifierProvider.notifier).abortConnection();
        await tester.pump();
        expect(container.read(Preferences.startedByUser), false);
        expect(repo.disconnections, 0, reason: 'Stop must wait for the native start to settle');
        if (disposeWhileStopping) {
          subscription.close();
          container.dispose();
        }
        gate.complete();
        await stopped;
        await tester.pump(const Duration(minutes: 2));
        expect(repo.disconnections, 1);
        expect(repo.connections, fromError ? 2 : 1, reason: 'Cancelled recovery must not restart');
        if (!disposeWhileStopping) {
          subscription.close();
          container.dispose();
        }
        await repo.events.close();
        await tester.pump();
        expect(tester.takeException(), isNull);
      });
    }
  }
  testWidgets('Stable duplicate status events reset recovery without postponing the timer', (tester) async {
    SharedPreferences.setMockInitialValues({'started_by_user': true, 'haptic_feedback': false});
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
    final subscription = container.listen(connectionNotifierProvider, (_, __) {});
    await tester.pump();
    await tester.pump();
    repo.events.add(const Disconnected(ConnectionFailure.backgroundCoreNotAvailable()));
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
    repo.events.add(const Disconnected(ConnectionFailure.backgroundCoreNotAvailable()));
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
  });
}
