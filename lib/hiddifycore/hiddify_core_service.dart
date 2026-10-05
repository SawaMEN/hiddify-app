import 'package:flutter/services.dart';

import 'dart:async';
import 'dart:convert';

import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_policy.dart';

import 'package:fpdart/fpdart.dart';
import 'package:grpc/grpc.dart';
import 'package:hiddify/core/directories/directories_provider.dart';
import 'package:hiddify/core/notification/in_app_notification_controller.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/features/connection/model/connection_failure.dart';
import 'package:hiddify/features/connection/health/recovery_policy.dart';
import 'package:hiddify/features/log/model/log_level.dart' as config_log_level;
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/hiddifycore/core_interface/core_interface_wrapper_stub.dart'
    if (dart.library.io) 'package:hiddify/hiddifycore/core_interface/core_interface_wrapper.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcommon/common.pb.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore.pb.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore_service.pbgrpc.dart';
import 'package:hiddify/hiddifycore/init_signal.dart';
import 'package:hiddify/singbox/model/core_status.dart';
import 'package:hiddify/singbox/model/singbox_config_option.dart';
import 'package:hiddify/utils/custom_loggers.dart';
import 'package:hiddify/utils/platform_utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:loggy/loggy.dart' as loggyl;
import 'package:rxdart/rxdart.dart';

class HiddifyCoreService with InfraLogger {
  HiddifyCoreService(this.ref);

  final Ref ref;
  final core = getCoreInterface();

  CoreStatus currentState = const CoreStatus.stopped();
  final statusController = BehaviorSubject<CoreStatus>();
  final logController = BehaviorSubject<List<LogMessage>>();
  // Unary deadlines must not be applied to long-lived event streams.
  final CallOptions unaryOptions = CallOptions(timeout: const Duration(seconds: 15));
  final CallOptions parseOptions = CallOptions(timeout: const Duration(seconds: 45));
  final CallOptions? grpcOptions = null;
  bool _disposed = false;
  Future<void> _subscriptionTail = Future<void>.value();
  final Map<String, Object> _subscriptionTokens = {};
  final Map<String, Timer> _reconnectTimers = {};
  final Map<String, RecoveryPolicy> _reconnectPolicies = {};
  final Map<String, StreamSubscription?> subscriptions = {};
  List<OutboundGroup> latest = [];
  List<LogMessage> logBuffer = [];

  static final Object _lifecycleZoneKey = Object();
  Future<void> _lifecycleTail = Future<void>.value();
  Future<Either<String, Unit>>? _setupPending;

  Future<T> _enqueueLifecycle<T>(Future<T> Function() operation) {
    // Higher-level operations (connect, profile validation) can hold the same queue while
    // invoking individual core methods. A Zone marker makes the queue safely reentrant and
    // avoids self-deadlocks from nested setup/changeOptions/start calls.
    if (_disposed) return Future.error(StateError("core service disposed"));
    if (identical(Zone.current[_lifecycleZoneKey], this)) {
      return operation();
    }
    final next = _lifecycleTail.then((_) {
      if (_disposed) throw StateError("core service disposed");
      return runZoned(operation, zoneValues: {_lifecycleZoneKey: this});
    });
    _lifecycleTail = next.then<void>((_) {}, onError: (Object error, StackTrace stackTrace) {});
    return next;
  }

  Future<T> runExclusive<T>(Future<T> Function() operation) => _enqueueLifecycle(operation);

  TaskEither<L, R> _serialized<L, R>(Future<Either<L, R>> Function() operation) =>
      TaskEither(() => _enqueueLifecycle(operation));

  Future<void> init() async {
    await setup()
        .mapLeft((e) {
          loggy.error(e);
          if (PlatformUtils.isIOS) return;
          _publishStatus(const CoreStatus.stopped());
          if (ref.mounted) ref.read(inAppNotificationControllerProvider).showErrorToast(e);
        })
        .map((_) {
          loggy.info('Hiddify-core setup done');
          if (ref.mounted) ref.read(coreRestartSignalProvider.notifier).restart();
        })
        .run();
  }

