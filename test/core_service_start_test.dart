import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:grpc/grpc.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/hiddifycore/core_interface/core_interface_mobile.dart';
import 'package:hiddify/hiddifycore/generated/v2/hello/hello.pb.dart';
import 'package:hiddify/hiddifycore/generated/v2/hello/hello_service.pbgrpc.dart';
import 'package:hiddify/singbox/model/core_status.dart';

class _Hello extends HelloServiceBase {
  @override
  Future<HelloResponse> sayHello(ServiceCall call, HelloRequest request) async => HelloResponse();

  @override
  Stream<HelloResponse> sayHelloStream(ServiceCall call, Stream<HelloRequest> request) => const Stream.empty();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('Core alerts never display a literal null message', () {
    for (final message in [null, '', '  ']) {
      final failure = CoreStatus.stopped(alert: CoreAlert.startService, message: message).getCoreAlert();
      expect(failure!.present(TranslationsEn()).message, 'startService');
    }
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
}
