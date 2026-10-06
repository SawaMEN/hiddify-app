import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:grpc/grpc.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/hiddifycore/core_interface/core_interface_mobile.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcommon/common.pb.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore.pb.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore_service.pbgrpc.dart';
import 'package:hiddify/hiddifycore/generated/v2/hello/hello.pb.dart';
import 'package:hiddify/hiddifycore/generated/v2/hello/hello_service.pbgrpc.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service.dart';
import 'package:hiddify/singbox/model/core_status.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

class _Hello extends HelloServiceBase {
  @override
  Future<HelloResponse> sayHello(ServiceCall call, HelloRequest request) async => HelloResponse();

  @override
  Stream<HelloResponse> sayHelloStream(ServiceCall call, Stream<HelloRequest> request) => const Stream.empty();
}

class _Daemon extends CoreServiceBase {
  CoreStates state = CoreStates.STOPPED;
  @override
  Stream<CoreInfoResponse> coreInfoListener(ServiceCall call, Empty request) async* {
    yield CoreInfoResponse(coreState: state);
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _StreamingDaemon extends _Daemon {
  final opened = Completer<void>();

  @override
  Stream<CoreInfoResponse> coreInfoListener(ServiceCall call, Empty request) async* {
    yield CoreInfoResponse(coreState: CoreStates.STOPPED);
    if (!opened.isCompleted) opened.complete();
    // Keep the RPC alive until the client closes its transport.
    while (!call.isCanceled) {
      await Future<void>.delayed(const Duration(milliseconds: 20));
    }
  }
}

final _provider = Provider<HiddifyCoreService>(HiddifyCoreService.new);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('Core alerts never display a literal null message', () {
    for (final message in [null, '', '  ']) {
      final failure = CoreStatus.stopped(alert: CoreAlert.startService, message: message).getCoreAlert();
      expect(failure!.present(TranslationsEn()).message, 'startService');
    }
  });

  test('Replacing and disposing control channels cancels open event RPCs', () async {
    final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final daemon = _StreamingDaemon();
    final server = Server.create(services: [_Hello(), daemon]);
    final core = CoreInterfaceMobile();
    for (final channel in [CoreInterfaceMobile.statusChannel, CoreInterfaceMobile.alertsChannel]) {
      messenger.setMockMethodCallHandler(MethodChannel(channel.name, const JSONMethodCodec()), (_) async => null);
    }
    await server.serve(address: '127.0.0.1', port: CoreInterfaceMobile.portFront);
    final directory = Directory.systemTemp;
    final directories = (baseDir: directory, workingDir: directory, tempDir: directory);
    await core.setup(directories, false, 3);
    final errors = <Object>[];
    final subscription = core.fgClient.coreInfoListener(Empty()).listen((_) {}, onError: errors.add);
    await daemon.opened.future.timeout(const Duration(seconds: 2));
    try {
      await core.setup(directories, false, 3).timeout(const Duration(seconds: 2));
      expect(errors, isNotEmpty, reason: 'Replacing a channel must cancel its ongoing RPCs');
      await subscription.cancel();
      final active = core.fgClient.coreInfoListener(Empty()).listen((_) {}, onError: (_) {});
      await Future<void>.delayed(const Duration(milliseconds: 50));
      await core.dispose().timeout(const Duration(seconds: 2));
      await active.cancel();
    } finally {
      await subscription.cancel();
      await core.dispose();
      await server.shutdown();
      for (final channel in [CoreInterfaceMobile.statusChannel, CoreInterfaceMobile.alertsChannel]) {
        messenger.setMockMethodCallHandler(MethodChannel(channel.name, const JSONMethodCodec()), null);
      }
    }
  });

  test('Background startup replaces a client even when a status RPC opens during native startup', () async {
    final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final foreground = Server.create(services: [_Hello()]);
    Server? background;
    final core = CoreInterfaceMobile();
    addTearDown(() async {
      await core.dispose();
      await foreground.shutdown();
      await background?.shutdown();
      messenger.setMockMethodCallHandler(CoreInterfaceMobile.methodChannel, null);
      for (final channel in [CoreInterfaceMobile.statusChannel, CoreInterfaceMobile.alertsChannel]) {
        messenger.setMockMethodCallHandler(MethodChannel(channel.name, const JSONMethodCodec()), null);
      }
    });
    for (final channel in [CoreInterfaceMobile.statusChannel, CoreInterfaceMobile.alertsChannel]) {
      messenger.setMockMethodCallHandler(MethodChannel(channel.name, const JSONMethodCodec()), (_) async => null);
    }
    messenger.setMockMethodCallHandler(CoreInterfaceMobile.methodChannel, (call) async {
      switch (call.method) {
        case 'setup':
          await foreground.serve(address: '127.0.0.1', port: CoreInterfaceMobile.portFront);
          return '';
        case 'stop':
          await background?.shutdown();
          background = null;
          return true;
        case 'start':
          final daemon = _StreamingDaemon();
          background = Server.create(services: [_Hello(), daemon]);
          await background!.serve(address: '127.0.0.1', port: CoreInterfaceMobile.portBack);
          messenger.handlePlatformMessage(
            CoreInterfaceMobile.statusChannel.name,
            const JSONMethodCodec().encodeSuccessEnvelope({'status': 'Started'}),
            (_) {},
          );
          core.bgClient.coreInfoListener(Empty()).listen((_) {}, onError: (_) {});
          await daemon.opened.future.timeout(const Duration(seconds: 2));
          return true;
        default:
          return null;
      }
    });
    final directory = Directory.systemTemp;
    await core.setup((baseDir: directory, workingDir: directory, tempDir: directory), false, 3);
    final initialClient = core.bgClient;
    expect(await core.setupBackground('/config.json', 'test').timeout(const Duration(seconds: 3)), const CoreStarted());
    final firstClient = core.bgClient;
    expect(firstClient, isNot(same(initialClient)));
    expect(await core.setupBackground('/config.json', 'test').timeout(const Duration(seconds: 3)), const CoreStarted());
    expect(core.bgClient, isNot(same(firstClient)));
    expect(await core.isBgClientAvailable(), isTrue);
  });

  for (final alert in [
    'StartService',
    'RequestVPNPermission',
    'RequestNotificationPermission',
    'android_start_failed',
  ]) {
    test('Native startup preserves $alert and its reason', () async {
      final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      final server = Server.create(services: [_Hello()]);
      final core = CoreInterfaceMobile();
      var setupCalls = 0;
      addTearDown(() async {
        await core.dispose();
        await server.shutdown();
        messenger.setMockMethodCallHandler(CoreInterfaceMobile.methodChannel, null);
        messenger.setMockMethodCallHandler(
          MethodChannel(CoreInterfaceMobile.statusChannel.name, const JSONMethodCodec()),
          null,
        );
        messenger.setMockMethodCallHandler(
          MethodChannel(CoreInterfaceMobile.alertsChannel.name, const JSONMethodCodec()),
          null,
        );
      });
      messenger.setMockMethodCallHandler(
        MethodChannel(CoreInterfaceMobile.statusChannel.name, const JSONMethodCodec()),
        (_) async => null,
      );
      messenger.setMockMethodCallHandler(
        MethodChannel(CoreInterfaceMobile.alertsChannel.name, const JSONMethodCodec()),
        (_) async => null,
      );
      messenger.setMockMethodCallHandler(CoreInterfaceMobile.methodChannel, (call) async {
        switch (call.method) {
          case 'setup':
            setupCalls++;
            await server.serve(address: '127.0.0.1', port: CoreInterfaceMobile.portFront);
            return '';
          case 'stop':
            return true;
          case 'start':
            throw PlatformException(code: alert, message: 'specific native failure');
          default:
            return null;
        }
      });
      final directory = Directory.systemTemp;
      await core.setup((baseDir: directory, workingDir: directory, tempDir: directory), false, 3);
      if (alert == 'StartService') {
        await core.setup((baseDir: directory, workingDir: directory, tempDir: directory), false, 3);
        expect(setupCalls, 1, reason: 'Reuse a running foreground core on warm startup');
      }
      final result = await core.setupBackground('/config.json', 'test');
      final expected = alert == 'android_start_failed'
          ? const CoreStatus.stopped(
              alert: CoreAlert.startService,
              message: 'android_start_failed: specific native failure',
            )
          : CoreStatus.fromEvent({'status': 'Stopped', 'alert': alert, 'message': 'specific native failure'});
      expect(result, expected);
      expect(result.getCoreAlert(), isNotNull);
    });
  }
  test('Daemon state overrides a stale Android service state and missing RPC recovers', () async {
    final container = ProviderContainer();
    final service = container.read(_provider);
    final daemon = _Daemon();
    final foreground = Server.create(services: [_Hello()]);
    final background = Server.create(services: [daemon]);
    final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    for (final channel in [CoreInterfaceMobile.statusChannel, CoreInterfaceMobile.alertsChannel]) {
      messenger.setMockMethodCallHandler(MethodChannel(channel.name, const JSONMethodCodec()), (_) async => null);
    }
    addTearDown(() async {
      await service.dispose();
      container.dispose();
      await foreground.shutdown();
      await background.shutdown();
      for (final channel in [CoreInterfaceMobile.statusChannel, CoreInterfaceMobile.alertsChannel]) {
        messenger.setMockMethodCallHandler(MethodChannel(channel.name, const JSONMethodCodec()), null);
      }
    });
    await foreground.serve(address: '127.0.0.1', port: CoreInterfaceMobile.portFront);
    await background.serve(address: '127.0.0.1', port: CoreInterfaceMobile.portBack);
    final directory = Directory.systemTemp;
    await service.core.setup((baseDir: directory, workingDir: directory, tempDir: directory), false, 3);
    messenger.handlePlatformMessage(
      CoreInterfaceMobile.statusChannel.name,
      const JSONMethodCodec().encodeSuccessEnvelope({'status': 'Started'}),
      (_) {},
    );
    expect(await service.backgroundCoreRunning(), isFalse);
    daemon.state = CoreStates.STARTED;
    expect(await service.backgroundCoreRunning(), isTrue);
    daemon.state = CoreStates.STARTING;
    expect(await service.backgroundCoreRunning(), isTrue);
    await background.shutdown();
    expect(await service.backgroundCoreRunning(), isFalse);
  });
}
