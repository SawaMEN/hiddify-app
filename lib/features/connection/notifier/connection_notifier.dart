import 'dart:io';
import 'dart:async';

import 'package:fpdart/fpdart.dart';
import 'package:hiddify/features/connection/health/recovery_policy.dart';
import 'package:hiddify/features/connection/health/android_vpn_settings.dart';

import 'package:hiddify/core/haptic/haptic_service.dart';
import 'package:hiddify/core/utils/single_call.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/core/router/dialog/dialog_notifier.dart';
import 'package:hiddify/features/connection/data/connection_data_providers.dart';
import 'package:hiddify/features/connection/data/connection_repository.dart';
import 'package:hiddify/features/connection/model/connection_failure.dart';
import 'package:hiddify/features/connection/model/connection_status.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:in_app_review/in_app_review.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:rxdart/rxdart.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

part 'connection_notifier.g.dart';

@Riverpod(keepAlive: true)
class ConnectionNotifier extends _$ConnectionNotifier with AppLogger {
  @override
  Stream<ConnectionStatus> build() async* {
    if (Platform.isIOS) {
      final result = await _connectionRepo.setup().run();
      result.match((error) => throw error, (_) {});
    }

    ref.onDispose(_cancelRecovery);
    ref.listen(Preferences.autoReconnect, (_, enabled) {
      if (!enabled) _cancelRecovery();
    });
    listenSelf((previous, next) async {
      if (next case AsyncData(
        value: Disconnected(connectionFailure: final failure?),
      ))
        _scheduleRecovery(failure);
      if (previous?.value is Connected) {
        if (next case AsyncData(value: Disconnected(connectionFailure: null))) {
          _scheduleRecovery(
            const ConnectionFailure.backgroundCoreNotAvailable(),
          );
        }
      }
      if (next case AsyncData(value: Connected())) {
        _retryTimer?.cancel();
        _retryTimer = null;
        ref.read(recoveryStatusProvider.notifier).set(0);
        if (_stableTimer == null || !_stableTimer!.isActive) {
          _stableTimer = Timer(const Duration(minutes: 1), _policy.reset);
        }
      } else {
        _stableTimer?.cancel();
      }
      if (previous == next) return;
      if (previous case AsyncData(:final value) when !value.isConnected) {
        if (next case AsyncData(value: final Connected _)) {
          await ref.read(hapticServiceProvider.notifier).heavyImpact();

          if (!ref.mounted) return;
          if (Platform.isAndroid &&
              !ref.read(Preferences.storeReviewedByUser)) {
            if (await InAppReview.instance.isAvailable() && ref.mounted) {
              InAppReview.instance.requestReview();
              ref.read(Preferences.storeReviewedByUser.notifier).update(true);
            }
          }
        }
      }
    });

    ref.listen(activeProfileProvider.select((value) => value.asData?.value), (
      previous,
      next,
    ) async {
      if (previous == null) return;
      final shouldReconnect =
          next == null ||
          previous.id != next.id ||
          previous.lastUpdate != next.lastUpdate ||
          previous.userOverride != next.userOverride;
      if (shouldReconnect) {
        await reconnect(next);
      }
    });

    yield* _connectionRepo.watchConnectionStatus().doOnData((event) {
      loggy.info("connection status: ${event.format()}");
    });
  }

  ConnectionRepository get _connectionRepo =>
      ref.read(connectionRepositoryProvider);

  Future<void> mayConnect() async {
    if (state case AsyncData(:final value)) {
      if (value case Disconnected()) return _connect();
    }
  }

  /// Stop intent and core before snapshotting data for a separate package.
  Future<void> disconnectForMigration() async {
    _cancelRecovery();
    await ref.read(Preferences.startedByUser.notifier).update(false);
    await _disconnect();
  }

