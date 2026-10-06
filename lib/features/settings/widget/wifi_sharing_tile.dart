import 'dart:math';

import 'package:material_ui/material_ui.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/features/settings/notifier/config_option/config_option_notifier.dart';
import 'package:hiddify/features/settings/widget/lan_sharing_tile.dart';
import 'package:hiddify/features/settings/widget/wifi_sharing_instructions_page.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

class WifiSharingTile extends ConsumerStatefulWidget {
  const WifiSharingTile({super.key});

  @override
  ConsumerState<WifiSharingTile> createState() => _WifiSharingTileState();
}

class _WifiSharingTileState extends ConsumerState<WifiSharingTile> {
  bool _busy = false;

  Future<void> _update(bool enabled) async {
    setState(() => _busy = true);
    try {
      await ref.read(configOptionNotifierProvider.notifier).updateTogether(() async {
        if (enabled && ref.read(ConfigOptions.lanSharingPassword).isEmpty) {
          final random = Random.secure();
          final password = List.generate(24, (_) => random.nextInt(16).toRadixString(16)).join();
          await ref.read(ConfigOptions.lanSharingPassword.notifier).update(password);
        }
        await ref.read(VpnPrivacyPreferences.wifiSharing.notifier).update(enabled);
      }, applyImmediately: true);
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$error')));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ru = Localizations.localeOf(context).languageCode == 'ru';
    final enabled = ref.watch(VpnPrivacyPreferences.wifiSharing);
    final root = PlatformUtils.isAndroid && ref.watch(VpnPrivacyPreferences.useRoot);
    return Column(
      children: [
        SwitchListTile.adaptive(
          key: const ValueKey('wifi-vpn-sharing'),
          secondary: const Icon(Icons.wifi_tethering_rounded),
          title: Text(ru ? 'Раздача Wi-Fi через VPN' : 'Share VPN over Wi-Fi'),
          subtitle: Text(
            root
                ? (ru
                      ? 'Root: трафик точки доступа через VPN. Включите точку доступа в настройках ОС.'
                      : 'Root: route hotspot traffic through VPN. Enable the hotspot in system settings.')
                : (ru
                      ? 'Без root: HTTP/SOCKS-прокси для подключённых устройств. Требуется настройка клиентов.'
                      : 'Without root: HTTP/SOCKS proxy for connected devices. Configure each client.'),
          ),
          value: enabled,
          onChanged: _busy ? null : _update,
        ),
        if (enabled && !root) const LanSharingPreferenceWidget(),
        ListTile(
          leading: const Icon(Icons.menu_book_outlined),
          title: Text(ru ? 'Как раздать VPN: инструкции по ОС' : 'How to share VPN: OS instructions'),
          trailing: const Icon(Icons.chevron_right_rounded),
          onTap: () =>
              Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const WifiSharingInstructionsPage())),
        ),
      ],
    );
  }
}
