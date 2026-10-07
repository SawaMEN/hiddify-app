import 'package:material_ui/material_ui.dart';
import 'package:hiddify/features/settings/data/automatic_hotspot.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service_provider.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/features/settings/widget/wifi_sharing_guide.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';

class WifiSharingInstructionsPage extends ConsumerStatefulWidget {
  const WifiSharingInstructionsPage({super.key});
  @override
  ConsumerState<WifiSharingInstructionsPage> createState() => _InstructionsState();
}

class _InstructionsState extends ConsumerState<WifiSharingInstructionsPage> {
  String? _ip;
  bool _loading = false;

  Future<void> _refresh() async {
    setState(() => _loading = true);
    try {
      final hotspot = ref.read(automaticHotspotProvider);
      if (hotspot['active'] == true) {
        setState(() => _ip = hotspot['ip'] as String?);
        return;
      }
      final result = await ref.read(hiddifyCoreServiceProvider).getLANIP().run();
      if (mounted) setState(() => _ip = result.fold((_) => null, (value) => value.ip));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ru = Localizations.localeOf(context).languageCode == 'ru';
    final port = ref.watch(ConfigOptions.mixedPort);
    final password = ref.watch(ConfigOptions.lanSharingPassword);
    final root = ref.watch(VpnPrivacyPreferences.useRoot);
    final hotspot = ref.watch(automaticHotspotProvider);
    final hotspotIp = hotspot['active'] == true ? hotspot['ip'] as String? : null;
    final effectivePort = port > 0 ? port : 12334;
    final host = hotspotIp ?? _ip ?? (ru ? 'IP раздатчика' : 'Host IP');
    return DefaultTabController(
      length: 5,
      child: Scaffold(
        appBar: AppBar(
          title: Text(ru ? 'Подключение к Wi-Fi телефона' : 'Connect to phone Wi-Fi'),
          bottom: const TabBar(
            isScrollable: true,
            tabs: [
              Tab(text: 'Android'),
              Tab(text: 'Windows'),
              Tab(text: 'Linux'),
              Tab(text: 'macOS'),
              Tab(text: 'iOS / iPadOS'),
            ],
          ),
        ),
        body: TabBarView(
          children: List.generate(5, (platform) {
            final guide = sharingGuide(platform, ru, host, effectivePort, root: root);
            return ListView(
              key: PageStorageKey('wifi-guide-$platform'),
              padding: const EdgeInsets.all(20),
              children: [
                Text(
                  ru
                      ? 'Этот телефон с приложением раздаёт Wi-Fi через VPN. Выберите ОС устройства, которое получает Wi-Fi от телефона. Все шаги и схемы ниже относятся к получающему устройству.'
                      : 'This phone runs the app and shares Wi-Fi through VPN. Choose the OS of the device receiving Wi-Fi from this phone. All steps and diagrams below apply to the receiving device.',
                ),
                const SizedBox(height: 16),
                if (hotspot['active'] == true)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: SelectableText(
                        '${ru ? 'Подключитесь к сети' : 'Join network'}: ${hotspot['ssid']}\n'
                        '${ru ? 'Пароль Wi-Fi' : 'Wi-Fi password'}: ${hotspot['password']}',
                      ),
                    ),
                  ),
                if (!root)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            ru ? 'Данные для подключения' : 'Connection details',
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: 8),
                          SelectableText(
                            ru
                                ? 'IP: ${hotspotIp ?? _ip ?? 'шлюз Wi-Fi клиента при прямой раздаче'}\nПорт: $effectivePort\nПользователь: hiddify\nПароль прокси: ${password.isEmpty ? 'не установлен — включите раздачу в основных настройках' : password}'
                                : 'IP: ${hotspotIp ?? _ip ?? 'client Wi-Fi gateway for a direct hotspot'}\nPort: $effectivePort\nUsername: hiddify\nProxy password: ${password.isEmpty ? 'not set — enable sharing in general settings' : password}',
                          ),
                          TextButton.icon(
                            onPressed: _loading ? null : _refresh,
                            icon: const Icon(Icons.refresh),
                            label: Text(ru ? 'Обновить IP раздатчика' : 'Refresh host IP'),
                          ),
                          Text(
                            ru
                                ? 'Точка доступа создаётся приложением автоматически. Пароль Wi-Fi показан выше; пароль прокси берите отсюда.'
                                : 'The app creates the hotspot automatically. The Wi-Fi password is above; use the proxy password shown here.',
                          ),
                        ],
                      ),
                    ),
                  ),
                const SizedBox(height: 16),
                Text(
                  ru
                      ? 'Ниже — нарисованные схемы экранов, а не снимки вашей системы. Названия пунктов могут отличаться. Подставляйте свои IP и порт.'
                      : 'Below are drawn screen diagrams, not screenshots of your system. Menu names may differ. Use your own IP and port.',
                ),
                if (root)
                  Text(
                    ru
                        ? 'На телефоне включён root-режим: подключайтесь без ручного прокси.'
                        : 'The phone uses root mode: connect without a manual proxy.',
                  ),
                for (var i = 0; i < guide.length; i++) _GuideSection(section: guide[i], number: i + 1, ru: ru),
                _GuideSection(
                  number: guide.length + 1,
                  ru: ru,
                  section: SharingGuideSection(
                    ru ? 'Проверьте, что всё работает' : 'Check that it works',
                    [
                      ru
                          ? 'На раздатчике и клиенте откройте один и тот же сайт проверки внешнего IP. Для сайта, направленного через VPN, адреса должны совпадать. Выберите сайт без исключения «напрямую», либо временно включите полный туннель. Верните прежний режим после проверки.'
                          : 'Open the same external IP check site on host and client. For a site routed through the VPN the addresses should match. Use a site without a direct exception, or temporarily enable full tunnel. Restore your previous mode after the check.',
                      if (hotspot['active'] == true)
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: SelectableText(
                              '${ru ? 'Подключитесь к сети' : 'Join network'}: ${hotspot['ssid']}\n'
                              '${ru ? 'Пароль Wi-Fi' : 'Wi-Fi password'}: ${hotspot['password']}',
                            ),
                          ),
                        ),
                      if (!root)
                        ru
                            ? 'Если браузер запрашивает пароль, укажите hiddify и пароль прокси. Ошибка 407 означает проблему авторизации; проверьте логин, пароль и поддержку авторизации клиентом.'
                            : 'If the browser prompts, enter hiddify and the proxy password. Error 407 indicates authentication trouble; check credentials and client authentication support.',
                      ru
                          ? 'Откройте тест DNS и IPv6 на клиенте. Без root обычный HTTP-прокси не перехватывает системный DNS, UDP и приложения, игнорирующие прокси. Для SOCKS5 включайте удалённый DNS. Совпадение IP браузера подтверждает только проверенный трафик.'
                          : 'Run a DNS and IPv6 test on the client. Without root, an HTTP proxy does not capture system DNS, UDP or apps ignoring the proxy. Enable remote DNS for SOCKS5. A matching browser IP confirms only the tested traffic.',
                    ],
                    screenTitle: ru ? 'Проверка на клиенте' : 'Client check',
                    fields: {
                      'Wi-Fi': 'VPN-WiFi',
                      ru ? 'VPN на раздатчике' : 'Host VPN': ru ? 'Подключён' : 'Connected',
                      ru ? 'IP тестового сайта' : 'Test site IP': ru
                          ? 'Совпадает с VPN для этого сайта'
                          : 'Matches VPN for this site',
                      'DNS / IPv6': ru ? 'Проверены отдельно' : 'Checked separately',
                    },
                  ),
                ),
                _GuideSection(
                  number: guide.length + 2,
                  ru: ru,
                  section: SharingGuideSection(ru ? 'Если не подключается' : 'If it does not connect', [
                    ru
                        ? 'Сеть не видна: проверьте, что включена системная точка доступа, а не только переключатель приложения; попробуйте 2,4 ГГц. «Подключено без интернета» не всегда означает ошибку: системная проверка Android может не использовать прокси. Проверьте сайт в настроенном браузере.'
                        : 'Network missing: check the system hotspot, not just the app switch; try 2.4 GHz. Connected without internet does not always mean failure: Android connectivity checks may not use the proxy. Test a site in the configured browser.',
                    ru
                        ? 'Тайм-аут или отказ подключения: проверьте сеть, локальный IP, порт, запущенный VPN и пароль. Обновите IP после перезапуска точки доступа. Если данные верны, проверьте доступность раздачи на телефоне. На получающем компьютере не нужно открывать входящий порт: он подключается к прокси телефона.'
                        : 'Timeout or connection refused: check the network, local IP, port, running VPN and password. Refresh the IP after restarting the hotspot. If the details are correct, check sharing availability on the phone. The receiving computer does not need an incoming port opened: it connects to the phone proxy.',
                    ru
                        ? 'IP остался прежним: убедитесь, что программа использует прокси, тестовый сайт не попал в прямые исключения, клиент не переключился на мобильные данные и не запустил собственный VPN. Проверяйте каждое нужное приложение отдельно.'
                        : 'IP unchanged: check that the app uses the proxy, the test site has no direct exception, the client has not switched to cellular data or started its own VPN. Test each app you need separately.',
                  ]),
                ),
                _GuideSection(
                  number: guide.length + 3,
                  ru: ru,
                  section: SharingGuideSection(ru ? 'Завершите раздачу' : 'Finish sharing', [
                    ru
                        ? 'Отключитесь от Wi-Fi телефона. Если вводили ручной прокси на получающем устройстве, верните «Нет / Выкл.». Оставленный IP прокси может мешать интернету при следующем подключении.'
                        : 'Disconnect from the phone Wi-Fi. If you set a manual proxy on the receiving device, restore None / Off. A leftover proxy IP may break the next internet connection.',
                    ru
                        ? 'Используйте защиту Wi-Fi и пароль прокси. Передавайте данные только своим устройствам; не публикуйте карточку с настоящим паролем.'
                        : 'Use Wi-Fi security and a proxy password. Share credentials only with your devices; do not publish the card containing your real password.',
                  ]),
                ),
                TextButton.icon(
                  onPressed: () => launchUrl(Uri.parse(_sources[platform]), mode: LaunchMode.externalApplication),
                  icon: const Icon(Icons.open_in_new),
                  label: Text(ru ? 'Официальная справка по настройкам ОС' : 'Official OS settings help'),
                ),
              ],
            );
          }),
        ),
      ),
    );
  }
}

