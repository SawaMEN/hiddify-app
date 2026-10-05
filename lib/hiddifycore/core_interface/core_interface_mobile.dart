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

  bool _isBgClientAvailable = false;
  bool _debug = false;

  LastStream<CoreStatus>? _status;
  ClientChannel? _fgChannel;
  ClientChannel? _bgChannel;
  @override
  Future<String> setup(Directories directories, bool debug, int mode) async {
    final channelOption = [1, 2].contains(mode)
        ? MTLSChannelCredentials(serverPublicKey: serverPublicKey, clientKey: cert)
        : const ChannelCredentials.insecure();
    _debug = debug;
    final helloChannel = ClientChannel(
      '127.0.0.1',
      port: portFront,
      options: ChannelOptions(credentials: channelOption),
    );
    final helloClient = HelloClient(helloChannel);
    final helloOptions = CallOptions(timeout: const Duration(seconds: 3));
    final status = statusChannel.receiveBroadcastStream().map(CoreStatus.fromEvent).map((value) {
      _isBgClientAvailable = value is CoreStarted;
      return value;
    });
    final alerts = alertsChannel.receiveBroadcastStream().map(CoreStatus.fromEvent);

    await _status?.close();
    _status = LastStream(Rx.merge([status, alerts]));
    try {
      await helloClient.sayHello(HelloRequest(name: "test"), options: helloOptions);
      loggy.info("core is already started!");
    } catch (e) {
      //core is not started yet

      await methodChannel
          .invokeMethod("setup", {
            "baseDir": directories.baseDir.path,
            "workingDir": directories.workingDir.path,
            "tempDir": directories.tempDir.path,
            "grpcPort": portFront,
            "mode": mode,
            "debug": debug,
          })
          .timeout(const Duration(seconds: 10));
      final res = await helloClient.sayHello(HelloRequest(name: "test"), options: helloOptions);
      loggy.info(res.toString());
    } finally {
      await helloChannel.shutdown();
    }

    await _fgChannel?.shutdown();
    await _bgChannel?.shutdown();
    _fgChannel = ClientChannel(
      '127.0.0.1',
      port: portFront,
      options: ChannelOptions(credentials: channelOption),
    );
    _bgChannel = ClientChannel(
      '127.0.0.1',
      port: portBack,
      options: ChannelOptions(credentials: channelOption),
    );
    fgClient = CoreClient(_fgChannel!);
    bgClient = CoreClient(_bgChannel!);
    return "";
  }

  @override
  Future<CoreStatus> setupBackground(String path, String name) async {
    // if (!await waitUntilPort(portBack, false, stop)) return const CoreStatus.stopped(alert: CoreAlert.createService);
    if (!await stop()) return const CoreStatus.stopped(alert: CoreAlert.createService);
    final status = _status;
    if (status == null) {
      return const CoreStatus.stopped(alert: CoreAlert.createService, message: "core is not initialized");
    }
    status.clean();
    final started = await methodChannel
        .invokeMethod<bool>("start", {
          "path": path,
          "name": name,
          "grpcPort": portBack,
          "startBg": true,
          "debug": _debug,
        })
        .timeout(const Duration(seconds: 35));
    if (started != true) return const CoreStatus.stopped(alert: CoreAlert.startService);

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

    if (!await waitUntilPort(portBack, true, null, maxTry: 10)) {
      await stopMethodChannel();
      return const CoreStatus.stopped(alert: CoreAlert.startService, message: "starting background core...");
    }
    _isBgClientAvailable = true;
    return const CoreStarted();
  }

  @override
  Future<bool> stop() async {
    final nativeStopped = await stopMethodChannel();
    if (!nativeStopped) return false;
    if (!await waitUntilPort(portBack, false, null, maxTry: 10)) {
      return false;
    }

    _isBgClientAvailable = false;
    return true;
  }

  Future<bool> stopMethodChannel() async {
    return await methodChannel.invokeMethod<bool>('stop').timeout(const Duration(seconds: 25)) == true;
  }

  @override
  Future<bool> isBgClientAvailable() async {
    return _isBgClientAvailable;
  }

  @override
  Future<void> dispose() async {
    await _status?.close();
    _status = null;
    await _fgChannel?.shutdown();
    await _bgChannel?.shutdown();
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
    return await isPortOpen("127.0.0.1", portFront);
  }

  @override
  Future<bool> isActiveBg() async {
    return await isPortOpen("127.0.0.1", portBack);
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
