import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:grpc/grpc.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service.dart';
import 'package:hiddify/hiddifycore/core_interface/core_interface_mobile.dart';
import 'package:hiddify/hiddifycore/generated/v2/hello/hello.pb.dart';
import 'package:hiddify/hiddifycore/generated/v2/hello/hello_service.pbgrpc.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcommon/common.pb.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore_service.pbgrpc.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore.pb.dart';

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

final _provider = Provider<HiddifyCoreService>(HiddifyCoreService.new);
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
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
