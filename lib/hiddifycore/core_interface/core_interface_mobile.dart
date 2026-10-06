import 'dart:async';
import 'dart:io';

import 'package:basic_utils/basic_utils.dart';
import 'package:flutter/services.dart';
import 'package:grpc/grpc.dart';
import 'package:hiddify/core/model/directories.dart';
import 'package:hiddify/core/utils/laststeam.dart';
import 'package:hiddify/hiddifycore/core_interface/core_interface.dart';
import 'package:hiddify/hiddifycore/core_interface/mtls_channel_cred.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore_service.pbgrpc.dart';
import 'package:hiddify/hiddifycore/generated/v2/hello/hello.pb.dart';
import 'package:hiddify/hiddifycore/generated/v2/hello/hello_service.pbgrpc.dart';
import 'package:hiddify/singbox/model/core_status.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:rxdart/rxdart.dart';

class CoreInterfaceMobile extends CoreInterface with InfraLogger {
  static const channelPrefix = "com.hiddify.app";
  static const methodChannel = MethodChannel("$channelPrefix/method");
  static const statusChannel = EventChannel("$channelPrefix/service.status", JSONMethodCodec());
  static const alertsChannel = EventChannel("$channelPrefix/service.alerts", JSONMethodCodec());

  late Uint8List serverPublicKey;
  static final cert = CryptoUtils.generateEcKeyPair();

  static const portBack = 17079;
  static const portFront = 17078;

  int _portFront = portFront;
  int _portBack = portBack;

  bool _isBgClientAvailable = false;
  bool _debug = false;
  CallOptions? _localAuthorization;
  ChannelCredentials _channelCredentials = const ChannelCredentials.insecure();
  Stream<CoreStatus> _serviceEvents = const Stream.empty();

  @override
  Stream<CoreStatus> get serviceEvents => _serviceEvents;

  LastStream<CoreStatus>? _status;
  ClientChannel? _fgChannel;
  ClientChannel? _bgChannel;
  @override
  Future<String> setup(Directories directories, bool debug, int mode) async {
    if (Platform.isAndroid) {
      final token = await methodChannel.invokeMethod<String>('get_grpc_auth_token');
      if (token == null || token.isEmpty) throw StateError('Local control authorization is missing');
      _localAuthorization = CallOptions(metadata: {'authorization': 'Bearer $token'});
      final ports = await methodChannel.invokeMapMethod<String, dynamic>('get_grpc_ports');
      final front = ports?['front'];
      final back = ports?['back'];
      if (front is! int || back is! int || front < 1 || front > 65535 || back < 1 || back > 65535 || front == back) {
        throw StateError('Local control ports are invalid');
      }
      _portFront = front;
      _portBack = back;
    }
    final channelOption = [1, 2].contains(mode)
        ? MTLSChannelCredentials(serverPublicKey: serverPublicKey, clientKey: cert)
        : const ChannelCredentials.insecure();
    _debug = debug;
    _channelCredentials = channelOption;
    final status = statusChannel.receiveBroadcastStream().map(CoreStatus.fromEvent).map((value) {
      _isBgClientAvailable = value is CoreStarted;
      return value;
    });
    final alerts = alertsChannel.receiveBroadcastStream().map(CoreStatus.fromEvent);

    await _status?.close();
    _serviceEvents = Rx.merge([status, alerts]).share();
    _status = LastStream(_serviceEvents);

    Future<void> hello() async {
      final channel = ClientChannel(
        '127.0.0.1',
        port: _portFront,
        options: ChannelOptions(credentials: channelOption),
      );
      try {
        await HelloClient(channel, options: _localAuthorization).sayHello(
          HelloRequest(name: "test"),
          options: CallOptions(timeout: const Duration(seconds: 3)),
        );
      } finally {
        await channel.shutdown();
      }
    }

    var foregroundReady = false;
    // A refused local TCP connection returns immediately on a cold launch. Avoid
    // spending the gRPC deadline probing a server that has not been created yet.
    if (await isPortOpen('127.0.0.1', _portFront)) {
      try {
        await hello();
        foregroundReady = true;
        loggy.info("core is already started!");
      } catch (e) {
        loggy.debug("foreground core handshake failed: $e");
      }
    }
    if (!foregroundReady) {
      await methodChannel
          .invokeMethod("setup", {
            "baseDir": directories.baseDir.path,
            "workingDir": directories.workingDir.path,
            "tempDir": directories.tempDir.path,
            "grpcPort": _portFront,
            "mode": mode,
            "debug": debug,
          })
          .timeout(const Duration(seconds: 10));
      // Use a fresh channel: a failed pre-setup handshake may be in reconnect backoff.
      await hello();
    }

    // Control channels carry indefinite event RPCs. Graceful shutdown waits for
    // those streams forever; replacing or disposing a channel must cancel them.
    await _fgChannel?.terminate();
    await _bgChannel?.terminate();
    _fgChannel = ClientChannel(
      '127.0.0.1',
      port: _portFront,
      options: ChannelOptions(credentials: channelOption),
    );
    _bgChannel = ClientChannel(
      '127.0.0.1',
      port: _portBack,
      options: ChannelOptions(credentials: channelOption),
    );
    fgClient = CoreClient(_fgChannel!, options: _localAuthorization);
    bgClient = CoreClient(_bgChannel!, options: _localAuthorization);
    return "";
  }

