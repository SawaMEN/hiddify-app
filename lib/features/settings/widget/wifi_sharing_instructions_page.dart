import 'package:material_ui/material_ui.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service_provider.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

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
    final instructions = ru
        ? const [
            'Устройство-раздатчик: включите точку доступа в настройках Android, затем раздачу Wi-Fi через VPN в приложении и подключитесь к серверу.\n\nС root: включите root-режим в дополнительных настройках, разрешите su и переподключите VPN. Ядро перехватывает трафик точки доступа автоматически. Нужны поддержка TUN и netfilter в ядре; ошибка запуска означает, что режим не активирован. При обходе VPN включённые списки маршрутизации продолжают действовать.\n\nБез root: подключённое устройство должно использовать HTTP/SOCKS-прокси раздатчика. На Android-клиенте: Wi-Fi → текущая сеть → изменить → дополнительные настройки → прокси вручную. Укажите IP раздатчика и порт. Для прокси с паролем используйте клиент, поддерживающий авторизацию (пользователь hiddify), либо импортируйте SOCKS-ссылку из приложения. Системное поле прокси поддерживается не всеми приложениями.',
            'Раздатчик: подключите VPN, включите раздачу в приложении, затем Параметры → Сеть и Интернет → Мобильный хот-спот.\n\nКлиент: Параметры → Сеть и Интернет → Прокси → Использовать прокси-сервер. Укажите IP раздатчика и порт; при запросе авторизации пользователь hiddify и пароль ниже. Для SOCKS используйте приложение с поддержкой SOCKS5.\n\nДля прозрачной раздачи всего трафика нужен TUN и системный Internet Connection Sharing (ncpa.cpl → свойства VPN-адаптера → Доступ → адаптер хот-спота), права администратора и совместимый адаптер. После настройки проверьте внешний IP клиента.',
            'Раздатчик: включите VPN и раздачу в приложении. В NetworkManager создайте точку доступа (Графические настройки Wi-Fi → Использовать как точку доступа; в KDE — создать соединение Wi-Fi в режиме точки доступа).\n\nКлиент: в настройках сети или приложения включите HTTP/SOCKS5-прокси, IP раздатчика и порт. Для SOCKS выбирайте удалённое разрешение DNS (socks5h); пользователь hiddify и пароль ниже.\n\nДля прозрачной раздачи нужен TUN, IP forwarding, правила forwarding/NAT и policy routing в таблицу TUN. Это требует прав администратора; обычное соединение NetworkManager в режиме shared само по себе не гарантирует VPN. Отключите IPv6 на клиенте, если VPN не обслуживает IPv6, и проверьте IP/DNS.',
            'Раздатчик: подключите VPN и включите раздачу в приложении. Системные настройки → Основные → Общий доступ → Общий Интернет: выберите входящее соединение и Wi-Fi, настройте пароль и включите доступ. Поддержка зависит от входящего интерфейса; один Wi-Fi адаптер обычно не может одновременно принимать Wi-Fi и раздавать его.\n\nКлиент: Системные настройки → Сеть → Wi-Fi → Подробнее → Прокси. Выберите веб-прокси и защищённый веб-прокси, IP раздатчика и порт, включите авторизацию с пользователем hiddify и паролем ниже. Для SOCKS выберите SOCKS-прокси. Общий Интернет не гарантирует передачу VPN; используйте прокси и проверяйте внешний IP.',
            'iPhone/iPad как раздатчик: Настройки → Режим модема. iOS раздаёт сотовое соединение и не предоставляет приложению возможность прозрачно направить клиентов через его VPN. Для доступного в локальной сети прокси приложение должно оставаться активным; фоновая работа и доступ через режим модема зависят от iOS. При недоступности используйте Android, Windows, Linux или macOS как раздатчик.\n\niPhone/iPad как клиент: Настройки → Wi-Fi → кнопка ⓘ у сети → Настройка прокси → Вручную. Укажите IP раздатчика и порт, включите аутентификацию: пользователь hiddify, пароль ниже. Этот HTTP-прокси используют только поддерживающие его приложения.',
          ]
        : const [
            'Host: enable Android hotspot in system settings, enable VPN sharing in the app, then connect the VPN.\n\nRoot: enable root mode in additional settings, grant su and reconnect. The core intercepts forwarded hotspot traffic automatically. TUN and netfilter kernel support are required; a startup error means sharing is not active. Enabled routing bypass lists still apply.\n\nWithout root: configure each client to use the host HTTP/SOCKS proxy. Android client: Wi-Fi → current network → Edit → Advanced → Manual proxy, then enter host IP and port. For password authentication use a compatible proxy client (username hiddify), or import the app SOCKS link. Some apps ignore the system proxy.',
            'Host: connect VPN, enable sharing in the app, then Settings → Network & Internet → Mobile hotspot.\n\nClient: Settings → Network & Internet → Proxy → Use a proxy server. Enter host IP and port; use username hiddify and the password below when prompted. SOCKS5 requires a compatible app.\n\nTransparent sharing requires TUN and Internet Connection Sharing (ncpa.cpl → VPN adapter properties → Sharing → hotspot adapter), administrator rights and compatible adapters. Verify the client external IP.',
            'Host: enable VPN and app sharing; create a Wi-Fi hotspot in NetworkManager/KDE network settings.\n\nClient: configure HTTP/SOCKS5 proxy in network or application settings, with host IP and port. Use socks5h for remote DNS, username hiddify and the password below.\n\nTransparent sharing requires administrator rights, TUN, IP forwarding, forwarding/NAT rules and policy routing to the TUN table. NetworkManager shared mode alone does not guarantee VPN routing. Disable client IPv6 if the VPN does not support it and verify IP/DNS.',
            'Host: connect VPN and enable app sharing. System Settings → General → Sharing → Internet Sharing: choose the incoming connection and Wi-Fi, set a password and enable sharing. A single Wi-Fi adapter usually cannot receive Wi-Fi and act as a hotspot simultaneously.\n\nClient: System Settings → Network → Wi-Fi → Details → Proxies. Set Web Proxy and Secure Web Proxy (or SOCKS proxy), host IP and port; authenticate with username hiddify and the password below. Internet Sharing does not guarantee VPN forwarding; use the proxy and verify external IP.',
            'iPhone/iPad host: Settings → Personal Hotspot. iOS shares cellular data and does not give this app a way to transparently route hotspot clients through VPN. A LAN proxy requires the app to stay active; background availability and hotspot reachability depend on iOS. If unreachable, use Android, Windows, Linux or macOS as host.\n\niPhone/iPad client: Settings → Wi-Fi → ⓘ → Configure Proxy → Manual. Enter host IP and port; enable authentication, username hiddify and the password below. Only apps that support the system HTTP proxy use it.',
          ];
    return DefaultTabController(
      length: 5,
      child: Scaffold(
        appBar: AppBar(
          title: Text(ru ? 'Раздача VPN по Wi-Fi' : 'VPN Wi-Fi sharing'),
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
          children: instructions
              .map(
                (text) => ListView(
                  padding: const EdgeInsets.all(20),
                  children: [
                    SelectableText(
                      ru
                          ? 'Прокси: ${_ip ?? 'IP раздатчика (шлюз Wi-Fi клиента)'}:$port\nПользователь: hiddify\nПароль: ${password.isEmpty ? 'не установлен' : password}'
                          : 'Proxy: ${_ip ?? 'host IP (client Wi-Fi gateway)'}:$port\nUsername: hiddify\nPassword: ${password.isEmpty ? 'not set' : password}',
                    ),
                    TextButton.icon(
                      onPressed: _loading ? null : _refresh,
                      icon: const Icon(Icons.refresh),
                      label: Text(ru ? 'Обновить IP раздатчика' : 'Refresh host IP'),
                    ),
                    Text(
                      ru
                          ? 'Если показан IP другого интерфейса, используйте адрес шлюза в свойствах Wi-Fi подключённого устройства.'
                          : 'If this shows another interface, use the gateway address in the client Wi-Fi properties.',
                    ),
                    const SizedBox(height: 20),
                    SelectableText(text),
                    const SizedBox(height: 20),
                    Text(
                      ru
                          ? 'Проверка: сравните внешний IP клиента и VPN-сервера, проверьте DNS. Без root прокси не перехватывает весь трафик: неподдерживающие его приложения, UDP и системный DNS могут идти напрямую. Не отключайте пароль на общедоступной сети. Отключайте раздачу в приложении после использования.'
                          : 'Check the client external IP against the VPN server and test DNS. Without root, proxy sharing does not capture all traffic: unsupported apps, UDP and system DNS may go direct. Keep a password on public networks. Disable app sharing when finished.',
                    ),
                  ],
                ),
              )
              .toList(),
        ),
      ),
    );
  }
}
