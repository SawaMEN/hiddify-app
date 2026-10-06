/// Bilingual guide content and illustrative settings screens, independent of UI.
class SharingGuideSection {
  const SharingGuideSection(this.title, this.steps, {this.screenTitle, this.fields = const {}});
  final String title;
  final List<String> steps;
  final String? screenTitle;
  final Map<String, String> fields;
}

List<SharingGuideSection> sharingGuide(int platform, bool ru, String host, int port, {bool root = false}) {
  String t(String russian, String english) => ru ? russian : english;
  final on = t('Включено', 'On');
  final manual = t('Вручную', 'Manual');
  final auth = {
    t('Сервер / IP', 'Server / IP'): host,
    t('Порт', 'Port'): '$port',
    t('Аутентификация', 'Authentication'): on,
    t('Пользователь', 'Username'): 'hiddify',
    t('Пароль прокси', 'Proxy password'): t('Из карточки выше', 'From the card above'),
  };
  final connect = SharingGuideSection(
    t('Подключитесь к Wi-Fi телефона', 'Join the phone Wi-Fi'),
    [
      t(
        'На устройстве, которое получает интернет, откройте список Wi-Fi сетей. Выберите точку доступа этого телефона (например, VPN-WiFi) и введите пароль Wi-Fi, заданный на телефоне.',
        'On the device receiving internet open the Wi-Fi network list. Select this phone hotspot (for example VPN-WiFi) and enter the Wi-Fi password set on the phone.',
      ),
      t(
        'Телефон с приложением должен уже раздавать Wi-Fi, а VPN и «Раздача Wi-Fi через VPN» должны быть включены. Приложение на получающем устройстве устанавливать не нужно. Выберите вкладку ОС получающего устройства.',
        'This phone must already have its hotspot, VPN and VPN Wi-Fi sharing enabled. You do not need to install this app on the receiving device. Choose the receiving device OS tab.',
      ),
      t(
        'Пока проверяете подключение, не переключайте получающее устройство на мобильные данные или другую Wi-Fi сеть. Если на нём работает свой VPN, отключите его для проверки.',
        'While testing, keep the receiving device on the phone Wi-Fi rather than cellular data or another network. Disable its own VPN for this check if one is running.',
      ),
    ],
    screenTitle: t('Получающее устройство → Wi-Fi', 'Receiving device → Wi-Fi'),
    fields: {
      t('Сеть телефона', 'Phone network'): 'VPN-WiFi',
      t('Состояние', 'Status'): t('Подключено', 'Connected'),
      t('Пароль Wi-Fi', 'Wi-Fi password'): t('Пароль точки доступа телефона', 'Phone hotspot password'),
    },
  );
  final proxyPath = [
    t(
      'Android: Настройки → Wi-Fi → сеть телефона → Изменить → Прокси → Нет.',
      'Android: Settings → Wi-Fi → phone network → Edit → Proxy → None.',
    ),
    t(
      'Windows: Параметры → Сеть и Интернет → Прокси → Использовать прокси-сервер → Выкл.',
      'Windows: Settings → Network & internet → Proxy → Use a proxy server → Off.',
    ),
    t(
      'Linux: Настройки → Сеть → Сетевой прокси → Отключено. Также отключите прокси в настройках используемых программ.',
      'Linux: Settings → Network → Network Proxy → Disabled. Also disable proxies in your apps.',
    ),
    t(
      'macOS: Системные настройки → Сеть → Wi-Fi → Подробнее → Прокси. Отключите веб-прокси HTTP/HTTPS и SOCKS-прокси.',
      'macOS: System Settings → Network → Wi-Fi → Details → Proxies. Disable HTTP/HTTPS web proxies and SOCKS Proxy.',
    ),
    t(
      'iOS: Настройки → Wi-Fi → ⓘ рядом с сетью телефона → Настройка прокси → Выкл.',
      'iOS: Settings → Wi-Fi → ⓘ next to the phone network → Configure Proxy → Off.',
    ),
  ][platform];
  if (root) {
    return [
      connect,
      SharingGuideSection(
        t('На телефоне включён root-режим', 'The phone uses root mode'),
        [
          t(
            'В этом режиме телефон сам направляет трафик точки доступа по правилам VPN. На получающем устройстве достаточно подключиться к Wi-Fi; вручную задавать HTTP/SOCKS5-прокси и вводить логин не нужно.',
            'In this mode the phone routes hotspot traffic according to its VPN rules. On the receiving device simply join Wi-Fi; you do not need to set an HTTP/SOCKS5 proxy or enter a proxy login.',
          ),
          t(
            'Если ранее вы настраивали прокси этой сети, отключите его по пути ниже. Прокси, заданный отдельно внутри браузера или другой программы, тоже отключите.',
            'If you previously configured a proxy for this network, disable it using the path below. Also disable any proxy set separately inside your browser or another app.',
          ),
          proxyPath,
          t(
            'После подключения проверьте внешний IP и DNS. Если интернет не работает или идёт напрямую, попросите проверить VPN и root-раздачу на телефоне. Текущие исключения маршрутизации телефона продолжают действовать.',
            'After joining check the external IP and DNS. If internet fails or goes direct, check the phone VPN and root sharing. The phone routing exceptions still apply.',
          ),
        ],
        screenTitle: t('Получающее устройство → Прокси', 'Receiving device → Proxy'),
        fields: {
          t('Wi-Fi телефона', 'Phone Wi-Fi'): t('Подключено', 'Connected'),
          t('Ручной прокси', 'Manual proxy'): t('Нет / Выкл.', 'None / Off'),
          t('Логин и пароль прокси', 'Proxy credentials'): t('Не требуются', 'Not required'),
        },
      ),
    ];
  }
  final address = SharingGuideSection(
    t('Найдите правильный IP раздатчика', 'Find the correct host IP'),
    [
      t(
        'На подключённом устройстве откройте сведения о Wi-Fi сети телефона. Найдите «Шлюз» или «Маршрутизатор»: это локальный IP телефона, который нужно ввести в настройках прокси.',
        'On the connected device open details of the phone Wi-Fi network. Find Gateway or Router: this is the phone local IP to enter in proxy settings.',
      ),
      t(
        'Сверьте адрес с карточкой выше. Если приложение показало другой интерфейс телефона, используйте шлюз подключённой Wi-Fi сети. Не вводите внешний IP, адрес VPN-сервера или IP самого клиента.',
        'Compare with the card above. If the app shows another phone interface, use the connected Wi-Fi network gateway. Do not enter the public IP, remote VPN server address or client own IP.',
      ),
      t(
        'После пересоздания точки доступа IP может измениться. Введите новый адрес на клиенте. Порт берите из карточки приложения; одинаковый порт используется для HTTP и SOCKS5.',
        'Recreating the hotspot may change its IP. Update the client address. Use the port from the app card; HTTP and SOCKS5 share the same port.',
      ),
    ],
    screenTitle: t('Клиент → Сведения о Wi-Fi', 'Client → Wi-Fi details'),
    fields: {
      t('Сеть', 'Network'): 'VPN-WiFi',
      t('Шлюз / Маршрутизатор', 'Gateway / Router'): host,
      t('Это IP прокси при прямой раздаче', 'Proxy IP for a direct hotspot'): host,
    },
  );
  late final SharingGuideSection client;
  switch (platform) {
    case 0:
      client = SharingGuideSection(
        t('Android: настройте прокси', 'Android: configure the proxy'),
        [
          t(
            'Откройте Настройки → Wi-Fi → подключённая сеть → Изменить / значок карандаша → Дополнительные параметры → Прокси → Вручную. Названия пунктов зависят от прошивки.',
            'Open Settings → Wi-Fi → connected network → Edit / pencil → Advanced options → Proxy → Manual. Names vary by Android version and manufacturer.',
          ),
          t(
            'В поле «Имя хоста прокси» введите локальный IP раздатчика без http:// и без порта. В поле «Порт» введите число из карточки выше. Сохраните настройки.',
            'Enter the host local IP in Proxy hostname, without http:// or a port. Enter the number from the card above in Proxy port. Save the settings.',
          ),
          t(
            'Обычный экран Android чаще всего не имеет полей логина и пароля. Прокси приложения требует авторизацию: браузер может запросить hiddify и пароль при открытии сайта. Если запроса нет или браузер не подключается, настройте HTTP/SOCKS5 в клиенте с явной поддержкой авторизации или импортируйте SOCKS-ссылку из страницы LAN-прокси приложения.',
            'The Android settings screen usually has no username or password fields. The app proxy requires authentication: a browser may prompt for hiddify and the password when opening a site. If it never prompts or fails to connect, configure HTTP/SOCKS5 in a client with explicit authentication support, or import the SOCKS link from the app LAN proxy page.',
          ),
          t(
            'Этот системный прокси используют только совместимые приложения. Для SOCKS5 выбирайте удалённый DNS, если клиент предлагает такую настройку. Не считайте весь трафик телефона защищённым только по значку Wi-Fi.',
            'Only compatible apps use this system proxy. For SOCKS5 select remote DNS when offered. The Wi-Fi icon does not mean all phone traffic is protected.',
          ),
        ],
        screenTitle: t('Android → Изменить сеть → Прокси', 'Android → Edit network → Proxy'),
        fields: {
          t('Прокси', 'Proxy'): manual,
          t('Имя хоста прокси', 'Proxy hostname'): host,
          t('Порт прокси', 'Proxy port'): '$port',
          t('Логин / пароль', 'Username / password'): t('В браузере или прокси-клиенте', 'In browser or proxy client'),
        },
      );
    case 1:
      client = SharingGuideSection(
        t('Windows-клиент: настройте HTTP-прокси', 'Windows client: configure HTTP proxy'),
        [
          t(
            'Подключитесь к Wi-Fi раздатчика. Откройте Параметры → Сеть и Интернет → Прокси → Настройка прокси вручную → Настроить (в Windows 10 — «Использовать прокси-сервер»).',
            'Join the host Wi-Fi. Open Settings → Network & internet → Proxy → Manual proxy setup → Set up (Windows 10: Use a proxy server).',
          ),
          t(
            'Включите «Использовать прокси-сервер». Введите локальный IP раздатчика и порт из карточки, сохраните. На этом экране нет полей авторизации: при запросе браузера укажите hiddify и пароль прокси.',
            'Enable Use a proxy server. Enter the host local IP and the port from the card, then save. This screen has no authentication fields: enter hiddify and the proxy password when the browser prompts.',
          ),
          t(
            'Если программа игнорирует системный прокси или не запрашивает пароль, задайте HTTP или SOCKS5 прямо в её настройках. Укажите те же IP, порт, пользователя и пароль. Для SOCKS5 включите DNS через прокси.',
            'If an app ignores the system proxy or cannot prompt for a password, configure HTTP or SOCKS5 in that app. Use the same IP, port, username and password. Enable DNS through the proxy for SOCKS5.',
          ),
          t(
            'IP точки доступа можно проверить командой ipconfig: в разделе адаптера Wi-Fi найдите «Основной шлюз». Не берите адрес компьютера из строки IPv4: нужен адрес телефона из «Основной шлюз».',
            'To check the hotspot IP run ipconfig and find Default Gateway under the client Wi-Fi adapter. Do not use the computer IPv4 address: use the phone address shown as Default Gateway.',
          ),
        ],
        screenTitle: t('Windows → Прокси → Вручную', 'Windows → Proxy → Manual'),
        fields: {
          t('Использовать прокси-сервер', 'Use a proxy server'): on,
          t('IP-адрес прокси', 'Proxy IP address'): host,
          t('Порт', 'Port'): '$port',
          t('Авторизация', 'Authentication'): t('По запросу браузера', 'When the browser prompts'),
        },
      );
    case 2:
      client = SharingGuideSection(
        t('Linux-клиент: прокси и DNS', 'Linux client: proxy and DNS'),
        [
          t(
            'Подключитесь к Wi-Fi раздатчика. В GNOME откройте Настройки → Сеть → Сетевой прокси → Вручную; в KDE найдите «Прокси» в настройках системы. Укажите IP раздатчика и одинаковый порт для HTTP и HTTPS.',
            'Join the host Wi-Fi. In GNOME open Settings → Network → Network Proxy → Manual; in KDE find Proxy in System Settings. Set the host IP and the same port for HTTP and HTTPS.',
          ),
          t(
            'Не все программы используют настройки окружения. При отсутствии авторизации настройте прокси в самой программе. Например, в Firefox найдите «прокси» поиском настроек → Настроить → Ручная настройка прокси. Укажите HTTP-прокси и тот же адрес для HTTPS. При запросе введите hiddify и пароль. Для SOCKS5 нужен клиент, где можно явно указать имя и пароль; обычного поля SOCKS-узла недостаточно.',
            'Not all programs use desktop proxy settings. If authentication is unavailable, configure the app itself. For example in Firefox search Settings for proxy → Settings → Manual proxy configuration. Set the HTTP proxy and use the same address for HTTPS. When prompted enter hiddify and the password. For SOCKS5 use a client with explicit username and password support; a SOCKS Host field alone is insufficient.',
          ),
          t(
            'Для консольных клиентов, поддерживающих его, socks5h означает DNS на стороне прокси; socks5 может разрешать имена локально. Пароль передавайте безопасным способом, без публикации в истории команд или снимках экрана.',
            'For compatible command-line clients socks5h resolves DNS through the proxy; socks5 may resolve locally. Supply the password securely without publishing it in command history or screenshots.',
          ),
          t(
            'На клиенте выполните ip route: адрес после «default via» обычно является IP прямой точки доступа. Сравните с карточкой приложения. Не путайте его с IP самого компьютера.',
            'Run ip route on the client: the address after default via is usually the direct hotspot IP. Compare it with the app card. Do not confuse it with the computer own IP.',
          ),
        ],
        screenTitle: t('Linux → Сетевой прокси / приложение', 'Linux → Network proxy / app'),
        fields: {
          t('Метод', 'Method'): manual,
          'HTTP / HTTPS': '$host:$port',
          'SOCKS5': '$host:$port',
          t('DNS через SOCKS5', 'DNS through SOCKS5'): on,
          t('Пользователь', 'Username'): 'hiddify',
        },
      );
    case 3:
      client = SharingGuideSection(
        t('macOS-клиент: HTTP или SOCKS5', 'macOS client: HTTP or SOCKS5'),
        [
          t(
            'Подключитесь к Wi-Fi раздатчика. Откройте Системные настройки → Сеть → Wi-Fi → Подробнее → Прокси.',
            'Join the host Wi-Fi. Open System Settings → Network → Wi-Fi → Details → Proxies.',
          ),
          t(
            'Включите «Веб-прокси (HTTP)» и «Защищённый веб-прокси (HTTPS)». Для обоих введите одинаковые IP и порт раздатчика. Включите требование пароля и укажите hiddify и пароль прокси из карточки. Нажмите OK / Готово.',
            'Enable Web Proxy (HTTP) and Secure Web Proxy (HTTPS). Enter the same host IP and port for both. Enable password authentication and enter hiddify and the proxy password from the card. Click OK / Done.',
          ),
          t(
            'В качестве альтернативы используйте «SOCKS-прокси», если программа его поддерживает. Не включайте все типы сразу без необходимости; настройку удалённого DNS проверяйте в самой программе. Некоторые программы игнорируют системный прокси.',
            'Alternatively use SOCKS Proxy if the app supports it. Avoid enabling every type at once without a reason; check remote DNS in the app itself. Some apps ignore the system proxy.',
          ),
          t(
            'Адрес прямой точки доступа: Wi-Fi → Подробнее → TCP/IP → Маршрутизатор. После отключения от раздачи выключите введённые прокси, иначе на других сетях интернет может не работать.',
            'Direct hotspot address: Wi-Fi → Details → TCP/IP → Router. When done disable these proxies so they do not break internet access on other networks.',
          ),
        ],
        screenTitle: t('macOS → Wi-Fi → Прокси', 'macOS → Wi-Fi → Proxies'),
        fields: {
          t('Веб-прокси (HTTP)', 'Web Proxy (HTTP)'): on,
          t('Защищённый веб-прокси (HTTPS)', 'Secure Web Proxy (HTTPS)'): on,
          ...auth,
        },
      );
    default:
      client = SharingGuideSection(
        t('iPhone/iPad-клиент: ручной HTTP-прокси', 'iPhone/iPad client: manual HTTP proxy'),
        [
          t(
            'Подключитесь к Wi-Fi раздатчика. Откройте Настройки → Wi-Fi → кнопку ⓘ рядом с подключённой сетью. В разделе IPv4 запишите «Маршрутизатор»: для прямой точки доступа это IP раздатчика.',
            'Join the host Wi-Fi. Open Settings → Wi-Fi → ⓘ next to the connected network. Under IPv4 note Router: for a direct hotspot this is the host IP.',
          ),
          t(
            'Прокрутите до «Настройка прокси» → «Вручную». В поле «Сервер» введите IP раздатчика без http://; в поле «Порт» — число из карточки приложения.',
            'Scroll to Configure Proxy → Manual. In Server enter the host IP without http://; in Port enter the number from the app card.',
          ),
          t(
            'Включите «Аутентификация». Введите пользователя hiddify и пароль прокси из карточки. Это не пароль Wi-Fi. Нажмите «Сохранить», откройте Safari и проверьте подключение.',
            'Enable Authentication. Enter username hiddify and the proxy password from the card. This is not the Wi-Fi password. Tap Save, open Safari and check connectivity.',
          ),
          t(
            'Эта настройка относится к текущей Wi-Fi сети и HTTP-прокси. Она не защищает сотовый трафик и не заставляет все приложения использовать прокси. Системного поля SOCKS5 здесь нет. После использования верните «Настройка прокси → Выкл.».',
            'This setting applies to this Wi-Fi network and HTTP proxy. It does not protect cellular traffic or force all apps to use the proxy. There is no system SOCKS5 field here. When done restore Configure Proxy → Off.',
          ),
        ],
        screenTitle: t('iOS → Wi-Fi → ⓘ → Настройка прокси', 'iOS → Wi-Fi → ⓘ → Configure Proxy'),
        fields: {
          t('Настройка прокси', 'Configure Proxy'): manual,
          ...auth,
          t('Действие', 'Action'): t('Сохранить', 'Save'),
        },
      );
  }
  return [connect, address, client];
}