  Future<void> toggleConnection() async {
    if (_retryTimer != null || _retrying) {
      _cancelRecovery();
      await ref.read(Preferences.startedByUser.notifier).update(false);
      await _disconnect();
      return;
    }
    _cancelRecovery();
    _policy.reset();
    final haptic = ref.read(hapticServiceProvider.notifier);
    if (state case AsyncError()) {
      if (await _androidServiceRunningSafely()) {
        await haptic.mediumImpact();
        await ref.read(Preferences.startedByUser.notifier).update(false);
        await _disconnect();
      } else {
        await haptic.lightImpact();
        await ref.read(Preferences.startedByUser.notifier).update(true);
        await _connect();
      }
    } else if (state case AsyncData(:final value)) {
      switch (value) {
        case Disconnected():
          await haptic.lightImpact();
          await ref.read(Preferences.startedByUser.notifier).update(true);
          await _connect();
        case Connected() || Connecting():
          await haptic.mediumImpact();
          await ref.read(Preferences.startedByUser.notifier).update(false);
          await _disconnect();
        case Disconnecting():
          loggy.debug("disconnect is already in progress");
      }
    }
  }

  Future<bool> reconnect(ProfileEntity? profile) async {
    final epoch = _epoch;
    if (state case AsyncData(:final value) when value == const Connected()) {
      if (profile == null) {
        loggy.info("no active profile, disconnecting");
        await _disconnect();
        return false;
      }
      loggy.info("active profile changed, reconnecting");
      final result = await _serializeCoreOperation(() async {
        if (!ref.mounted ||
            epoch != _epoch ||
            !ref.read(Preferences.startedByUser))
          return right<ConnectionFailure, Unit>(unit);
        return _connectionRepo
            .reconnect(profile, ref.read(Preferences.disableMemoryLimit))
            .run();
      });
      if (!ref.mounted || epoch != _epoch) return false;
      await result.match<Future<void>>(
        (error) => _handleFailure(error, resetStartedByUser: true),
        (_) async {},
      );
      return result.isRight();
    }
    return false;
  }

  // Recreate the Android service explicitly when switching VPN/proxy mode.
  // Toggling twice depends on status stream timing and can leave it disconnected.
  Future<bool> reconnectService(ProfileEntity? profile) async {
    final epoch = _epoch;
    if (profile == null) {
      await _disconnect();
      return false;
    }
    final repository = _connectionRepo;
    final disableMemoryLimit = ref.read(Preferences.disableMemoryLimit);
    final result =
        await _serializeCoreOperation<Either<ConnectionFailure, Unit>>(
          () async {
            if (!ref.mounted ||
                epoch != _epoch ||
                !ref.read(Preferences.startedByUser))
              return right(unit);
            final stopped = await repository.disconnect().run();
            return stopped.match((error) async => stopped, (_) async {
              if (!ref.mounted ||
                  epoch != _epoch ||
                  !ref.read(Preferences.startedByUser))
                return right<ConnectionFailure, Unit>(unit);
              return repository.connect(profile, disableMemoryLimit).run();
            });
          },
        );
    if (!ref.mounted || epoch != _epoch) return false;
    await result.match<Future<void>>(
      (error) => _handleFailure(error, resetStartedByUser: true),
      (_) async {},
    );
    return result.isRight();
  }

  Future<void> abortConnection() async {
    _cancelRecovery();
    await ref.read(Preferences.startedByUser.notifier).update(false);
    if (state case AsyncData(:final value)) {
      switch (value) {
        case Connected() || Connecting():
          loggy.debug("aborting connection");
          await ref.read(Preferences.startedByUser.notifier).update(false);
          await _disconnect();
        default:
      }
    }
  }

  final _policy = RecoveryPolicy();
  Timer? _retryTimer, _stableTimer;
  bool _retrying = false;
  int _epoch = 0;
  DateTime? _lastHealthRecovery;
  void _cancelRecovery() {
    _epoch++;
    _retryTimer?.cancel();
    _retryTimer = null;
    _stableTimer?.cancel();
    _stableTimer = null;
    if (ref.mounted) ref.read(recoveryStatusProvider.notifier).set(0);
  }

