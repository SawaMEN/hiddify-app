import 'dart:async';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/features/app/widget/startup_app.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';

void main() {
  testWidgets('An unfinished initializer displays startup instead of a blank screen', (tester) async {
    final startup = Completer<Widget>();
    await tester.pumpWidget(StartupApp(initialization: startup.future));
    await tester.pump(const Duration(seconds: 30));
    expect(find.text('Starting VetrOFF Client…'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
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
    expect(find.text('Starting VetrOFF Client…'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  test('Malformed optional overrides do not prevent stored profiles from loading', () {
    for (final value in [null, 'null', '[]', '{broken', '{"version":"wrong"}', '{"name":42}']) {
      expect(UserOverride.fromStr(value), isNull);
    }
    expect(UserOverride.fromStr('{"name":"Saved"}')?.name, 'Saved');
  });
}
