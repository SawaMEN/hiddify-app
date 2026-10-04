import 'dart:async';
import 'dart:convert';

import 'package:fpdart/fpdart.dart';
import 'package:grpc/grpc.dart';
import 'package:hiddify/core/directories/directories_provider.dart';
import 'package:hiddify/core/model/directories.dart';
import 'package:hiddify/core/notification/in_app_notification_controller.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/core/utils/exception_handler.dart';
import 'package:hiddify/features/connection/model/connection_failure.dart';
import 'package:hiddify/features/log/model/log_level.dart' as config_log_level;
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/hiddifycore/core_interface/core_interface.dart';
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
  final CallOptions? grpcOptions = null;
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
    if (identical(Zone.current[_lifecycleZoneKey], this)) {
      return operation();
    }
    final next = _lifecycleTail.then(
      (_) => runZoned(operation, zoneValues: {_lifecycleZoneKey: this}),
    );
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
          statusController.add(const CoreStatus.stopped());
          ref.read(inAppNotificationControllerProvider).showErrorToast(e);
        })
        .map((_) {
          loggy.info('Hiddify-core setup done');
          ref.read(coreRestartSignalProvider.notifier).restart();
        })
        .run();
  }

  TaskEither<String, Unit> validateConfigByPath(String path, String tempPath, bool debug) {
    return _serialized(() async {
      Future<Either<String, Unit>> parse() async {
        try {
          final response = await core.fgClient.parse(
            ParseRequest(tempPath: tempPath, configPath: path, debug: debug),
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
        final response = await core.fgClient.parse(ParseRequest(configPath: path, debug: false));
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
      final setupResponse = await core.setup(directories, debug, 3);
      if (setupResponse.isNotEmpty) return left(setupResponse);

      await startListeningLogs('fg', core.fgClient);
      if (!core.isSingleChannel()) {
        await startListeningLogs('bg', core.bgClient);
      }
      statusController.add(currentState);
      await startListeningStatus('bg', core.bgClient);
      return right(unit);
    } catch (e, stackTrace) {
      loggy.error('core setup failed', e, stackTrace);
      return left(e.toString());
    }
  }

  TaskEither<String, Unit> changeOptions(SingboxConfigOption options) {
    return _serialized(() async {
      try {
        final request = ChangeHiddifySettingsRequest(hiddifySettingsJson: jsonEncode(options.toJson()));
        final foreground = await core.fgClient.changeHiddifySettings(request);
        if (foreground.messageType != MessageType.EMPTY) {
          return left('${foreground.messageType} ${foreground.message}');
        }

        if (!core.isSingleChannel() && await core.isBgClientAvailable()) {
          try {
            final background = await core.bgClient.changeHiddifySettings(request);
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
      statusController.add(currentState = const CoreStatus.starting());
      loggy.debug('starting');
      try {
        final background = await core.setupBackground(path, name);
        if (background != const CoreStatus.started()) {
          currentState = background;
          statusController.add(currentState);
          return left(background.getCoreAlert() ?? const ConnectionFailure.unexpected('failed to start core'));
        }

        if (!core.isSingleChannel()) {
          await startListeningLogs('bg', core.bgClient);
          await startListeningStatus('bg', core.bgClient);
        }

        final res = await core.bgClient.start(
          StartRequest(
            configPath: path,
            configName: name,
            disableMemoryLimit: disableMemoryLimit,
          ),
        );
        ref.read(coreRestartSignalProvider.notifier).restart();
        if (res.messageType != MessageType.ALREADY_STARTED && res.messageType != MessageType.EMPTY) {
          final alert = res.message.contains('denied') ? CoreAlert.requestVPNPermission : CoreAlert.startFailed;
          currentState = CoreStatus.stopped(
            alert: alert,
            message: 'failed to start core ${res.messageType} ${res.message}',
          );
          statusController.add(currentState);
          return left(
            currentState.getCoreAlert() ??
                ConnectionFailure.unexpected('failed to start core ${res.messageType} ${res.message}'),
          );
        }
        return right(unit);
      } on GrpcError catch (e, stackTrace) {
        statusController.add(currentState = const CoreStatus.stopped());
        loggy.error('failed to start background core', e, stackTrace);
        ref.read(coreRestartSignalProvider.notifier).restart();
        if (e.code == StatusCode.unavailable) {
          return left(const ConnectionFailure.unexpected('background core is not started yet!'));
        }
        return left(ConnectionFailure.unexpected(e, stackTrace));
      } catch (e, stackTrace) {
        statusController.add(currentState = const CoreStatus.stopped());
        loggy.error('failed to start core', e, stackTrace);
        return left(ConnectionFailure.unexpected(e, stackTrace));
      }
    });
  }

  TaskEither<String, Unit> stop() {
    return _serialized(() async {
      loggy.debug('stopping');
      statusController.add(currentState = const CoreStatus.stopping());

      String? backgroundStopWarning;
      try {
        final res = await core.bgClient.stop(Empty());
        if (res.messageType != MessageType.EMPTY && res.messageType != MessageType.ALREADY_STOPPED) {
          backgroundStopWarning = '${res.messageType} ${res.message}';
          loggy.warning('background stop returned $backgroundStopWarning');
        }
      } on GrpcError catch (e) {
        final ignorable = e.code == StatusCode.unavailable ||
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
          return left('native background service did not stop completely');
        }
      } catch (e, stackTrace) {
        loggy.error('failed to stop native background service', e, stackTrace);
        return left(e.toString());
      }

      await stopListenSingle('bg');
      statusController.add(currentState = const CoreStatus.stopped());
      if (backgroundStopWarning != null) {
        loggy.warning('native shutdown succeeded after background stop warning: $backgroundStopWarning');
      }
      return right(unit);
    });
  }

  TaskEither<String, Unit> restart(String path, String name, bool disableMemoryLimit) {
    return _serialized(() async {
      loggy.debug('restarting');
      try {
        final res = await core.bgClient.restart(
          StartRequest(
            configPath: path,
            configName: name,
            disableMemoryLimit: disableMemoryLimit,
            delayStart: true,
          ),
        );
        if (res.messageType != MessageType.EMPTY && res.messageType != MessageType.ALREADY_STARTED) {
          return left('${res.messageType} ${res.message}');
        }
        ref.read(coreRestartSignalProvider.notifier).restart();
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
    return TaskEither.tryCatch(
      () async {
        if (!PlatformUtils.isIOS) {
          throw UnsupportedError('reset tunnel function unavailable on platform');
        }
        if (!await core.resetTunnel()) throw StateError('failed to reset tunnel');
        return unit;
      },
      (error, _) => error.toString(),
    );
  }

  Stream<OutboundGroup?> watchGroup() async* {
    loggy.debug('watching group');
    if (!core.isInitialized()) return;
    try {
      yield* core.bgClient.outboundsInfo(Empty()).map((event) => event.items.isEmpty ? null : event.items.first);
    } catch (e, stackTrace) {
      loggy.error('error watching group', e, stackTrace);
      rethrow;
    }
  }

  Stream<List<OutboundGroup>> watchActiveGroups() async* {
    loggy.info('watching active groups');
    if (!core.isInitialized()) return;
    try {
      yield* core.bgClient.mainOutboundsInfo(Empty()).map((event) => latest = event.items.toList()).startWith(latest);
    } catch (e, stackTrace) {
      loggy.error('error watching active groups', e, stackTrace);
      rethrow;
    }
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
        final res = await core.bgClient.urlTest(UrlTestRequest(tag: tag));
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
      return right(unit);
    });
  }

  Stream<CoreStatus> watchStatus() async* {
    await startListeningStatus('bg', core.bgClient);
    yield* statusController.stream;
  }

  Future<void> startListeningStatus(String key, CoreClient cc) async {
    await listenSingle<CoreStatus>(
      '${key}StatusListener',
      () => cc
          .coreInfoListener(Empty(), options: grpcOptions)
          .doOnCancel(() {
            loggy.error('status', 'cancelled');
            if (currentState == const CoreStatus.started()) currentState = const CoreStatus.stopped();
          })
          .doOnDone(() {
            loggy.error('status', 'done');
            if (currentState == const CoreStatus.started()) currentState = const CoreStatus.stopped();
          })
          .endWith(CoreInfoResponse(coreState: CoreStates.STOPPED))
          .map((event) {
            currentState = CoreStatus.fromCoreInfo(event);
            statusController.add(currentState);
            return currentState;
          }),
      onError: (error) => loggy.error('Stream error in ${key}StatusListener: $error'),
    );
  }

  Future<void> startListeningLogs(String key, CoreClient cc) async {
    final logLevel = ref.read(ConfigOptions.logLevel);
    final coreLogLevel = getCoreLogLevel(logLevel);
    final listenKey = '${key}LogListener';
    await listenSingle<LogMessage>(listenKey, () {
      return cc.logListener(LogRequest(level: coreLogLevel), options: grpcOptions).map((event) {
        logBuffer.add(event);
        if (logBuffer.length > 300) logBuffer.removeAt(0);
        logController.add(List<LogMessage>.unmodifiable(logBuffer));
        for (final line in event.message.split('\n')) {
          loggy.log(getLogLevel(event.level), line);
        }
        return event;
      });
    });
  }

  Future<void> stopListenSingle(String key) async {
    final keysToRemove = subscriptions.entries
        .where((entry) => entry.key.startsWith(key))
        .map((entry) => entry.key)
        .toList();
    for (final subscriptionKey in keysToRemove) {
      final sub = subscriptions.remove(subscriptionKey);
      await sub?.cancel();
    }
  }

  Future<StreamSubscription<T>?> listenSingle<T>(
    String key,
    Stream<T> Function() stream, {
    Function(dynamic error)? onError,
  }) async {
    if (subscriptions.containsKey(key)) await stopListenSingle(key);
    subscriptions[key] = null;
    final subscription = stream().listen(
      (_) {},
      cancelOnError: true,
      onError: (Object error, StackTrace stackTrace) {
        loggy.log(loggyl.LogLevel.error, 'Stream error: $error');
        onError?.call(error);
        final current = subscriptions.remove(key);
        if (current != null) unawaited(current.cancel());
      },
    );
    subscriptions[key] = subscription;
    return subscription;
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
    for (final mode in [SetupMode.GRPC_NORMAL_INSECURE, SetupMode.GRPC_NORMAL]) {
      try {
        await core.fgClient.close(CloseRequest(mode: mode));
      } catch (e) {
        loggy.debug('foreground close for $mode failed: $e');
      }
    }
  }

  TaskEither<String, LANIPResponse> getLANIP() {
    return TaskEither.tryCatch(
      () => core.fgClient.getLANIP(Empty()),
      (error, stackTrace) {
        loggy.error('failed to get LAN IP', error, stackTrace);
        return error.toString();
      },
    );
  }
}
