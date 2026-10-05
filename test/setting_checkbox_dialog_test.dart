import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/router/dialog/widgets/setting_checkbox_dialog.dart';
import 'package:hiddify/hiddifycore/generated/v2/config/route_rule.pb.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:protobuf/protobuf.dart';

void main() {
  for (final cancel in [true, false]) {
    testWidgets('Checkbox dialog keeps changes local until ${cancel ? "cancel" : "done"}', (tester) async {
      final selected = <ProtobufEnum>[Outbound.direct];
      List<ProtobufEnum>? result;
      final router = GoRouter(routes: [
        GoRoute(
          path: '/',
          builder: (context, state) => Scaffold(
            body: TextButton(
              child: const Text('Open'),
              onPressed: () async {
                result = await showDialog<List<ProtobufEnum>>(
                  context: context,
                  builder: (_) => SettingCheckboxDialog(
                    title: 'Options',
                    values: [Outbound.direct, Outbound.block],
                    selectedValues: selected,
                    t: const {'direct': 'Direct', 'block': 'Block'},
                  ),
                );
              },
            ),
          ),
        ),
      ]);
      addTearDown(router.dispose);
      await tester.pumpWidget(ProviderScope(
        overrides: [translationsProvider.overrideWith((_) async => TranslationsEn())],
        child: MaterialApp.router(routerConfig: router),
      ));
      // Resolve translations before opening the dialog.
      final context = tester.element(find.text('Open'));
      await ProviderScope.containerOf(context).read(translationsProvider.future);
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(CheckboxListTile).last);
      await tester.pumpAndSettle();
      expect(selected, [Outbound.direct]);
      await tester.tap(find.text(cancel ? 'Cancel' : 'Done'));
      await tester.pumpAndSettle();
      expect(result, cancel ? isNull : [Outbound.direct, Outbound.block]);
      expect(selected, [Outbound.direct]);
      expect(tester.takeException(), isNull);
    });
  }
}