  TaskEither<String, Unit> validateConfigByPath(String path, String tempPath, bool debug) {
    return _serialized(() async {
      Future<Either<String, Unit>> parse() async {
        try {
          final response = await core.fgClient.parse(
            ParseRequest(tempPath: tempPath, configPath: path, debug: debug),
            options: parseOptions,
          );
          if (response.responseCode != ResponseCode.OK) {
            return left('${response.responseCode} ${response.message}');
          }
          return right(unit);
        } catch (e) {
          return left(e.toString());
        }
      }

      final first = await parse();
      if (first.isRight()) return first;

      // We already own the lifecycle queue here, so call the internal setup implementation
      // directly instead of enqueueing another operation.
      final setupResult = await _setupInternal();
      final setupError = setupResult.match<String?>((error) => error, (_) => null);
      if (setupError != null) return left(setupError);
      return parse();
    });
  }

  TaskEither<String, String> generateFullConfigByPath(String path) {
    return _serialized(() async {
      try {
        final response = await core.fgClient.parse(ParseRequest(tempPath: path, debug: false), options: parseOptions);
        if (response.responseCode != ResponseCode.OK) {
          return left('${response.responseCode} ${response.message}');
        }
        return right(response.content);
      } catch (e, stackTrace) {
        loggy.error('failed to generate full config', e, stackTrace);
        return left(e.toString());
      }
    });
  }

  TaskEither<String, Unit> setup() => TaskEither(() async {
    // A setup queued by init() may be waiting behind the connection operation
    // that owns this Zone. Awaiting it here would deadlock the lifecycle queue.
    if (identical(Zone.current[_lifecycleZoneKey], this)) {
      return _setupInternal();
    }
    final pending = _setupPending;
    if (pending != null) return pending;

    final operation = _enqueueLifecycle(_setupInternal);
    _setupPending = operation;
    try {
      return await operation;
    } finally {
      if (identical(_setupPending, operation)) _setupPending = null;
    }
  });

  Future<Either<String, Unit>> _setupInternal() async {
    try {
      final directories = ref.read(appDirectoriesProvider).requireValue;
      final debug = ref.read(debugModeNotifierProvider);
      await stopListenSingle('fg');
      await stopListenSingle('bg');
      await stopListenSingle('native');
      final setupResponse = await core.setup(directories, debug, 3);
      if (setupResponse.isNotEmpty) return left(setupResponse);

      await startListeningLogs('fg', core.fgClient);
      await listenSingle<CoreStatus>(
        'nativeStatusListener',
        () => core.serviceEvents,
        onData: (event) {
          if (event is CoreStopped || event is CoreStopping) {
            currentState = event;
            _publishStatus(event);
          }
        },
      );
      // Mobile creates the background server only when the service starts.
      // Connecting before then poisons the channel with reconnect backoff.
      if (core.isSingleChannel() || await core.isBgClientAvailable()) {
        await startListeningLogs('bg', core.bgClient);
        await startListeningStatus('bg', core.bgClient);
      }
      _publishStatus(currentState);
      return right(unit);
    } catch (e, stackTrace) {
      loggy.error('core setup failed', e, stackTrace);
      return left(e.toString());
    }
  }

  TaskEither<String, Unit> changeOptions(SingboxConfigOption options) {
    return _serialized(() async {
      try {
        final json = applyVpnPrivacyPolicy(
          options.toJson(),
          android: PlatformUtils.isAndroid,
          fullTunnel: ref.read(VpnPrivacyPreferences.fullTunnel),
          hideLocalProxy: ref.read(VpnPrivacyPreferences.hideLocalProxy),
          hideClashApi: ref.read(VpnPrivacyPreferences.hideClashApi),
          disableSystemProxy: ref.read(VpnPrivacyPreferences.disableSystemProxy),
          encryptedDns: ref.read(VpnPrivacyPreferences.encryptedDns),
        );
        if (PlatformUtils.isAndroid) {
          const channel = MethodChannel('com.hiddify.app/method');
          final regional = await channel.invokeMapMethod<String, dynamic>('get_regional_routing', {
            'region': json['region'],
          });
          if (regional != null) json.addAll(regional);
        }
        if (!options.enableMixedPort) json['mixed-port'] = 0;
        if (!options.enableDirectPort) json['direct-port'] = 0;
        if (!options.enableTproxyPort) json['tproxy-port'] = 0;
        if (!options.enableRedirectPort) json['redirect-port'] = 0;
        if (PlatformUtils.isAndroid) {
          await const MethodChannel('com.hiddify.app/method')
              .invokeMethod<void>('save_privacy_core_options', {'json': jsonEncode(json)});
        }
        final request = ChangeHiddifySettingsRequest(hiddifySettingsJson: jsonEncode(json));
        final foreground = await core.fgClient.changeHiddifySettings(request, options: unaryOptions);
        if (foreground.messageType != MessageType.EMPTY) {
          return left('${foreground.messageType} ${foreground.message}');
        }

        if (!core.isSingleChannel() && await core.isBgClientAvailable()) {
          try {
            final background = await core.bgClient.changeHiddifySettings(request, options: unaryOptions);
            if (background.messageType != MessageType.EMPTY) {
              return left('${background.messageType} ${background.message}');
            }
          } on GrpcError catch (e) {
            if (e.code != StatusCode.unavailable) rethrow;
            loggy.debug('background core is not started yet: $e');
          }
        }
        return right(unit);
      } catch (e, stackTrace) {
        loggy.error('failed to change core options', e, stackTrace);
        return left(e.toString());
      }
    });
  }

