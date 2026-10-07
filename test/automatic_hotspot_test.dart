import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/features/settings/data/automatic_hotspot.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('vetroff/hotspot');

  test('Wi-Fi QR preserves SSIDs and passwords containing reserved characters', () {
    expect(AutomaticHotspot.wifiQr('Vetr;OFF:1', r'p\ass,"word'), r'WIFI:T:WPA;S:Vetr\;OFF\:1;P:p\\ass\,\"word;;');
  });

  test('Android status and credentials come from the hotspot reservation', () async {
    final calls = <MethodCall>[];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return call.method == 'stop'
          ? null
          : {'active': true, 'ssid': 'AndroidAP', 'password': 'secret123', 'ip': '192.168.43.1', 'root': true};
    });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    final controller = container.read(automaticHotspotProvider.notifier);
    expect(container.read(automaticHotspotProvider)['active'], false);
    await controller.start(root: true);
    expect(calls.first.arguments, {'root': true});
    expect(container.read(automaticHotspotProvider)['ssid'], 'AndroidAP');
    await controller.activate();
    await controller.stop();
    expect(calls.map((call) => call.method), ['start', 'activate', 'stop']);
    expect(container.read(automaticHotspotProvider)['active'], false);
  });

  test('a native start failure is surfaced without inventing an active AP', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, (_) async {
      throw PlatformException(code: 'hotspot_error', message: 'permission denied');
    });
    addTearDown(
      () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(channel, null),
    );
    final container = ProviderContainer();
    addTearDown(container.dispose);
    await expectLater(
      container.read(automaticHotspotProvider.notifier).start(root: false),
      throwsA(isA<PlatformException>()),
    );
    expect(container.read(automaticHotspotProvider)['active'], false);
  });
}
