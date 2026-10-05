import 'package:material_ui/material_ui.dart';
import 'package:hiddify/core/model/failures.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/features/connection/health/android_vpn_settings.dart';

class VpnProtectionPage extends ConsumerStatefulWidget {
  const VpnProtectionPage({super.key});
  @override
  ConsumerState<VpnProtectionPage> createState() => _VpnProtectionPageState();
}

class _VpnProtectionPageState extends ConsumerState<VpnProtectionPage> with WidgetsBindingObserver {
  Map<dynamic, dynamic> _status = {};
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refresh();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _refresh() async {
    try {
      final status = await AndroidVpnSettings.protection();
      if (mounted) setState(() => _status = status);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final t = ref.watch(translationsProvider).requireValue;
    String label(dynamic value) => value == true
        ? t.client.enabled
        : value == false
        ? t.client.disabled
        : t.client.unknown;
    return Scaffold(
      appBar: AppBar(title: Text(t.client.vpnProtection)),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(t.client.vpnProtectionHint),
          const SizedBox(height: 16),
          ListTile(title: Text(t.client.alwaysOn), trailing: Text(label(_status['alwaysOn']))),
          ListTile(title: Text(t.client.lockdown), trailing: Text(label(_status['lockdown']))),
          FilledButton(
            onPressed: () async {
              try {
                await AndroidVpnSettings.open();
              } catch (e) {
                if (context.mounted)
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(t.presentShortError(e))));
              }
            },
            child: Text(t.client.openVpnSettings),
          ),
        ],
      ),
    );
  }
}