const _sources = [
  'https://support.google.com/android/answer/9059108',
  'https://support.microsoft.com/en-us/windows/experience/connectivity-networking/use-a-proxy-server-in-windows',
  'https://help.gnome.org/users/gnome-help/stable/net-proxy.html.en',
  'https://support.apple.com/guide/mac-help/mchlp2591/mac',
  'https://support.apple.com/guide/iphone/iphw5gjwl8k2/ios',
];

class _GuideSection extends StatelessWidget {
  const _GuideSection({required this.section, required this.number, required this.ru});
  final SharingGuideSection section;
  final int number;
  final bool ru;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(top: 24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('$number. ${section.title}', style: theme.textTheme.titleLarge),
          const SizedBox(height: 12),
          for (var i = 0; i < section.steps.length; i++)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 28,
                    height: 28,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: theme.colorScheme.primaryContainer, shape: BoxShape.circle),
                    child: Text('${i + 1}', style: TextStyle(color: theme.colorScheme.onPrimaryContainer)),
                  ),
                  const SizedBox(width: 10),
                  Expanded(child: SelectableText(section.steps[i])),
                ],
              ),
            ),
          if (section.screenTitle != null) _SettingsSketch(title: section.screenTitle!, fields: section.fields, ru: ru),
        ],
      ),
    );
  }
}

