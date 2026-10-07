import 'dart:math';

import 'package:material_ui/material_ui.dart';
import 'package:hiddify/features/settings/data/automatic_hotspot.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/connection/model/connection_status.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/features/common/qr_code_dialog.dart';
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
      if (PlatformUtils.isAndroid) {
        if (enabled) {
          if (await ref.read(activeProfileProvider.future) == null) {
            throw StateError('Сначала выберите VPN-профиль / Select a VPN profile first');
          }
          await ref.read(automaticHotspotProvider.notifier).start(root: ref.read(VpnPrivacyPreferences.useRoot));
        } else {
          await ref.read(automaticHotspotProvider.notifier).stop();
        }
      }
      await ref.read(configOptionNotifierProvider.notifier).updateTogether(() async {
        if (enabled && ref.read(ConfigOptions.lanSharingPassword).isEmpty) {
          final random = Random.secure();
          final password = List.generate(24, (_) => random.nextInt(16).toRadixString(16)).join();
          await ref.read(ConfigOptions.lanSharingPassword.notifier).update(password);
        }
        await ref.read(VpnPrivacyPreferences.wifiSharing.notifier).update(enabled);
      }, applyImmediately: true);
      if (PlatformUtils.isAndroid && enabled) {
        final connection = await ref.read(connectionNotifierProvider.future);
        if (connection is Disconnected) await ref.read(connectionNotifierProvider.notifier).toggleConnection();
        await ref.read(automaticHotspotProvider.notifier).activate();
      }
    } catch (error) {
      if (PlatformUtils.isAndroid) {
        // A failed VPN startup must never leave an apparently working AP.
        try {
          await ref.read(automaticHotspotProvider.notifier).stop();
        } catch (_) {}
        try {
          await ref
              .read(configOptionNotifierProvider.notifier)
              .updateTogether(
                () => ref.read(VpnPrivacyPreferences.wifiSharing.notifier).update(false),
                applyImmediately: true,
              );
        } catch (_) {}
      }
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
    final hotspot = ref.watch(automaticHotspotProvider);
    ref.listen(automaticHotspotProvider, (previous, next) {
      if (PlatformUtils.isAndroid && !_busy && previous?['active'] == true && next['active'] != true && enabled) {
        _update(false);
      }
    });
    return Column(
      children: [
        SwitchListTile.adaptive(
          key: const ValueKey('wifi-vpn-sharing'),
          secondary: const Icon(Icons.wifi_tethering_rounded),
          title: Text(ru ? 'Раздача Wi-Fi через VPN' : 'Share VPN over Wi-Fi'),
          subtitle: Text(
            root
                ? (ru
                      ? 'Root: точка доступа и VPN запускаются автоматически. Подключите устройства к созданной сети.'
                      : 'Root: automatically start the hotspot and VPN. Join the created Wi-Fi network.')
                : (ru
                      ? 'Точка доступа и VPN запускаются автоматически. Без root настройте прокси на подключённых устройствах.'
                      : 'Automatically start the hotspot and VPN. Without root, configure a proxy on connected devices.'),
          ),
          value: PlatformUtils.isAndroid ? hotspot['active'] == true : enabled,
          onChanged: _busy ? null : _update,
        ),
        if (hotspot['active'] == true)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SelectableText(
                    '${ru ? 'Сеть Wi-Fi' : 'Wi-Fi network'}: ${hotspot['ssid']}\n'
                    '${ru ? 'Пароль Wi-Fi' : 'Wi-Fi password'}: ${hotspot['password']}\n'
                    'IP: ${hotspot['ip']}',
                  ),
                  TextButton.icon(
                    icon: const Icon(Icons.qr_code),
                    label: Text(ru ? 'QR-код Wi-Fi' : 'Wi-Fi QR code'),
                    onPressed: () => showDialog<void>(
                      context: context,
                      builder: (_) => Dialog(
                        child: QrCodeDialog(
                          AutomaticHotspot.wifiQr(hotspot['ssid'] as String, hotspot['password'] as String),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        if (enabled && !root) const LanSharingPreferenceWidget(),
        ListTile(
          leading: const Icon(Icons.menu_book_outlined),
          title: Text(ru ? 'Как подключить устройства к Wi-Fi телефона' : 'Connect devices to this phone Wi-Fi'),
          trailing: const Icon(Icons.chevron_right_rounded),
          onTap: () =>
              Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const WifiSharingInstructionsPage())),
        ),
      ],
    );
  }
}