  bool _scheduleRecovery(ConnectionFailure failure) {
    if (!ref.mounted ||
        !ref.read(Preferences.autoReconnect) ||
        !ref.read(Preferences.startedByUser))
      return false;
    if (failure is! UnexpectedConnectionFailure &&
        failure is! BackgroundCoreNotAvailable)
      return false;
    if (_retryTimer != null) return true;
    final delay = _policy.nextDelay();
    if (delay == null) {
      _cancelRecovery();
      unawaited(ref.read(Preferences.startedByUser.notifier).update(false));
      return false;
    }
    final epoch = _epoch;
    ref.read(recoveryStatusProvider.notifier).set(_policy.attempts);
    _retryTimer = Timer(delay, () {
      _retryTimer = null;
      unawaited(_retry(epoch));
    });
    return true;
  }

  Future<void> _retry(int epoch) async {
    _retrying = true;
    try {
      if (!ref.mounted ||
          epoch != _epoch ||
          !ref.read(Preferences.startedByUser) ||
          !ref.read(Preferences.autoReconnect))
        return;
      if (!await AndroidVpnSettings.wantsConnection()) {
        if (ref.mounted && epoch == _epoch) {
          _cancelRecovery();
          await ref.read(Preferences.startedByUser.notifier).update(false);
        }
        return;
      }
      if (await AndroidVpnSettings.serviceRunning()) {
        if (ref.mounted && epoch == _epoch)
          ref.read(recoveryStatusProvider.notifier).set(0);
        return;
      }
      final profile = await ref.read(activeProfileProvider.future);
      if (!ref.mounted || epoch != _epoch || profile == null) return;
      if (state.value?.isConnected ?? false) return;
      final result = await _serializeCoreOperation(() async {
        if (!ref.mounted ||
            epoch != _epoch ||
            !ref.read(Preferences.startedByUser) ||
            !ref.read(Preferences.autoReconnect))
          return right<ConnectionFailure, Unit>(unit);
        return _connectionRepo
            .connect(profile, ref.read(Preferences.disableMemoryLimit))
            .run();
      });
      if (!ref.mounted || epoch != _epoch) return;
      result.match((failure) {
        state = AsyncError(failure, StackTrace.current);
        _scheduleRecovery(failure);
      }, (_) {});
    } catch (error, stack) {
      if (ref.mounted && epoch == _epoch) {
        state = AsyncError(error, stack);
        _scheduleRecovery(ConnectionFailure.unexpected(error, stack));
      }
    } finally {
      _retrying = false;
    }
  }

  Future<void> recoverUnhealthyConnection() async {
    if (!ref.mounted ||
        !ref.read(Preferences.autoReconnect) ||
        !ref.read(Preferences.startedByUser) ||
        !(state.value?.isConnected ?? false))
      return;
    final now = DateTime.now();
    if (_lastHealthRecovery != null &&
        now.difference(_lastHealthRecovery!) < const Duration(minutes: 2))
      return;
    _lastHealthRecovery = now;
    final epoch = _epoch;
    final profile = await ref.read(activeProfileProvider.future);
    if (!ref.mounted || epoch != _epoch || profile == null) return;
    final result = await _serializeCoreOperation(() async {
      if (!ref.mounted ||
          epoch != _epoch ||
          !ref.read(Preferences.startedByUser))
        return right<ConnectionFailure, Unit>(unit);
      return _connectionRepo
          .reconnect(profile, ref.read(Preferences.disableMemoryLimit))
          .run();
    });
    if (ref.mounted && epoch == _epoch)
      await result.match<Future<void>>(
        (e) => _handleFailure(e, resetStartedByUser: true),
        (_) async {},
      );
  }

  final _singleStart = SingleCall();
  Future<void> _coreOperationTail = Future<void>.value();

