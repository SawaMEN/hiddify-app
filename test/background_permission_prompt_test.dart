import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/theme/app_theme.dart';
import 'package:hiddify/core/theme/app_theme_mode.dart';
import 'package:hiddify/features/app/widget/background_permission_prompt.dart';
import 'package:material_ui/material_ui.dart';

void main() {
  for (final allow in [false, true]) {
    testWidgets('Background explanation returns permission choice: $allow', (tester) async {
      bool? choice;
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.runAsync(() async {
        for (final entry in {'Manrope': 'assets/fonts/Manrope.ttf', 'MaterialIcons': 'fonts/MaterialIcons-Regular.otf'}.entries) {
          final loader = FontLoader(entry.key)..addFont(rootBundle.load(entry.value));
          await loader.load();
        }
      });
      final boundary = GlobalKey();
      await tester.pumpWidget(RepaintBoundary(key: boundary, child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: AppTheme(AppThemeMode.dark, 'Manrope').darkTheme(null),
        home: Scaffold(body: Builder(builder: (context) => TextButton(
        onPressed: () async { choice = await showBackgroundPermissionExplanation(context, 'ru'); },
        child: const Text('VetrOFF Client'),
      ))))));
      await tester.tap(find.text('VetrOFF Client'));
      await tester.pumpAndSettle();
      expect(find.text('Стабильная работа в фоне'), findsOneWidget);
      expect(find.textContaining('системный запрос Android'), findsOneWidget);
      if (!allow && Platform.environment['IS_GITHUB_ACTIONS'] == '1') {
        await tester.runAsync(() async {
          final render = boundary.currentContext!.findRenderObject()! as RenderRepaintBoundary;
          final image = await render.toImage();
          final bytes = await image.toByteData(format: ui.ImageByteFormat.png);
          image.dispose();
          final file = File('test/failures/background-permission-preview.png');
          await file.parent.create(recursive: true);
          await file.writeAsBytes(bytes!.buffer.asUint8List());
        });
      }
      await tester.tap(find.text(allow ? 'Разрешить работу в фоне' : 'Позже'));
      await tester.pumpAndSettle();
      expect(choice, allow);
      expect(find.byType(BackgroundPermissionSheet), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }
}
