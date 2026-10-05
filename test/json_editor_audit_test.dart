import 'dart:convert';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/features/profile/details/json_editor.dart';

void main() {
  Widget editor(Object data, ValueChanged<dynamic> changed, {List<Editors> editors = const [Editors.tree]}) =>
      MaterialApp(
        home: Scaffold(
          body: JsonEditor(
            json: jsonEncode(data),
            editors: editors,
            onChanged: changed,
            expandedObjects: const [
              ['outbounds'],
              ['outbounds', 0],
              ['items'],
              ['nested'],
              ['nested'],
            ],
          ),
        ),
      );

  Future<void> edit(WidgetTester tester, String text, String replacement) async {
    await tester.tap(find.text(text).last);
    await tester.pump();
    await tester.enterText(find.byType(TextFormField).last, replacement);
    tester.binding.focusManager.primaryFocus?.unfocus();
    await tester.pump();
  }

  testWidgets('Editing a numeric-looking string preserves type and whitespace', (tester) async {
    dynamic changed;
    await tester.pumpWidget(editor({'password': '00123'}, (v) => changed = v));
    await edit(tester, '00123', ' 00456 ');
    expect(changed['password'], ' 00456 ');
    expect(changed['password'], isA<String>());
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('Numeric keys remain strings and duplicate rename cannot overwrite values', (tester) async {
    dynamic changed;
    await tester.pumpWidget(editor({'original': 'one', 'existing': 'two'}, (v) => changed = v));
    await edit(tester, 'original', '123');
    expect(changed, {'123': 'one', 'existing': 'two'});
    await edit(tester, '123', 'existing');
    expect(changed, {'123': 'one', 'existing': 'two'});
    expect(find.text('one'), findsOneWidget);
    expect(find.text('two'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('Unknown protocol stays editable without dropdown assertion', (tester) async {
    await tester.pumpWidget(
      editor({
        'outbounds': [
          {'type': 'custom-new-protocol'},
        ],
      }, (_) {}),
    );
    expect(tester.takeException(), isNull);
    expect(find.text('custom-new-protocol'), findsWidgets);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('Primitive list index is read-only and search finds its value', (tester) async {
    await tester.pumpWidget(
      editor({
        'items': ['needle'],
      }, (_) {}),
    );
    await tester.tap(find.text('0'));
    await tester.pump();
    expect(find.byType(TextFormField), findsNothing);
    await tester.enterText(find.byType(TextField).first, 'needle');
    await tester.pump(const Duration(milliseconds: 501));
    expect(find.text('1 results'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(const Duration(seconds: 1));
  });

  testWidgets('Insertion preserves a pre-existing placeholder key', (tester) async {
    dynamic changed;
    await tester.pumpWidget(
      editor({
        'nested': {'new_key_added': 'original'},
      }, (v) => changed = v),
    );
    await tester.tap(find.byType(PopupMenuButton<String>).at(1));
    await tester.pumpAndSettle();
    await tester.tap(find.text('String'));
    await tester.pumpAndSettle();
    expect(changed['nested']['new_key_added'], 'original');
    expect(changed['nested'].length, 2);
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets('Invalid text survives mode switch and format action', (tester) async {
    await tester.pumpWidget(editor({'name': 'valid'}, (_) {}, editors: const [Editors.text, Editors.tree]));
    await tester.enterText(find.byType(TextFormField), '{unfinished');
    await tester.tap(find.byTooltip('Format'));
    await tester.pump();
    expect(tester.widget<TextFormField>(find.byType(TextFormField)).controller!.text, '{unfinished');
    await tester.tap(find.byTooltip('Change editor'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Tree'));
    await tester.pumpAndSettle();
    expect(tester.widget<TextFormField>(find.byType(TextFormField)).controller!.text, '{unfinished');
    await tester.pumpWidget(const SizedBox());
  });
}