  Future<T> _serializeCoreOperation<T>(Future<T> Function() operation) {
    final next = _coreOperationTail.then((_) => operation());
    _coreOperationTail = next.then<void>(
      (_) {},
      onError: (Object error, StackTrace stackTrace) {},
    );
    return next;
  }

  Future<void> _connect() async {
    await _singleStart.run<void>(() async {
      await _connectThrottled();
    }, onIgnored: null);
  }

  Future<void> _connectThrottled() async {
    final epoch = _epoch;
    final activeProfile = await ref.read(activeProfileProvider.future);
    if (!ref.mounted || epoch != _epoch || !ref.read(Preferences.startedByUser))
      return;
    if (activeProfile == null) {
      loggy.info("no active profile, not connecting");
      await ref.read(Preferences.startedByUser.notifier).update(false);
      return;
    }
    final result = await _serializeCoreOperation(
      () async =>
          !ref.mounted ||
              epoch != _epoch ||
              !ref.read(Preferences.startedByUser)
          ? right<ConnectionFailure, Unit>(unit)
          : await _connectionRepo
                .connect(
                  activeProfile,
                  ref.read(Preferences.disableMemoryLimit),
                )
                .run(),
    );
    if (!ref.mounted || epoch != _epoch) return;
    await result.match<Future<void>>(
      (error) => _handleFailure(error, resetStartedByUser: true),
      (_) async {},
    );
  }

  Future<bool> _androidServiceRunningSafely() async {
    if (!Platform.isAndroid) return false;
    try {
      return await AndroidVpnSettings.serviceRunning();
    } catch (error, stackTrace) {
      loggy.warning('failed to query Android service state', error, stackTrace);
      return false;
    }
  }

  Future<void> _requestNativeStop() async {
    if (!Platform.isAndroid) return;
    try {
      final stopped = await AndroidVpnSettings.stopService();
      if (!stopped) loggy.warning('Android service did not confirm the preemptive stop');
    } catch (error, stackTrace) {
      loggy.warning('failed to preempt Android service stop', error, stackTrace);
    }
  }

  Future<void> _disconnect() async {
    _cancelRecovery();
    // A connect operation can legitimately spend tens of seconds in Android permission/setup
    // while the serialized core queue is occupied. Ask the native service to stop immediately;
    // the regular queued disconnect below still performs the authoritative core cleanup.
    if (Platform.isAndroid) unawaited(_requestNativeStop());
    final result = await _serializeCoreOperation(
      () => _connectionRepo.disconnect().run(),
    );
    await result.match<Future<void>>(
      (error) => _handleFailure(error),
      (_) async {},
    );
  }

  Future<void> _handleFailure(
    ConnectionFailure error, {
    bool resetStartedByUser = false,
  }) async {
    loggy.warning("connection operation failed", error);
    if (!ref.mounted) return;
    state = AsyncError(error, StackTrace.current);
    if (resetStartedByUser && _scheduleRecovery(error)) return;
    if (resetStartedByUser) {
      await ref.read(Preferences.startedByUser.notifier).update(false);
    }
    if (error.toString().contains("panic")) {
      await Sentry.captureException(Exception(error.toString()));
    }
    if (!ref.mounted) return;
    final translations = await ref.read(translationsProvider.future);
    if (!ref.mounted) return;
    await ref
        .read(dialogNotifierProvider.notifier)
        .showCustomAlertFromErr(error.present(translations));
  }
}

@Riverpod(keepAlive: true)
bool serviceRunning(Ref ref) {
  // ref.watch(coreRestartSignalProvider);
  return ref.watch(connectionNotifierProvider).value?.isConnected ?? false;
}

final recoveryStatusProvider = NotifierProvider<RecoveryStatusNotifier, int>(
  RecoveryStatusNotifier.new,
);

class RecoveryStatusNotifier extends Notifier<int> {
  @override
  int build() => 0;
  void set(int value) => state = value;
}
