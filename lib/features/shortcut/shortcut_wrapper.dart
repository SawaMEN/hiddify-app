import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:hiddify/core/router/bottom_sheets/bottom_sheets_notifier.dart';
import 'package:hiddify/core/router/go_router/go_router_notifier.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

class ShortcutWrapper extends HookConsumerWidget {
  const ShortcutWrapper(this.child, {super.key});

  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Shortcuts(
      shortcuts: {
        LogicalKeySet(LogicalKeyboardKey.select): const ActivateIntent(),
        const SingleActivator(LogicalKeyboardKey.keyV, meta: true): PasteIntent(),
        const SingleActivator(LogicalKeyboardKey.keyV, control: true): PasteIntent(),
      },
      child: Actions(
        actions: {
          PasteIntent: CallbackAction(
            onInvoke: (_) async {
              if (rootNavKey.currentContext != null) {
                final captureResult = await Clipboard.getData(Clipboard.kTextPlain).then((value) => value?.text ?? '');
                ref.read(bottomSheetsNotifierProvider.notifier).showAddProfile(url: captureResult);
              }
              return null;
            },
          ),
        },
        child: child,
      ),
    );
  }
}

class PasteIntent extends Intent {}
