import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/hiddifycore/core_interface/core_interface_mobile.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      CoreInterfaceMobile.methodChannel,
      null,
    ),
  );

  test('Native false and null stop responses remain failures', () async {
    final core = CoreInterfaceMobile();
    for (final response in [false, null]) {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
        CoreInterfaceMobile.methodChannel,
        (call) async {
          expect(call.method, 'stop');
          return response;
        },
      );
      expect(await core.stop(), isFalse);
    }
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      CoreInterfaceMobile.methodChannel,
      (_) async => true,
    );
    expect(await core.stopMethodChannel(), isTrue);
  });

  test('Native platform errors propagate instead of becoming successful stop', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(
      CoreInterfaceMobile.methodChannel,
      (_) async => throw PlatformException(code: 'stop_failed'),
    );
    await expectLater(CoreInterfaceMobile().stop(), throwsA(isA<PlatformException>()));
  });
}
