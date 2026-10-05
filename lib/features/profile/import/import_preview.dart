import 'package:material_ui/material_ui.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/features/profile/import/import_summary.dart';

Future<bool> showImportPreview(BuildContext context, TranslationsEn t, ImportSummary summary, bool insecure) async =>
    await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: Text(t.client.preview),
        content: SizedBox(
          width: 440,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(t.client.importHint),
                const SizedBox(height: 16),
                Text('${t.client.servers}: ${summary.servers.length}'),
                Text('${t.client.duplicates}: ${summary.duplicates}'),
                Text('${t.client.unknownTypes}: ${summary.unknownTypes}'),
                Text('${t.client.overrides}: ${summary.overrides.isEmpty ? "—" : summary.overrides.join(", ")}'),
                if (insecure) ...[
                  const SizedBox(height: 16),
                  Text(t.client.plainHttp, style: TextStyle(color: Theme.of(context).colorScheme.error)),
                ],
              ],
            ),
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(t.client.cancel)),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(t.client.confirmImport)),
        ],
      ),
    ) ??
    false;
