/// Bilingual guide content and illustrative settings screens, independent of UI.
class SharingGuideSection {
  const SharingGuideSection(this.title, this.steps, {this.screenTitle, this.fields = const {}});
  final String title;
  final List<String> steps;
  final String? screenTitle;
  final Map<String, String> fields;
}

List<SharingGuideSection> sharingGuide(int platform, bool ru, String host, int port) {
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
  final app = SharingGuideSection(
    t('Подготовьте раздатчик в приложении', 'Prepare the host in the app'),
    [
      t(
        'Раздатчик — устройство с запущенным VPN. Клиент — устройство, которое подключается к его Wi-Fi. Вкладку выбирайте по ОС устройства, которое сейчас настраиваете; ОС раздатчика и клиента могут отличаться.',
        'The host runs the VPN. The client joins its Wi-Fi. Choose the tab for the device you are configuring; host and client can use different operating systems.',
      ),
      t(
        'Проверьте, что на раздатчике работает интернет. В основных настройках приложения включите «Раздача Wi-Fi через VPN». Подключите VPN и дождитесь успешного подключения.',
        'Check the host internet connection. In the app general settings enable VPN Wi-Fi sharing. Connect the VPN and wait for a successful connection.',
      ),
      t(
        'В обычном режиме приложение открывает HTTP/SOCKS5-прокси. Системную точку доступа включите отдельно по шагам ниже. Переключатель приложения сам Wi-Fi сеть не создаёт.',
        'In normal mode the app opens an HTTP/SOCKS5 proxy. Enable the system hotspot separately using the steps below. The app switch does not create a Wi-Fi network.',
      ),
      t(
        'Запишите порт, имя hiddify и пароль из карточки выше. Пароль Wi-Fi и пароль прокси — два разных пароля. Не вводите адрес VPN-сервера в поле IP прокси.',
        'Note the port, username hiddify and password in the card above. The Wi-Fi password and proxy password are different. Do not enter the remote VPN server address as the proxy IP.',
      ),
    ],
    screenTitle: t('Приложение → Основные настройки', 'App → General settings'),
    fields: {
      t('Раздача Wi-Fi через VPN', 'VPN Wi-Fi sharing'): on,
      'VPN': t('Подключён', 'Connected'),
      t('Порт прокси', 'Proxy port'): '$port',
    },
  );
  final address = SharingGuideSection(
    t('Найдите правильный IP раздатчика', 'Find the correct host IP'),
    [
      t(
        'Подключите клиент к созданной сети Wi-Fi. Нажмите «Обновить IP раздатчика» выше. Приложение может показать адрес другого сетевого интерфейса, поэтому сравните его со шлюзом сети клиента.',
        'Join the host Wi-Fi on the client. Tap Refresh host IP above. The app may show another interface address, so compare it with the client network gateway.',
      ),
      t(
        'Нужен локальный адрес раздатчика в сети клиента. Для прямого подключения к точке доступа это обычно «Шлюз» или «Маршрутизатор». Если оба устройства подключены к общему роутеру, нужен LAN IP самого раздатчика, а не адрес роутера.',
        'Use the host local address in the client network. For a direct hotspot connection it is usually Gateway or Router. When both devices join an existing router, use the host LAN IP, not the router address.',
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
  late final SharingGuideSection hotspot;
  late final SharingGuideSection client;
  SharingGuideSection? extra;
  switch (platform) {
    case 0:
      hotspot = SharingGuideSection(
        t('Android: включите точку доступа', 'Android: enable the hotspot'),
        [
          t(
            'Откройте Настройки → Сеть и Интернет → Точка доступа и модем → Точка доступа Wi-Fi. На Samsung путь может называться «Подключения → Мобильная точка доступа и модем». У других производителей найдите «Точка доступа» поиском настроек.',
            'Open Settings → Network & internet → Hotspot & tethering → Wi-Fi hotspot. Samsung may use Connections → Mobile Hotspot and Tethering. On other devices search Settings for hotspot.',
          ),
          t(
            'Задайте понятное имя сети (например, VPN-WiFi), защиту WPA2/WPA3 и отдельный пароль Wi-Fi. Включите точку доступа. Если клиент не видит сеть, попробуйте диапазон 2,4 ГГц.',
            'Choose a network name (for example VPN-WiFi), WPA2/WPA3 security and a separate Wi-Fi password. Turn on the hotspot. Try the 2.4 GHz band if the client cannot see it.',
          ),
          t(
            'На клиенте выберите эту сеть и введите пароль Wi-Fi. Для раздачи из мобильного интернета включите мобильные данные. Раздача из входящего Wi-Fi поддерживается не на всех телефонах.',
            'On the client select this network and enter the Wi-Fi password. Enable mobile data when sharing cellular internet. Sharing an incoming Wi-Fi connection is not supported on every phone.',
          ),
        ],
        screenTitle: t('Android → Точка доступа Wi-Fi', 'Android → Wi-Fi hotspot'),
        fields: {
          t('Точка доступа', 'Hotspot'): on,
          t('Имя сети', 'Network name'): 'VPN-WiFi',
          t('Защита', 'Security'): 'WPA2 / WPA3',
          t('Пароль Wi-Fi', 'Wi-Fi password'): '••••••••',
        },
      );
      client = SharingGuideSection(
        t('Android-клиент: обычный режим без root', 'Android client: normal mode without root'),
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
      extra = SharingGuideSection(
        t('Android-раздатчик: вариант с root', 'Android host: root mode'),
        [
          t(
            'Этот вариант только для устройства, где root уже установлен. В дополнительных настройках приложения включите root-режим и подтвердите запрос su в менеджере root. Установка root не входит в эти действия.',
            'This option is for a device that already has root. Enable root mode in the app additional settings and grant its su request in your root manager. These steps do not install root.',
          ),
          t(
            'Оставьте включёнными системную точку доступа и «Раздача Wi-Fi через VPN». Переподключите VPN после смены режима. Дождитесь успешного запуска; ошибка TUN/netfilter означает, что перехват не запущен.',
            'Keep the system hotspot and VPN Wi-Fi sharing enabled. Reconnect the VPN after changing mode. Wait for successful startup; a TUN/netfilter error means interception has not started.',
          ),
          t(
            'На клиенте отключите вручную заданный прокси (Прокси → Нет / Выкл.) и подключитесь к точке доступа. В этом режиме ядро перехватывает трафик клиентов; логин прокси вводить не нужно. Применяются текущие правила маршрутизации, включая исключения напрямую.',
            'On the client disable the manually configured proxy (Proxy → None / Off) and join the hotspot. The core intercepts client traffic in this mode; no proxy login is needed. Current routing rules, including direct exceptions, still apply.',
          ),
          t(
            'Проверьте IP и DNS на клиенте. Если трафик идёт напрямую, проверьте выданные root-права, переподключите VPN и попробуйте обычный режим с прокси. Автоперехват зависит от ядра и прошивки устройства.',
            'Check the client IP and DNS. If traffic goes direct, check root permission, reconnect the VPN and try normal proxy mode. Automatic interception depends on the device kernel and firmware.',
          ),
        ],
        screenTitle: t('Раздатчик с root → Клиент без прокси', 'Root host → Client without proxy'),
        fields: {
          t('Root-режим на раздатчике', 'Host root mode'): on,
          t('Разрешение su', 'su permission'): t('Разрешено', 'Granted'),
          t('VPN после переподключения', 'VPN after reconnect'): t('Подключён', 'Connected'),
          t('Прокси на клиенте', 'Client proxy'): t('Нет / Выкл.', 'None / Off'),
        },
      );
    case 1:
      hotspot = SharingGuideSection(
        t('Windows-раздатчик: мобильный хот-спот', 'Windows host: mobile hotspot'),
        [
          t(
            'Откройте Параметры → Сеть и Интернет → Мобильный хот-спот. В «Совместное использование интернет-соединения из» выберите работающий входящий интернет (Ethernet или Wi-Fi). Для обычного режима не требуется выбирать VPN-адаптер.',
            'Open Settings → Network & internet → Mobile hotspot. Under Share my internet connection from select a working incoming connection (Ethernet or Wi-Fi). Normal proxy mode does not require a VPN adapter here.',
          ),
          t(
            'Выберите раздачу через Wi-Fi. Нажмите «Изменить» в свойствах сети, задайте имя и пароль Wi-Fi, сохраните и включите мобильный хот-спот. Подключите клиент к этой сети.',
            'Select sharing over Wi-Fi. Edit network properties, set the Wi-Fi name and password, save and enable Mobile hotspot. Join this network on the client.',
          ),
          t(
            'Если брандмауэр запрашивает доступ, разрешите приложению входящие подключения в доверенной частной сети. Не выключайте брандмауэр целиком. В обычном режиме хот-спот сам по себе раздаёт входящий интернет; клиенту обязательно нужен прокси.',
            'If the firewall prompts, allow the app incoming connections on the trusted private network. Keep the firewall enabled. In normal mode the hotspot shares incoming internet; the client must use the proxy.',
          ),
        ],
        screenTitle: t('Windows → Мобильный хот-спот', 'Windows → Mobile hotspot'),
        fields: {
          t('Раздавать через', 'Share over'): 'Wi-Fi',
          t('Имя сети', 'Network name'): 'VPN-WiFi',
          t('Пароль Wi-Fi', 'Wi-Fi password'): '••••••••',
          t('Мобильный хот-спот', 'Mobile hotspot'): on,
        },
      );
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
            'IP точки доступа можно проверить командой ipconfig: в разделе адаптера Wi-Fi найдите «Основной шлюз». Для обоих устройств на общем роутере используйте IP раздатчика из его ipconfig.',
            'To check the hotspot IP run ipconfig and find Default Gateway under the client Wi-Fi adapter. For both devices on a shared router use the host IP from its ipconfig.',
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
      extra = SharingGuideSection(
        t('Если нужен прозрачный режим на Windows', 'If you need transparent sharing on Windows'),
        [
          t(
            'Root-переключатель приложения относится к Android. На Windows автоматический перехват хот-спота этим переключателем не настраивается. Для всего трафика нужны TUN, права администратора и совместимость Internet Connection Sharing с VPN-адаптером.',
            'The app root switch is for Android. It does not configure automatic hotspot interception on Windows. Forwarding all traffic requires TUN, administrator rights and compatible Internet Connection Sharing with the VPN adapter.',
          ),
          t(
            'В совместимой конфигурации откройте Win+R → ncpa.cpl → свойства VPN-адаптера → Доступ. Разрешите общий доступ и выберите адаптер хот-спота. Если VPN-адаптера или вкладки «Доступ» нет, используйте обычный режим. Эта настройка зависит от драйвера и может конфликтовать с мобильным хот-спотом.',
            'For a compatible setup open Win+R → ncpa.cpl → VPN adapter properties → Sharing. Enable sharing and choose the hotspot adapter. If the adapter or Sharing tab is absent, use normal mode. This depends on the driver and may conflict with Mobile hotspot.',
          ),
          t(
            'После изменений переподключите клиент, отключите его ручной прокси и обязательно проверьте IP и DNS. Работа хот-спота без ошибок ещё не подтверждает передачу через VPN.',
            'After changes reconnect the client, disable its manual proxy and verify IP and DNS. A working hotspot alone does not confirm VPN forwarding.',
          ),
        ],
      );
    case 2:
      hotspot = SharingGuideSection(
        t('Linux-раздатчик: точка доступа', 'Linux host: hotspot'),
        [
          t(
            'В GNOME откройте Настройки → Wi-Fi → меню → Включить точку доступа Wi-Fi. Если такого пункта нет, адаптер или его драйвер может не поддерживать режим точки доступа.',
            'In GNOME open Settings → Wi-Fi → menu → Turn On Wi-Fi Hotspot. If missing, the adapter or driver may not support hotspot mode.',
          ),
          t(
            'В KDE откройте настройки сетевых соединений и создайте Wi-Fi соединение в режиме «Точка доступа». Задайте имя сети, WPA2/WPA3, пароль и общий доступ IPv4 (shared). Включите соединение и подключите клиент.',
            'In KDE open network connection settings and create a Wi-Fi connection in Access Point mode. Set the network name, WPA2/WPA3, password and IPv4 shared mode. Activate it and connect the client.',
          ),
          t(
            'Раздатчик должен иметь рабочий входящий интернет. Один адаптер часто не умеет одновременно принимать Wi-Fi и раздавать сеть; при необходимости используйте Ethernet, мобильный модем или второй адаптер. Разрешите порт прокси только в доверенной LAN в вашем брандмауэре.',
            'The host needs working incoming internet. One adapter often cannot receive Wi-Fi and host a network simultaneously; use Ethernet, a cellular modem or a second adapter if needed. Allow the proxy port only on the trusted LAN in your firewall.',
          ),
        ],
        screenTitle: t('Linux → Wi-Fi → Точка доступа', 'Linux → Wi-Fi → Hotspot'),
        fields: {
          t('Режим', 'Mode'): t('Точка доступа', 'Access Point'),
          'SSID': 'VPN-WiFi',
          t('Защита', 'Security'): 'WPA2 / WPA3',
          'IPv4': t('Общий доступ (shared)', 'Shared'),
        },
      );
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
            'На клиенте выполните ip route: адрес после «default via» обычно является IP прямой точки доступа. Сравните с карточкой приложения. При общей сети через роутер используйте адрес самого раздатчика.',
            'Run ip route on the client: the address after default via is usually the direct hotspot IP. Compare it with the app card. On a shared router network use the host own address.',
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
      extra = SharingGuideSection(t('Прозрачная раздача на Linux', 'Transparent sharing on Linux'), [
        t(
          'Режим shared NetworkManager создаёт сеть и NAT, но сам не гарантирует передачу через VPN. Для прозрачного режима нужны TUN, IP forwarding, forwarding/NAT и policy routing в таблицу TUN с правами администратора.',
          'NetworkManager shared mode creates a network and NAT but does not guarantee VPN forwarding. Transparent mode requires administrator rights, TUN, IP forwarding, forwarding/NAT and policy routing to the TUN table.',
        ),
        t(
          'Имена интерфейсов, таблицы маршрутов и nftables/iptables отличаются между системами. Автоматической настройки этим переключателем для Linux нет; используйте обычный режим с прокси, если не настраиваете маршрутизацию вручную. При ручной настройке проверьте также IPv6 и DNS.',
          'Interface names, route tables and nftables/iptables differ between systems. This switch does not automatically configure Linux forwarding; use normal proxy mode unless you configure routing manually. Check IPv6 and DNS in any manual setup.',
        ),
      ]);
    case 3:
      hotspot = SharingGuideSection(
        t('macOS-раздатчик: Общий Интернет', 'macOS host: Internet Sharing'),
        [
          t(
            'Откройте Системные настройки → Основные → Общий доступ → Общий Интернет (кнопка информации / настройки). На старых версиях macOS: Системные настройки → Общий доступ.',
            'Open System Settings → General → Sharing → Internet Sharing (information / settings button). Older macOS versions use System Preferences → Sharing.',
          ),
          t(
            'Выберите работающий входящий интерфейс, например Ethernet. В списке «Для компьютеров, использующих» отметьте Wi-Fi. В параметрах Wi-Fi задайте имя сети, защиту WPA2/WPA3 и пароль. Включите Общий Интернет и подтвердите.',
            'Select a working incoming interface, such as Ethernet. Under To devices using select Wi-Fi. In Wi-Fi options set the network name, WPA2/WPA3 and password. Enable Internet Sharing and confirm.',
          ),
          t(
            'Один Wi-Fi адаптер обычно не может одновременно принимать Wi-Fi и раздавать его. При таком подключении используйте другой входящий интерфейс. Оставьте приложение и VPN запущенными; настройте прокси на клиенте. Общий Интернет сам не гарантирует VPN.',
            'One Wi-Fi adapter usually cannot receive Wi-Fi and share it simultaneously. Use a different incoming interface in that case. Keep the app and VPN running; configure the client proxy. Internet Sharing itself does not guarantee VPN forwarding.',
          ),
        ],
        screenTitle: t('macOS → Общий Интернет', 'macOS → Internet Sharing'),
        fields: {
          t('Общее подключение из', 'Share connection from'): 'Ethernet',
          t('Для устройств через', 'To devices using'): 'Wi-Fi',
          t('Имя сети', 'Network name'): 'VPN-WiFi',
          t('Общий Интернет', 'Internet Sharing'): on,
        },
      );
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
      hotspot = SharingGuideSection(
        t('iPhone/iPad как раздатчик: ограничения', 'iPhone/iPad as host: limitations'),
        [
          t(
            'iOS раздаёт сотовое подключение через Настройки → Режим модема → Разрешать другим. Задайте пароль Wi-Fi. Этот системный режим сам по себе не направляет подключённых клиентов через VPN приложения.',
            'iOS shares cellular data through Settings → Personal Hotspot → Allow Others to Join. Set the Wi-Fi password. This system mode itself does not route connected clients through the app VPN.',
          ),
          t(
            'iOS не предоставляет этому приложению способ прозрачно перехватить трафик режима модема. Надёжный вариант: используйте Android, Windows, Linux или macOS как раздатчик, а iPhone/iPad как клиент по инструкции ниже.',
            'iOS does not give this app a way to transparently intercept Personal Hotspot traffic. The reliable option is to use Android, Windows, Linux or macOS as the host and iPhone/iPad as the client below.',
          ),
          t(
            'Доступ к прокси приложения в LAN зависит от iOS и локальных разрешений. При экспериментальной раздаче оставьте приложение открытым, разрешите доступ к локальной сети при запросе и проверьте доступность IP и порта. При блокировке экрана или уходе в фон соединение может прерваться; работа не гарантируется.',
            'App LAN proxy access depends on iOS and local permissions. For experimental sharing keep the app open, grant local network access when prompted and test IP and port reachability. Locking the screen or backgrounding may interrupt it; availability is not guaranteed.',
          ),
        ],
        screenTitle: t('iOS → Режим модема', 'iOS → Personal Hotspot'),
        fields: {
          t('Разрешать другим', 'Allow Others to Join'): on,
          t('Пароль Wi-Fi', 'Wi-Fi password'): '••••••••',
          t('VPN для клиентов автоматически', 'Automatic VPN for clients'): t('Не поддерживается', 'Not supported'),
        },
      );
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
  return [app, hotspot, address, client, if (extra != null) extra];
}
