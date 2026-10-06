import 'package:material_ui/material_ui.dart';
import 'package:hiddify/features/settings/notifier/config_option/config_option_notifier.dart';
import 'package:hiddify/features/settings/widget/preference_tile.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

class HandbookRoutingTile extends ConsumerStatefulWidget {
  const HandbookRoutingTile({super.key});
  @override
  ConsumerState<HandbookRoutingTile> createState() => _HandbookRoutingTileState();
}

class _HandbookRoutingTileState extends ConsumerState<HandbookRoutingTile> {
  bool _busy = false;
  bool _validSites(String value) {
    final sites = value.split(RegExp(r'[\s,;]+')).where((entry) => entry.isNotEmpty).toList();
    return sites.length <= 128 &&
        sites.every((site) => site.length <= 253 && RegExp(r'^[a-zA-Z0-9_-]+(\.[a-zA-Z0-9_-]+)+$').hasMatch(site));
  }

  Future<void> _update(Future<void> Function() change) async {
    setState(() => _busy = true);
    try {
      await ref.read(configOptionNotifierProvider.notifier).updateTogether(change, applyImmediately: true);
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$error')));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ru = Localizations.localeOf(context).languageCode == 'ru';
    final enabled = ref.watch(VpnPrivacyPreferences.handbookRouting);
    return Column(
      children: [
        SwitchListTile.adaptive(
          key: const ValueKey('handbook-routing'),
          title: Text(ru ? 'Пользовательские списки My Handbook' : 'My Handbook routing lists'),
          subtitle: Text(
            ru
                ? 'Альтернатива встроенной маршрутизации: при включении встроенные списки не применяются. Полный туннель имеет приоритет. Кэш обновляется раз в сутки при подключении.'
                : 'Replaces built-in routing lists while enabled. Full tunnel takes precedence. Cache refreshes daily on connection.',
          ),
          value: enabled,
          onChanged: _busy
              ? null
              : (value) => _update(() => ref.read(VpnPrivacyPreferences.handbookRouting.notifier).update(value)),
        ),
        if (enabled) ...[
          SwitchListTile.adaptive(
            key: const ValueKey('handbook-proxy'),
            title: Text(ru ? 'Сервисы из iplist через VPN' : 'iplist services through VPN'),
            value: ref.watch(VpnPrivacyPreferences.handbookProxy),
            onChanged: _busy
                ? null
                : (value) => _update(() => ref.read(VpnPrivacyPreferences.handbookProxy.notifier).update(value)),
          ),
          ValuePreferenceWidget(
            enabled: !_busy,
            validateInput: _validSites,
            title: ru
                ? 'Сервисы через VPN (домены через запятую; пусто — все)'
                : 'VPN services (comma-separated domains; empty = all)',
            value: ref.watch(VpnPrivacyPreferences.handbookProxySites),
            preferences: ref.watch(VpnPrivacyPreferences.handbookProxySites.notifier),
          ),
          ListTile(
            title: const Text('iplist.my-handbook.ru'),
            trailing: const Icon(Icons.open_in_new),
            onTap: () => launchUrl(Uri.parse('https://iplist.my-handbook.ru/ru'), mode: LaunchMode.externalApplication),
          ),
          SwitchListTile.adaptive(
            key: const ValueKey('handbook-direct'),
            title: Text(ru ? 'Российские сервисы из ru-iplist напрямую' : 'ru-iplist Russian services direct'),
            value: ref.watch(VpnPrivacyPreferences.handbookDirect),
            onChanged: _busy
                ? null
                : (value) => _update(() => ref.read(VpnPrivacyPreferences.handbookDirect.notifier).update(value)),
          ),
          ValuePreferenceWidget(
            enabled: !_busy,
            validateInput: _validSites,
            title: ru
                ? 'Прямые сервисы (домены через запятую; пусто — все)'
                : 'Direct services (comma-separated domains; empty = all)',
            value: ref.watch(VpnPrivacyPreferences.handbookDirectSites),
            preferences: ref.watch(VpnPrivacyPreferences.handbookDirectSites.notifier),
          ),
          ListTile(
            title: const Text('ru-iplist.my-handbook.ru'),
            trailing: const Icon(Icons.open_in_new),
            onTap: () =>
                launchUrl(Uri.parse('https://ru-iplist.my-handbook.ru/ru'), mode: LaunchMode.externalApplication),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              ru
                  ? 'Домены сервисов берите из каталога на сайте. Загружаются домены, IPv4 и IPv6 по HTTPS; первоначальная загрузка выполняется до подключения VPN. При пересечении списков VPN имеет приоритет. При первой загрузке требуется доступность источников; ошибка загрузки отображается как ошибка подключения.'
                  : 'Use service domains from the linked catalogues. Loads domains, IPv4 and IPv6 over HTTPS before VPN starts. VPN wins on overlap. Sources must be reachable on first use; download failures appear as connection errors.',
            ),
          ),
        ],
      ],
    );
  }
}