  TaskEither<ConnectionFailure, Unit> start(String path, String name, bool disableMemoryLimit) {
    return _serialized(() async {
      _publishStatus(currentState = const CoreStatus.starting());
      loggy.debug('starting');
      try {
        final background = await core.setupBackground(path, name);
        if (background != const CoreStatus.started()) {
          await _cleanupFailedStart();
          _publishStatus(currentState = background);
          return left(background.getCoreAlert() ?? const ConnectionFailure.unexpected('failed to start core'));
        }

        if (!core.isSingleChannel()) {
          await startListeningLogs('bg', core.bgClient);
          await startListeningStatus('bg', core.bgClient);
        }

        final res = await core.bgClient.start(
          StartRequest(configPath: path, configName: name, disableMemoryLimit: disableMemoryLimit),
          options: parseOptions,
        );
        if (res.messageType != MessageType.ALREADY_STARTED && res.messageType != MessageType.EMPTY) {
          final alert = res.message.contains('denied') ? CoreAlert.requestVPNPermission : CoreAlert.startFailed;
          final failureStatus = CoreStatus.stopped(
            alert: alert,
            message: 'failed to start core ${res.messageType} ${res.message}',
          );
          await _cleanupFailedStart();
          _publishStatus(currentState = failureStatus);
          return left(
            failureStatus.getCoreAlert() ??
                ConnectionFailure.unexpected('failed to start core ${res.messageType} ${res.message}'),
          );
        }
        _publishStatus(currentState = const CoreStatus.started());
        if (ref.mounted) ref.read(coreRestartSignalProvider.notifier).restart();
        return right(unit);
      } on GrpcError catch (e, stackTrace) {
        await _cleanupFailedStart();
        _publishStatus(currentState = const CoreStatus.stopped());
        loggy.error('failed to start background core', e, stackTrace);
        if (e.code == StatusCode.unavailable) {
          return left(const ConnectionFailure.unexpected('background core is not started yet!'));
        }
        return left(ConnectionFailure.unexpected(e, stackTrace));
      } catch (e, stackTrace) {
        await _cleanupFailedStart();
        _publishStatus(currentState = const CoreStatus.stopped());
        loggy.error('failed to start core', e, stackTrace);
        return left(ConnectionFailure.unexpected(e, stackTrace));
      }
    });
  }

  Future<void> _cleanupFailedStart() async {
    try {
      await core.stop();
    } catch (error, stack) {
      loggy.warning('Failed-start cleanup failed', error, stack);
    }
    await stopListenSingle('bg');
    latest = [];
  }

  TaskEither<String, Unit> stop() {
    return _serialized(() async {
      loggy.debug('stopping');
      final beforeStop = currentState;
      _publishStatus(currentState = const CoreStatus.stopping());

      String? backgroundStopWarning;
      try {
        final res = await core.bgClient.stop(Empty(), options: unaryOptions);
        if (res.messageType != MessageType.EMPTY && res.messageType != MessageType.ALREADY_STOPPED) {
          backgroundStopWarning = '${res.messageType} ${res.message}';
          loggy.warning('background stop returned $backgroundStopWarning');
        }
      } on GrpcError catch (e) {
        final ignorable =
            e.code == StatusCode.unavailable ||
            (e.code == StatusCode.unknown && (e.message?.contains('HTTP/2') ?? false));
        if (!ignorable) {
          backgroundStopWarning = e.message ?? 'failed to stop core: $e';
          loggy.warning('background stop RPC failed, forcing native shutdown: $e');
        }
      } catch (e, stackTrace) {
        backgroundStopWarning = e.toString();
        loggy.warning('background stop request failed, forcing native shutdown', e, stackTrace);
      }

      try {
        final stopped = await core.stop();
        if (!stopped) {
          _publishStatus(currentState = beforeStop);
          return left('native background service did not stop completely');
        }
      } catch (e, stackTrace) {
        loggy.error('failed to stop native background service', e, stackTrace);
        _publishStatus(currentState = beforeStop);
        return left(e.toString());
      }

      await stopListenSingle('bg');
      latest = [];
      _publishStatus(currentState = const CoreStatus.stopped());
      if (backgroundStopWarning != null) {
        loggy.warning('native shutdown succeeded after background stop warning: $backgroundStopWarning');
      }
      return right(unit);
    });
  }