/// Code-drawn screenshots keep translated labels sharp at any text scale.
/// These are illustrations: no fields or switches modify the user's settings.
class _SettingsSketch extends StatelessWidget {
  const _SettingsSketch({required this.title, required this.fields, required this.ru});
  final String title;
  final Map<String, String> fields;
  final bool ru;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    return Align(
      alignment: Alignment.centerLeft,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 560),
        child: Semantics(
          label: ru ? 'Схематичный экран настроек' : 'Illustrated settings screen',
          child: Container(
            width: double.infinity,
            decoration: BoxDecoration(
              color: colors.surface,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: colors.outline, width: 2),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: colors.secondaryContainer,
                    borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.settings_outlined, color: colors.onSecondaryContainer),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          title,
                          style: TextStyle(color: colors.onSecondaryContainer, fontWeight: FontWeight.bold),
                        ),
                      ),
                    ],
                  ),
                ),
                for (final field in fields.entries)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(14, 12, 14, 0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(field.key, style: theme.textTheme.labelLarge),
                        const SizedBox(height: 4),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                          decoration: BoxDecoration(
                            color: colors.primaryContainer,
                            border: Border.all(color: colors.primary),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Row(
                            children: [
                              Expanded(
                                child: Text(field.value, style: TextStyle(color: colors.onPrimaryContainer)),
                              ),
                              if (field.value == (ru ? 'Включено' : 'On')) ...[
                                const SizedBox(width: 8),
                                Icon(Icons.toggle_on, color: colors.primary, size: 32),
                              ],
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                Padding(
                  padding: const EdgeInsets.all(14),
                  child: Text(
                    ru ? 'Схема • подставьте свои данные' : 'Illustration • use your own details',
                    style: theme.textTheme.labelSmall,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
