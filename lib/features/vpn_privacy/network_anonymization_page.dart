import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:material_ui/material_ui.dart';
import 'package:hiddify/core/utils/preferences_utils.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';

String _networkPrivacyText(BuildContext context, String ru, String en) =>
    Localizations.localeOf(context).languageCode == 'ru' ? ru : en;

class NetworkAnonymizationPage extends ConsumerStatefulWidget {
  const NetworkAnonymizationPage({super.key});

  @override
  ConsumerState<NetworkAnonymizationPage> createState() => _NetworkAnonymizationPageState();
}

class _NetworkAnonymizationPageState extends ConsumerState<NetworkAnonymizationPage> {
  bool _busy = false;

  Future<void> _update(
    StateNotifierProvider<PreferencesNotifier<bool, bool>, bool> preference,
    bool value,
  ) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await ref.read(preference.notifier).update(value);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _networkPrivacyText(
              context,
              'Изменение будет применено после переподключения VPN.',
              'The change will be applied after reconnecting the VPN.',
            ),
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _setAll(bool enabled) async {
    if (_busy) return;
    if (enabled) {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(
            _networkPrivacyText(
              context,
              'Включить все экспериментальные меры?',
              'Enable all experimental protections?',
            ),
          ),
          content: Text(
            _networkPrivacyText(
              context,
              'Эти настройки не рекомендуются к применению. Они могут нарушить работу сайтов, видеозвонков, игр, локальных устройств и приложений. Продолжить?',
              'These settings are not recommended for normal use. They may break websites, calls, games, local devices, and apps. Continue?',
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: Text(_networkPrivacyText(context, 'Отмена', 'Cancel')),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(_networkPrivacyText(context, 'Включить', 'Enable')),
            ),
          ],
        ),
      );
      if (confirmed != true || !mounted) return;
    }

    setState(() => _busy = true);
    try {
      await ref.read(VpnPrivacyPreferences.anonymizationBlockQuic.notifier).update(enabled);
      await ref.read(VpnPrivacyPreferences.anonymizationBlockStun.notifier).update(enabled);
      await ref.read(VpnPrivacyPreferences.anonymizationBlockPlainHttp.notifier).update(enabled);
      await ref.read(VpnPrivacyPreferences.anonymizationIsolateLan.notifier).update(enabled);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _networkPrivacyText(
              context,
              enabled
                  ? 'Экспериментальные меры включены. Переподключите VPN для применения.'
                  : 'Экспериментальные меры отключены. Переподключите VPN для применения.',
              enabled
                  ? 'Experimental protections enabled. Reconnect the VPN to apply them.'
                  : 'Experimental protections disabled. Reconnect the VPN to apply the change.',
            ),
          ),
        ),
      );
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _switch({
    required String titleRu,
    required String titleEn,
    required String subtitleRu,
    required String subtitleEn,
    required StateNotifierProvider<PreferencesNotifier<bool, bool>, bool> preference,
  }) {
    return SwitchListTile.adaptive(
      value: ref.watch(preference),
      onChanged: _busy ? null : (value) => _update(preference, value),
      title: Text(_networkPrivacyText(context, titleRu, titleEn)),
      subtitle: Text(_networkPrivacyText(context, subtitleRu, subtitleEn)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final enabledCount = [
      ref.watch(VpnPrivacyPreferences.anonymizationBlockQuic),
      ref.watch(VpnPrivacyPreferences.anonymizationBlockStun),
      ref.watch(VpnPrivacyPreferences.anonymizationBlockPlainHttp),
      ref.watch(VpnPrivacyPreferences.anonymizationIsolateLan),
    ].where((value) => value).length;

    return Scaffold(
      appBar: AppBar(
        title: Text(_networkPrivacyText(context, 'Анонимизация в сети', 'Network anonymization')),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Padding(
                        padding: EdgeInsets.only(top: 2, right: 12),
                        child: Icon(Icons.warning_amber_rounded),
                      ),
                      Expanded(
                        child: Text(
                          _networkPrivacyText(
                            context,
                            'Экспериментальные настройки — не рекомендуются к применению',
                            'Experimental settings — not recommended for normal use',
                          ),
                          style: const TextStyle(fontWeight: FontWeight.w700),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Text(
                    _networkPrivacyText(
                      context,
                      'Функции ниже намеренно отключены по умолчанию. Они изменяют сетевое поведение приложений и могут вызвать несовместимость. Используйте их только если понимаете последствия.',
                      'The options below are intentionally disabled by default. They change application network behavior and may cause compatibility problems. Use them only if you understand the consequences.',
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _networkPrivacyText(
                      context,
                      'Настройки применяются после переподключения VPN.',
                      'Settings take effect after reconnecting the VPN.',
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: _busy ? null : () => _setAll(true),
            icon: const Icon(Icons.privacy_tip_outlined),
            label: Text(
              _networkPrivacyText(
                context,
                'Включить все экспериментальные меры',
                'Enable all experimental protections',
              ),
            ),
          ),
          TextButton.icon(
            onPressed: _busy || enabledCount == 0 ? null : () => _setAll(false),
            icon: const Icon(Icons.restart_alt),
            label: Text(_networkPrivacyText(context, 'Отключить все', 'Disable all')),
          ),
          if (_busy) const LinearProgressIndicator(),
          const Divider(height: 28),
          _switch(
            titleRu: 'Блокировать QUIC / HTTP/3',
            titleEn: 'Block QUIC / HTTP/3',
            subtitleRu:
                'Блокирует распознанный QUIC в пользовательском TUN-трафике. Браузер обычно переключается на HTTPS поверх TCP, но часть приложений может потерять скорость или соединение.',
            subtitleEn:
                'Rejects detected QUIC in user TUN traffic. Browsers usually fall back to HTTPS over TCP, but some apps may lose performance or connectivity.',
            preference: VpnPrivacyPreferences.anonymizationBlockQuic,
          ),
          _switch(
            titleRu: 'Защита STUN / WebRTC',
            titleEn: 'STUN / WebRTC protection',
            subtitleRu:
                'Блокирует распознанный STUN в TUN. Может уменьшить сетевые утечки через WebRTC, но способен сломать звонки, P2P, игры и передачу медиа.',
            subtitleEn:
                'Rejects detected STUN in the TUN. This can reduce WebRTC network leaks, but may break calls, P2P, games, and media connectivity.',
            preference: VpnPrivacyPreferences.anonymizationBlockStun,
          ),
          _switch(
            titleRu: 'Блокировать незашифрованный HTTP',
            titleEn: 'Block unencrypted HTTP',
            subtitleRu:
                'Блокирует распознанный обычный HTTP из приложений. Сайты без HTTPS, captive portal и отдельные проверки подключения могут перестать работать.',
            subtitleEn:
                'Rejects detected plain HTTP from apps. Non-HTTPS sites, captive portals, and some connectivity checks may stop working.',
            preference: VpnPrivacyPreferences.anonymizationBlockPlainHttp,
          ),
          _switch(
            titleRu: 'Изолировать локальную сеть',
            titleEn: 'Isolate the local network',
            subtitleRu:
                'Блокирует обращения приложений из TUN к приватным IP-адресам локальной сети. Принтеры, телевизоры, роутер, NAS и другие LAN-устройства могут стать недоступны.',
            subtitleEn:
                'Rejects TUN application traffic to private LAN addresses. Printers, TVs, routers, NAS devices, and other local services may become unavailable.',
            preference: VpnPrivacyPreferences.anonymizationIsolateLan,
          ),
          const Divider(height: 28),
          ListTile(
            leading: const Icon(Icons.fingerprint),
            title: Text(
              _networkPrivacyText(
                context,
                'Что эти настройки не скрывают',
                'What these settings do not hide',
              ),
            ),
            subtitle: Text(
              _networkPrivacyText(
                context,
                'VPN не может глобально изменить User-Agent, операционную систему, браузер, Canvas/WebGL и другие данные внутри HTTPS. Этот режим не расшифровывает HTTPS и не является гарантией полной анонимности.',
                'A VPN cannot globally change the User-Agent, operating system, browser, Canvas/WebGL, or other data carried inside HTTPS. This mode does not decrypt HTTPS and does not guarantee complete anonymity.',
              ),
            ),
          ),
        ],
      ),
    );
  }
}