  @override
  Future<CoreStatus> setupBackground(String path, String name) async {
    // if (!await waitUntilPort(_portBack, false, stop)) return const CoreStatus.stopped(alert: CoreAlert.createService);
    if (!await stop(preserveIntent: true)) return const CoreStatus.stopped(alert: CoreAlert.createService);
    final status = _status;
    if (status == null) {
      return const CoreStatus.stopped(alert: CoreAlert.createService, message: "core is not initialized");
    }
    status.clean();
    final bool? started;
    try {
      started = await methodChannel
          .invokeMethod<bool>("start", {
            "path": path,
            "name": name,
            "grpcPort": _portBack,
            "startBg": true,
            "debug": _debug,
          })
          .timeout(const Duration(seconds: 40));
    } on PlatformException catch (e) {
      final failure = CoreStatus.fromEvent({
        "status": "Stopped",
        "alert": e.code,
        "message": e.message ?? "native service startup failed",
      });
      if (failure case CoreStopped(alert: CoreAlert.unknown)) {
        return CoreStatus.stopped(
          alert: CoreAlert.startService,
          message: "${e.code}: ${e.message ?? 'startup failed'}",
        );
      }
      return failure;
    }
    if (started != true) {
      return const CoreStatus.stopped(alert: CoreAlert.startService, message: "native service did not start");
    }

    // Android's method result already waits for Started and carries native alerts.
    // Waiting on the merged event stream again can miss Started and cost 20 seconds.
    if (!Platform.isAndroid) {
      loggy.info("Waiting for starting core");
      waitingForService:
      for (var i = 0; i < 20; i++) {
        try {
          final res = await status.get(timeout: const Duration(seconds: 1));

          switch (res) {
            case CoreStarted():
              break waitingForService;
            case CoreStopped():
              if (res.alert != null) {
                return res;
              }

            case CoreStopping():
            // return res;
            case CoreStarting():
          }
          status.clean();
        } on TimeoutException {
          // just retry
        }
      }
      loggy.info("Waiting for starting core finished");
    }

    if (!await waitUntilPort(_portBack, true, null, maxTry: 10)) {
      await stopMethodChannel(preserveIntent: true);
      return const CoreStatus.stopped(
        alert: CoreAlert.startService,
        message: "background core did not open its local control port",
      );
    }
    // Discard the channel that probed a stopped/previous service. Its connection
    // can still be in gRPC backoff or refer to the server we just shut down.
    // Terminate event RPCs instead of waiting for indefinite streams to finish.
    await _bgChannel?.terminate();
    _bgChannel = ClientChannel(
      '127.0.0.1',
      port: _portBack,
      options: ChannelOptions(credentials: _channelCredentials),
    );
    bgClient = CoreClient(_bgChannel!, options: _localAuthorization);
    _isBgClientAvailable = true;
    return const CoreStarted();
  }

  @override
  Future<bool> stop({bool preserveIntent = false}) async {
    final nativeStopped = await stopMethodChannel(preserveIntent: preserveIntent);
    if (!nativeStopped) return false;
    if (!await waitUntilPort(_portBack, false, null, maxTry: 10)) {
      return false;
    }

    _isBgClientAvailable = false;
    return true;
  }

  Future<bool> stopMethodChannel({bool preserveIntent = false}) async {
    return await methodChannel
            .invokeMethod<bool>('stop', {'preserveIntent': preserveIntent})
            .timeout(const Duration(seconds: 25)) ==
        true;
  }

  @override
  Future<bool> isBgClientAvailable() async {
    return _isBgClientAvailable;
  }

  @override
  Future<void> dispose() async {
    await _status?.close();
    _status = null;
    await _fgChannel?.terminate();
    await _bgChannel?.terminate();
    _fgChannel = null;
    _bgChannel = null;
  }

  @override
  Future<bool> resetTunnel() async {
    await methodChannel.invokeMethod("reset");
    return true;
  }

  @override
  Future<bool> isActiveFg() async {
    return await isPortOpen("127.0.0.1", _portFront);
  }

  @override
  Future<bool> isActiveBg() async {
    return await isPortOpen("127.0.0.1", _portBack);
  }
}

Future<bool> waitUntilPort(
  int portNumber,
  bool isOpen,
  Future Function()? callFunctionAfterEachFail, {
  int maxTry = 10,
}) async {
  for (var i = 0; i < maxTry; i++) {
    if (await isPortOpen("127.0.0.1", portNumber) == isOpen) {
      return true;
    }
    if (callFunctionAfterEachFail != null) {
      await callFunctionAfterEachFail();
    }

    await Future.delayed(const Duration(milliseconds: 200));
  }
  return false;
}

Future<bool> isPortOpen(String host, int port, {Duration timeout = const Duration(milliseconds: 300)}) async {
  try {
    final socket = await Socket.connect(host, port, timeout: timeout);
    await socket.close();
    return true;
  } on SocketException catch (_) {
    return false;
  } catch (_) {
    return false;
  }
}
