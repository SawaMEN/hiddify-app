import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;

import 'package:material_ui/material_ui.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/features/app/widget/startup_app.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';

void main() {
  testWidgets('An unfinished initializer displays startup instead of a blank screen', (tester) async {
    final startup = Completer<Widget>();
    await tester.pumpWidget(StartupApp(initialization: startup.future));
    final initialAngle = tester.widget<RotationTransition>(find.byKey(const ValueKey('startup_rotor'))).turns.value;
    await tester.pump(const Duration(seconds: 30));
    expect(tester.widget<RotationTransition>(find.byKey(const ValueKey('startup_rotor'))).turns.value, isNot(initialAngle));
    expect(find.text('VetrOFF Client'), findsOneWidget);
    expect(find.text('Starting'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(tester.takeException(), isNull);
    addTearDown(() => tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump();
    expect(tester.binding.transientCallbackCount, 0);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pump();
    expect(tester.binding.transientCallbackCount, greaterThan(0));
  });

  testWidgets('Russian startup is readable and releases its animations after completion', (tester) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    tester.platformDispatcher.localeTestValue = const Locale('ru');
    addTearDown(tester.platformDispatcher.clearLocaleTestValue);
    final startup = Completer<Widget>();
    final boundary = GlobalKey();
    await tester.runAsync(() async {
      final loader = FontLoader('Manrope')..addFont(rootBundle.load('assets/fonts/Manrope.ttf'));
      await loader.load();
    });
    await tester.pumpWidget(RepaintBoundary(key: boundary, child: StartupApp(initialization: startup.future)));
    await tester.runAsync(() async {
      await precacheImage(const AssetImage('assets/images/logo.png'), tester.element(find.byType(StartupApp)));
    });
    await tester.pump(const Duration(seconds: 2));
    expect(find.text('Запуск'), findsOneWidget);
    expect(find.text('VetrOFF Client'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await expectLater(find.byKey(boundary), matchesGoldenFile('goldens/startup-russian.png'));
    if (Platform.environment['IS_GITHUB_ACTIONS'] == '1') {
      await tester.runAsync(() async {
      final render = boundary.currentContext!.findRenderObject()! as RenderRepaintBoundary;
      final image = await render.toImage();
      final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
      image.dispose();
      final file = File('test/failures/startup-preview.png');
      await file.parent.create(recursive: true);
      await file.writeAsBytes(bytes!.buffer.asUint8List());
      });
    }
    startup.complete(const MaterialApp(home: Text('Ready')));
    await tester.pumpAndSettle();
    expect(find.text('Ready'), findsOneWidget);
    expect(tester.binding.transientCallbackCount, 0);
  });

  testWidgets('Reduced motion shows startup without scheduling repeating frames', (tester) async {
    tester.platformDispatcher.accessibilityFeaturesTestValue = const FakeAccessibilityFeatures(disableAnimations: true);
    addTearDown(tester.platformDispatcher.clearAccessibilityFeaturesTestValue);
    final startup = Completer<Widget>();
    await tester.pumpWidget(StartupApp(initialization: startup.future));
    await tester.pumpAndSettle();
    expect(find.text('VetrOFF Client'), findsOneWidget);
    expect(tester.binding.transientCallbackCount, 0);
    expect(tester.takeException(), isNull);
  });

  testWidgets('A failed initializer displays its diagnostic', (tester) async {
    final startup = Completer<Widget>();
    await tester.pumpWidget(StartupApp(initialization: startup.future));
    startup.completeError(StateError('Startup failed at [preferences]'));
    await tester.pumpAndSettle();
    expect(find.text('Unable to start VetrOFF Client'), findsOneWidget);
    expect(find.textContaining('preferences'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('A successful initializer replaces startup with the app', (tester) async {
    final startup = Completer<Widget>();
    await tester.pumpWidget(StartupApp(initialization: startup.future));
    startup.complete(const MaterialApp(home: Text('Ready')));
    await tester.pumpAndSettle();
    expect(find.text('Ready'), findsOneWidget);
    expect(find.text('VetrOFF Client'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  test('Malformed optional overrides do not prevent stored profiles from loading', () {
    for (final value in [null, 'null', '[]', '{broken', '{"version":"wrong"}', '{"name":42}']) {
      expect(UserOverride.fromStr(value), isNull);
    }
    expect(UserOverride.fromStr('{"name":"Saved"}')?.name, 'Saved');
  });
}
