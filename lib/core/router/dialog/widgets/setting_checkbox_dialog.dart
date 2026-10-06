import 'package:material_ui/material_ui.dart';
import 'package:go_router/go_router.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:protobuf/protobuf.dart';

class SettingCheckboxDialog extends ConsumerStatefulWidget {
  const SettingCheckboxDialog({
    super.key,
    required this.title,
    required this.values,
    required this.selectedValues,
    this.defaultValue,
    this.t,
  });

  final String title;
  final List<ProtobufEnum> values;
  final List<ProtobufEnum> selectedValues;
  final List<ProtobufEnum>? defaultValue;
  final Map<String, String>? t;

  @override
  ConsumerState<SettingCheckboxDialog> createState() => _SettingCheckboxDialogState();
}

class _SettingCheckboxDialogState extends ConsumerState<SettingCheckboxDialog> {
  late final List<ProtobufEnum> current = List.of(widget.selectedValues);

  String textWithTranslation(ProtobufEnum e) {
    if (widget.t == null) return '$e';
    return widget.t!['$e'] ?? '$e';
  }

  @override
  Widget build(BuildContext context) {
    final t = ref.watch(translationsProvider).requireValue;

    return AlertDialog(
      title: Text(widget.title),
      content: ConstrainedBox(
        constraints: const BoxConstraints(maxHeight: 300),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: widget.values
                .map(
                  (e) => CheckboxListTile(
                    title: Text(textWithTranslation(e)),
                    value: current.contains(e),
                    onChanged: (selected) => setState(() {
                      if (selected == true) {
                        current.add(e);
                      } else {
                        current.remove(e);
                      }
                    }),
                  ),
                )
                .toList(),
          ),
        ),
      ),
      actions: [
        if (widget.defaultValue != null) TextButton(child: Text(t.common.reset), onPressed: () => context.pop(List<ProtobufEnum>.of(widget.defaultValue!))),
        TextButton(child: Text(t.common.cancel), onPressed: () => context.pop()),
        TextButton(child: Text(t.common.done), onPressed: () => context.pop(List<ProtobufEnum>.of(current))),
      ],
    );
  }
}