  TaskEither<String, Unit> restart(String path, String name, bool disableMemoryLimit) {
    return _serialized(() async {
      loggy.debug('restarting');
      latest = [];
      try {
        final res = await core.bgClient.restart(
          StartRequest(configPath: path, configName: name, disableMemoryLimit: disableMemoryLimit, delayStart: true),
          options: parseOptions,
        );
        if (res.messageType != MessageType.EMPTY && res.messageType != MessageType.ALREADY_STARTED) {
          return left('${res.messageType} ${res.message}');
        }
        if (ref.mounted) ref.read(coreRestartSignalProvider.notifier).restart();
        return right(unit);
      } on GrpcError catch (e, stackTrace) {
        loggy.error('failed to restart background core', e, stackTrace);
        return left(e.message ?? 'gRPC restart failed: ${e.codeName}');
      } catch (e, stackTrace) {
        loggy.error('failed to restart core', e, stackTrace);
        return left(e.toString());
      }
    });
  }

  TaskEither<String, Unit> resetTunnel() {
    return TaskEither.tryCatch(() async {
      if (!PlatformUtils.isIOS) {
        throw UnsupportedError('reset tunnel function unavailable on platform');
      }
      if (!await core.resetTunnel()) throw StateError('failed to reset tunnel');
      return unit;
    }, (error, _) => error.toString());
  }

  Stream<OutboundGroup?> watchGroup() async* {
    loggy.debug('watching group');
    if (!core.isInitialized()) return;
    try {
      yield* _recoveringStream(() => core.bgClient.outboundsInfo(Empty()))
          .map((event) => event.items.isEmpty ? null : event.items.first);
    } catch (e, stackTrace) {
      loggy.error('error watching group', e, stackTrace);
      rethrow;
    }
  }

  Stream<List<OutboundGroup>> watchActiveGroups() async* {
    loggy.info('watching active groups');
    if (!core.isInitialized()) return;
    try {
      yield* _recoveringStream(() => core.bgClient.mainOutboundsInfo(Empty()))
          .map((event) => latest = event.items.toList())
          .startWith(latest);
    } catch (e, stackTrace) {
      loggy.error('error watching active groups', e, stackTrace);
      rethrow;
    }
  }

  Stream<T> _recoveringStream<T>(Stream<T> Function() factory) {
    var failures = 0;
    return Rx.retryWhen<T>(() => factory().doOnData((_) => failures = 0), (error, stackTrace) {
      if (_disposed ||
          currentState is CoreStopped ||
          ++failures > 5 ||
          error is! GrpcError ||
          ![StatusCode.unavailable, StatusCode.deadlineExceeded, StatusCode.unknown].contains(error.code)) {
        return Stream<void>.error(error, stackTrace);
      }
      return Stream<void>.fromFuture(Future<void>.delayed(Duration(milliseconds: 250 * failures)));
    });
  }

  ResponseStream<SystemInfo> watchStats() {
    loggy.debug('watching stats');
    return core.bgClient.getSystemInfoStream(Empty());
  }

  TaskEither<String, Unit> selectOutbound(String groupTag, String outboundTag) {
    return TaskEither.tryCatch(
      () async {
        final res = await core.bgClient.selectOutbound(
          SelectOutboundRequest(groupTag: groupTag, outboundTag: outboundTag),
          options: CallOptions(timeout: const Duration(seconds: 1)),
        );
        if (res.code != ResponseCode.OK) throw StateError('${res.code} ${res.message}');
        return unit;
      },
      (error, stackTrace) {
        loggy.error('error selecting outbound', error, stackTrace);
        return error.toString();
      },
    );
  }

