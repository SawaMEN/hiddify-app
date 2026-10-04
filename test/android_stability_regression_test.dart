import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/features/per_app_proxy/model/per_app_proxy_backup.dart';
import 'package:hiddify/features/profile/details/json_editor.dart';

void main() {
  test('Per-app backups export one JSON object and accept legacy double encoding', () {
    const backup = PerAppProxyBackup(
      include: PerAppProxyBackupMode(selected: ['org.example.app'], deselected: ['org.example.other']),
      exclude: PerAppProxyBackupMode(selected: ['org.example.excluded']),
    );
    final text = utf8.decode(utf8.encode(backup.toBackupText()));
    expect(jsonDecode(text), isA<Map<String, dynamic>>());
    expect(PerAppProxyBackup.fromBackupText(text), backup);
    expect(PerAppProxyBackup.fromBackupText(jsonEncode(text)), backup);
    for (final invalid in ['[]', 'null', '42', '{broken']) {
      expect(() => PerAppProxyBackup.fromBackupText(invalid), throwsFormatException);
    }
  });

  Widget editor({
    List<Editors> editors = const [Editors.tree],
    ValueChanged<dynamic>? onChanged,
    ValueChanged<bool>? onValidationChanged,
  }) => MaterialApp(
    home: Scaffold(
      body: JsonEditor(
        json: jsonEncode({
          'route': {'final': 'a"b\\c\n'},
          'dns': {'servers': []},
          'outbounds': [],
        }),
        editors: editors,
        onChanged: onChanged ?? (_) {},
        onValidationChanged: onValidationChanged,
      ),
    ),
  );

  testWidgets('Closing the editor cancels pending search', (tester) async {
    await tester.pumpWidget(editor());
    await tester.enterText(find.byType(TextField).first, 'route');
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('Closing the editor before delayed search scrolling is safe', (tester) async {
    await tester.pumpWidget(editor());
    await tester.enterText(find.byType(TextField).first, 'route');
    await tester.pump(const Duration(milliseconds: 501));
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 1));
    expect(tester.takeException(), isNull);
  });

  testWidgets('Text editor escapes JSON strings and publishes valid edits immediately', (tester) async {
    dynamic changed;
    bool? valid;
    await tester.pumpWidget(
      editor(
        editors: const [Editors.text],
        onChanged: (value) => changed = value,
        onValidationChanged: (value) => valid = value,
      ),
    );
    final field = find.byType(TextFormField);
    final controller = tester.widget<TextFormField>(field).controller!;
    final source = jsonDecode(controller.text) as Map<String, dynamic>;
    expect(source['route']['final'], 'a"b\\c\n');
    source['route']['final'] = 'edited';
    await tester.enterText(field, jsonEncode(source));
    expect(changed['route']['final'], 'edited');
    expect(changed['dns'], source['dns']);
    expect(valid, isTrue);
    await tester.enterText(field, '{broken');
    expect(valid, isFalse);
    await tester.pumpWidget(const SizedBox());
    expect(tester.takeException(), isNull);
  });
}
