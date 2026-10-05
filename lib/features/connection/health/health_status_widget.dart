import 'package:material_ui/material_ui.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/features/connection/health/connection_health.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/connection/diagnostics/diagnostics_page.dart';

class HealthStatusWidget extends ConsumerWidget {
  const HealthStatusWidget({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider).requireValue;
    final running = ref.watch(serviceRunningProvider),
        health = ref.watch(connectionHealthProvider),
        retry = ref.watch(recoveryStatusProvider);
    final label = retry > 0
        ? '${t.client.recovering}: $retry / 5'
        : !running
        ? t.client.disconnected
        : switch (health) {
            InternetHealth.available => t.client.connectedHealthy,
            InternetHealth.unavailable => t.client.connectedUnhealthy,
            InternetHealth.checking => t.client.checking,
            InternetHealth.unchecked => t.client.connectedUnchecked,
          };
    return Semantics(
      liveRegion: true,
      child: TextButton(
        onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const DiagnosticsPage())),
        child: Text(label, textAlign: TextAlign.center),
      ),
    );
  }
}