  TaskEither<String, Unit> urlTest(String tag) {
    return TaskEither.tryCatch(
      () async {
        final res = await core.bgClient.urlTest(UrlTestRequest(tag: tag), options: parseOptions);
        if (res.code != ResponseCode.OK) throw StateError('${res.code} ${res.message}');
        return unit;
      },
      (error, stackTrace) {
        loggy.error('error in url test', error, stackTrace);
        return error.toString();
      },
    );
  }

  Stream<List<LogMessage>> watchLogs(String path) async* {
    if (!core.isInitialized()) return;
    await startListeningLogs('bg', core.bgClient);
    await startListeningLogs('fg', core.fgClient);
    yield* logController.stream;
  }

  TaskEither<String, Unit> clearLogs() {
    return TaskEither(() async {
      logBuffer.clear();
      if (!logController.isClosed) logController.add(const []);
      return right(unit);
    });
  }

  /// Query the daemon rather than trusting the Android service wrapper's state.
  Future<bool> backgroundCoreRunning() async {
    try {
      final status = await core.bgClient
          .coreInfoListener(Empty(), options: CallOptions(timeout: const Duration(seconds: 4)))
          .map(CoreStatus.fromCoreInfo)
          .first
          .timeout(const Duration(seconds: 4));
      currentState = status;
      _publishStatus(status);
      return status is CoreStarted || status is CoreStarting;
    } catch (error) {
      loggy.warning('Background core did not answer: $error');
      return false;
    }
  }

  Stream<CoreStatus> watchStatus() async* {
    if (core.isInitialized() && (core.isSingleChannel() || await core.isBgClientAvailable())) {
      await startListeningStatus('bg', core.bgClient);
    }
    yield* statusController.stream;
  }

  void _publishStatus(CoreStatus status) {
    if (status is CoreStopped) latest = [];
    if (!_disposed && !statusController.isClosed) statusController.add(status);
  }

  Future<void> startListeningStatus(String key, CoreClient cc) async {
    await listenSingle<CoreStatus>(
      '${key}StatusListener',
      () => cc.coreInfoListener(Empty()).map(CoreStatus.fromCoreInfo),
      onData: (event) {
        currentState = event;
        _publishStatus(event);
      },
      onError: (error) {
        loggy.warning('Status connection lost: $error');
        // Android's native status channel reports actual service shutdowns.
        // A broken UI control stream must not tear down a running VPN.
        if (!PlatformUtils.isAndroid) {
          currentState = const CoreStatus.stopped();
          _publishStatus(currentState);
        }
      },
      reconnect: true,
    );
  }

  Future<void> startListeningLogs(String key, CoreClient cc) async {
    if (_disposed || !ref.mounted) return;
    final coreLogLevel = getCoreLogLevel(ref.read(ConfigOptions.logLevel));
    await listenSingle<LogMessage>(
      '${key}LogListener',
      () => cc.logListener(LogRequest(level: coreLogLevel)),
      onData: (event) {
        logBuffer.add(event);
        if (logBuffer.length > 300) logBuffer.removeAt(0);
        if (!logController.isClosed) logController.add(List<LogMessage>.unmodifiable(logBuffer));
        for (final line in event.message.split('\n')) {
          loggy.log(getLogLevel(event.level), line);
        }
      },
      reconnect: true,
    );
  }

  Future<T> _withSubscriptionLock<T>(Future<T> Function() operation) {
    final next = _subscriptionTail.then((_) => operation());
    _subscriptionTail = next.then<void>((_) {}, onError: (Object e, StackTrace st) {});
    return next;
  }

  Future<void> stopListenSingle(String prefix) => _withSubscriptionLock(() async {
    final keys = {
      ...subscriptions.keys,
      ..._subscriptionTokens.keys,
      ..._reconnectTimers.keys,
      ..._reconnectPolicies.keys,
    }.where((key) => key.startsWith(prefix)).toList();
    for (final key in keys) {
      _subscriptionTokens.remove(key); // invalidate callbacks before awaiting cancellation
      _reconnectTimers.remove(key)?.cancel();
      _reconnectPolicies.remove(key);
      await subscriptions.remove(key)?.cancel();
    }
  });

