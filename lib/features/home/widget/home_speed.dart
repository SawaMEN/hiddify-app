import 'package:material_ui/material_ui.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/core/widget/glass_surface.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/features/stats/notifier/stats_notifier.dart';
import 'package:hiddify/features/proxy/selection/smart_selection.dart';
import 'package:hiddify/utils/number_formatters.dart';

class HomeSpeed extends ConsumerWidget {
  const HomeSpeed({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider).requireValue;
    final stats = ref.watch(statsNotifierProvider).value;
    final selected = ref.watch(smartSelectionProvider);
    return RepaintBoundary(
      child: GlassSurface(
        radius: 20,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              Row(
                children: [
                  Expanded(child: Text('↓ ${(stats?.downlink.toInt() ?? 0).speed()}', textAlign: TextAlign.center)),
                  Expanded(child: Text('↑ ${(stats?.uplink.toInt() ?? 0).speed()}', textAlign: TextAlign.center)),
                ],
              ),
              if (selected != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(t.client.selectionReason, textAlign: TextAlign.center),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