  Future<StreamSubscription<T>?> listenSingle<T>(
    String key,
    Stream<T> Function() stream, {
    void Function(T event)? onData,
    void Function(dynamic error)? onError,
    bool reconnect = false,
  }) => _withSubscriptionLock(() async {
    if (_disposed) return null;
    _subscriptionTokens.remove(key);
    _reconnectTimers.remove(key)?.cancel();
    await subscriptions.remove(key)?.cancel();
    if (_disposed) return null;
    final token = Object();
    _subscriptionTokens[key] = token;
    bool isCurrent() => !_disposed && identical(_subscriptionTokens[key], token);
    var terminated = false;
    final subscribedAt = DateTime.now();
    void ended(Object error) {
      if (!isCurrent() || terminated) return;
      terminated = true;
      final old = subscriptions.remove(key);
      if (old != null) unawaited(old.cancel());
      onError?.call(error);
      if (reconnect) {
        final policy = _reconnectPolicies.putIfAbsent(key, RecoveryPolicy.new);
        if (DateTime.now().difference(subscribedAt) >= const Duration(minutes: 1)) {
          policy.reset();
        }
        final delay = policy.nextDelay();
        if (delay == null) {
          _subscriptionTokens.remove(key);
          loggy.warning('Unable to restore $key after five retries: $error');
          return;
        }
        _reconnectTimers[key] = Timer(delay, () {
          _reconnectTimers.remove(key);
          if (isCurrent()) {
            unawaited(
              listenSingle<T>(key, stream, onData: onData, onError: onError, reconnect: true).catchError((
                Object e,
                StackTrace st,
              ) {
                loggy.warning('Unable to restore $key', e, st);
                return null;
              }),
            );
          }
        });
      } else {
        _subscriptionTokens.remove(key);
      }
    }

    try {
      final subscription = stream().listen(
        (event) {
          if (isCurrent() && !terminated) {
            onData?.call(event);
          }
        },
        cancelOnError: true,
        onError: (Object error, StackTrace st) => ended(error),
        onDone: () => ended(StateError('$key closed')),
      );
      if (!terminated) subscriptions[key] = subscription;
      return subscription;
    } catch (e) {
      ended(e);
      return null;
    }
  });

  Future<void> dispose() async {
    if (_disposed) return;
    _disposed = true;
    await _lifecycleTail;
    await stopListenSingle('');
    await core.dispose();
    await statusController.close();
    await logController.close();
  }

  loggyl.LogLevel getLogLevel(LogLevel level) {
    return switch (level) {
      LogLevel.DEBUG => loggyl.LogLevel.debug,
      LogLevel.INFO => loggyl.LogLevel.info,
      LogLevel.WARNING => loggyl.LogLevel.warning,
      LogLevel.ERROR || LogLevel.FATAL => loggyl.LogLevel.error,
      _ => loggyl.LogLevel.info,
    };
  }

  LogLevel getCoreLogLevel(config_log_level.LogLevel level) {
    return switch (level) {
      config_log_level.LogLevel.trace => LogLevel.TRACE,
      config_log_level.LogLevel.debug => LogLevel.DEBUG,
      config_log_level.LogLevel.info => LogLevel.INFO,
      config_log_level.LogLevel.warn => LogLevel.WARNING,
      config_log_level.LogLevel.error => LogLevel.ERROR,
      config_log_level.LogLevel.fatal || config_log_level.LogLevel.panic => LogLevel.FATAL,
      _ => LogLevel.INFO,
    };
  }

  Future<void> closeFront() => _enqueueLifecycle(_closeFront);

  Future<void> _closeFront() async {
    if (!core.isInitialized() || core.isSingleChannel()) return;
    await stopListenSingle('fg');
    await stopListenSingle('bg');
    // Native Close(mode) also stops the shared VPN instance on Android.
    // Keep servers alive and release only UI subscriptions while paused.
    if (PlatformUtils.isAndroid) return;
    for (final mode in [SetupMode.GRPC_NORMAL_INSECURE, SetupMode.GRPC_NORMAL]) {
      try {
        await core.fgClient.close(CloseRequest(mode: mode), options: unaryOptions);
      } catch (e) {
        loggy.debug('foreground close for $mode failed: $e');
      }
    }
  }

  TaskEither<String, LANIPResponse> getLANIP() {
    return TaskEither.tryCatch(() => core.fgClient.getLANIP(Empty(), options: unaryOptions), (error, stackTrace) {
      loggy.error('failed to get LAN IP', error, stackTrace);
      return error.toString();
    });
  }
}
