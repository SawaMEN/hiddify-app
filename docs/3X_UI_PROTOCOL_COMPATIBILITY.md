# Совместимость клиента с SawaMEN/3x-ui

Аудит 2026-10-09. Исходная панель: `cf730af652387a2e99fa1cda20674b038a6edfdf`.
Исходный клиент: `2d6622f4`. Проверены код панели, генераторы подписок,
реестр транспортов закреплённого ядра и фактические преобразователи ссылок.

Это **не полная реализация всех протоколов панели**. Панель содержит 25 типов
inbound, включая служебные входы и протоколы отдельных внешних процессов.
Добавлены пути импорта и native-транспорты Sudoku, FPTN, OpenFlux и PingTunnel;
AmneziaWG обновлён до совместимой с панелью версии wire-протокола.

| Тип панели | Поддержка в клиенте после изменений | Формат / ограничение |
|---|---|---|
| VMess | Существующая | `vmess://`, JSON; доступность зависит от транспорта |
| VLESS | Существующая + исправление | `vless://`, TLS/REALITY, Vision; параметр `encryption` теперь сохраняется |
| Trojan | Существующая | `trojan://`, JSON |
| Shadowsocks | Существующая | `ss://`, JSON; индивидуальные пароли сохраняются преобразователем |
| WireGuard | Существующая | `wireguard://`, `wg://`, native endpoint JSON |
| Hysteria | Существующая | В этой панели это **Hysteria 2**, ссылки `hysteria2://` / `hy2://` |
| TUIC | Существующая | `tuic://`, JSON |
| AmneziaWG | Исправлена и расширена | `vpn://` с base64url `.conf`, `awg://`, endpoint JSON; AWG 2/3, S3/S4, H1–H4, I1–I5, HeaderProtectionKey и расширенные настройки |
| Snell | Исправлена | `snell://`, native JSON; клиент v4 для серверного расширения v5 и клиент v6; сохранён `userkey` |
| Mieru | Исправлена | Полный protobuf `mieru://` и профиль `mierus://`; повторяющиеся порты/протоколы, диапазоны, handshake, multiplexing, MTU |
| TrustTunnel | Добавлен импорт | `tt://?` TLV и `trusttunnel://`; HTTP/2 и HTTP/3, SNI и DER-цепочка сертификатов |
| NaiveProxy | Существующая + исправление | `naive://`, `naive+https://`, `naive+quic://`; `quic=0` больше не включает QUIC |
| AnyTLS | Существующая | `anytls://`, JSON; добавлен корректный шаблон редактора |
| ShadowTLS | Существующая + исправление подписок | Native sing-box JSON с внутренним outbound и `detour`; самостоятельной ссылки для пары слоёв панель не выдаёт |
| MASQUE | Исправлен импорт подписок | `masque-client` в `endpoints`; полный JSON панели, включая массив отдельных конфигураций |
| HTTP | Есть HTTP outbound в ядре | Панель не включает этот inbound в клиентскую VPN-подписку; ручной JSON / proxy URI |
| Mixed | Есть SOCKS/HTTP outbound в ядре | Это серверный объединённый SOCKS/HTTP-вход, не отдельный VPN-протокол |
| Tunnel | Служебный серверный inbound | Не является самостоятельным удалённым VPN-протоколом |
| TUN | Локальный сетевой интерфейс | Клиент уже использует Android VpnService; не импортируется как сервер |
| MTProto | Не является VPN для устройства | `tg://proxy` предназначен для Telegram; выдаётся понятная ошибка |
| Sudoku | Реализован | `sudoku://`, native JSON; KIP handshake, AEAD, appearance tables, packed/pure downlink, HTTPMask, multiplex и UDP-over-TCP |
| FPTN | Реализован | `fptn:<base64>`, native JSON; TLS2 obfuscation / SNI decoy, login/JWT, Protobuf WebSocket IP tunnel, IPv4/IPv6, обязательный pin сертификата |
| OpenFlux | Реализован | `openflux://v1/<deflate+base64url>`, native JSON; OpenFlux 0.3.0 session negotiation, AES-GCM, batched codec, direct/yandex/vyandex/boards/mailru; IPv4 |
| VK TURN Proxy | Не реализован | Панель выдаёт **`wingsv://`**, protobuf + DEFLATE; нужен TURN и внешний WireGuard-транспорт |
| PingTunnel | Реализован | Native JSON и `pingtunnel://host?key=123`; ICMP + reliability/SOCKS TCP/UDP; панель не выдаёт share URI |

## Реальные ошибки, устранённые в этой работе

- AWG-библиотека клиента не принимала S3/S4. Добавлен официальный
  `amneziawg-go/v3 v3.1.20260828`, закреплённый и в панели. Оставлен совместимый
  с sing-box gVisor-стек с адаптером событий TUN; сокеты по-прежнему открываются
  через существующий dialer, необходимый Android VPN.
- Добавлены AWG 3 настройки и перевод base64 HeaderProtectionKey в hex UAPI.
  `on/off` из `.conf` преобразуются в булевы значения, принимаемые библиотекой.
- IPv6 endpoint AWG теперь записывается с квадратными скобками. Включён ранее
  пропущенный тест, который передаёт этот endpoint реальному AWG device.
- `vpn://` не требует второго системного TUN: импорт создаёт userspace endpoint.
- Snell-парсер сохраняет `userkey`, версию, режим v6 и параметры obfs v4.
- Mieru protobuf декодируется по активному профилю; сохраняются серверы,
  повторяющиеся transport bindings и MTU. В native Mieru outbound добавлено
  поле `mtu` и его передача в конфигурацию библиотеки.
- TrustTunnel TLV разбирается с проверкой границ, версии и обязательных полей;
  неизвестные теги пропускаются согласно официальной спецификации. DER
  сертификаты преобразуются в PEM без автоматического отключения проверки TLS.
- Массив полных sing-box конфигураций больше не воспринимается как массив
  outbounds. Теги и detour-ссылки получают отдельные пространства имён;
  MASQUE остаётся endpoint. Proxy-only импорт использует DNS приложения
  вместо ссылок на отброшенные DNS-серверы исходного документа.
- Смешанная URI-подписка не теряет неподдерживаемые строки молча. Ошибка
  сообщает номер строки и протокол; исходные ссылки и пароли не печатаются.
- Android обрабатывает прямые ссылки поддерживаемых протоколов. Предпросмотр
  правильно считает серверы в JSON-массивах; редактор получил native шаблоны.

## Новые транспорты

Sudoku использует закреплённый snapshot upstream с сохранением лицензии. Персональный
private key преобразуется в public seed по upstream-алгоритму. Dialer и HTTPMask
подключены к защищённым сокетам ядра, включая multiplex и UDP-over-TCP.
Outbound владеет открытыми соединениями: остановка закрывает активные чтения,
а завершение handshake одновременно с остановкой не оставляет сокет открытым.

FPTN реализован внутри Go-ядра. Сохраняются TLS Session ID timestamp markers,
TLS2 XOR/padding, переходы обфускации между handshake и HTTP, SNI decoy, JWT и
формат Protobuf batch/IP assignment. Панель закрепляет сервер 0.4.4 и клиент 0.4.6;
у обеих версий сервер принимает Protobuf при отсутствии `X-Serializer: yaff`.
MD5 здесь применяется только для точного сравнения сертификата с pin из токена,
по формату FPTN. Нет режима пропуска этой проверки. Оба списка серверов токена
импортируются с удалением одинаковых endpoints.

OpenFlux использует snapshot 0.3.0 — тот же, что панель. Все HTTP/WebSocket/TCP
carrier-сокеты используют отдельный dialer профиля, без изменения глобальных
сетевых настроек процесса. IP-стек изолирован; системный TUN и root не требуются.
Публичные каналы yandex/vyandex/boards/mailru доступны; интерактивный вход в
аккаунты и WebView для человеческой CAPTCHA не реализованы. Direct не зависит
от этих сервисов. IPv6 отсутствует в upstream 0.3.0 и отклоняется явно.

PingTunnel использует upstream framing, reliability, SOCKS и encryption с
исправлениями гонок счётчиков, lifecycle-флагов и времён активности. Android
открывает ICMP datagram socket и вызывает VpnService protection до использования.
Если устройство запрещает такой сокет, соединение завершается явной ошибкой;
root/raw fallback на Android не запускается. Linux требует CAP_NET_RAW.
ICMP нельзя провести через обычный TCP/UDP detour, такие настройки отклоняются.
Локальный SOCKS listener передаётся клиенту без освобождения порта между
резервированием и запуском; ошибка запуска listener закрывает ICMP-сокет.
Ключ панели и параметры `encrypt`/`encrypt_key` соответствуют JSON-полям клиента
`key`/`encryption`/`encryption_key`. Пример ручной ссылки:
`pingtunnel://server.example?key=123&encrypt=chacha20&encrypt_key=YOUR_SECRET`.

## Ограничения

TrustTunnel `subscription_url` deep links требуют отдельного механизма загрузки
TrustTunnel-подписки; используйте статические параметры endpoint. Его anti-DPI
и client-random-prefix пока не поддерживаются: импорт явно отклоняет их.
DNS-поля deep link не заменяют DNS-политику приложения.

Массив независимых полных конфигураций импортируется в режиме proxy-only.
Режим full-config отклоняет такой массив, поскольку разные DNS/routing
политики нельзя незаметно объединить. Для него импортируйте одну конфигурацию.

TCP/raw, WebSocket, gRPC, HTTPUpgrade и XHTTP уже присутствуют в преобразователе.
mKCP не реализован этим преобразователем и не заявляется как поддерживаемый.
Поддержка протокола не означает, что любая его серверная комбинация транспорта
автоматически совместима с клиентом.

## Проверка

- Прошли пакеты Go: `v2/config`, `v2/config/paneluri`, native AWG protocol/transport
  и Mieru, с тегами `with_awg,with_clash_api` и `-ldflags=-checklinkname=0`.
- Прошёл реальный локальный обмен TCP и UDP через AWG 3 с S3/S4,
  HeaderProtectionKey и ContentPaddingAddition.
- Прошли проверки гонок данных AWG/Mieru и компиляция изменённых Go-транспортов
  для Android ARM64 (`GOOS=android GOARCH=arm64 CGO_ENABLED=0`).
- TCP/UDP loopback Sudoku: upstream handshake, AEAD, pure и packed downlink.
- TCP/UDP и unconnected UDP loopback OpenFlux: negotiated encrypted direct session.
- TCP/UDP и unconnected UDP loopback FPTN: независимый серверный TLS2 framing,
  TLS с обязательным pin, login/JWT, Protobuf IP assignment/batches; неверные
  credentials и certificate pin отклоняются.
- PingTunnel TCP/UDP: upstream клиент и сервер с сохранением ICMP packet framing.
  В этой среде CAP_NET_RAW отсутствует, поэтому packet IO теста заменён локальным
  datagram каналом. Подключение к настоящему внешнему ICMP-серверу не проверено.
- Race checks новых четырёх адаптеров и Go vet пройдены.
- Регрессии lifecycle: остановка Sudoku прерывает активное чтение и отклоняет
  новые соединения; ошибка запуска PingTunnel не оставляет packet socket открытым.
- TrustTunnel fuzz: 7 229 входов, без падений.
- Прошли 9 Kotlin/JUnit тестов импорта, с компиляцией production-файлов Kotlin
  2.4.20. Gradle wrapper не смог скачать distribution из этой среды, поэтому
  для этих тестов использован напрямую тот же Kotlin compiler и JUnit.
- `tool/check_native_project.py`: ресурсы и граница Kotlin/Android корректны.
- APK не собирался. Подключения к реальным пользовательским серверам и работа
  на физическом Android не проверены; доступные credentials не предоставлялись.

## Источники

- Панель: `internal/database/model/model.go`, `internal/sub/service.go`,
  `internal/sub/snell_link.go`, `internal/sub/mieru_link.go`,
  `internal/sub/singbox_subscription.go`, `internal/externalvpn/additional.go`,
  `internal/externalvpn/manager.go`, `internal/web/service/vk_turn_proxy_service.go`.
- Клиент: закреплённые `hiddify-core/ray2sing` и
  `hiddify-core/hiddify-sing-box/include/registry.go`.
- [TrustTunnel Deep Link Specification](https://github.com/TrustTunnel/TrustTunnel/blob/master/DEEP_LINK.md).
- [AmneziaWG UAPI](https://github.com/amnezia-vpn/amneziawg-go/blob/v3.1.20260828/device/uapi.go).

- [Sudoku upstream](https://github.com/SUDOKU-ASCII/sudoku).
- [OpenFlux 0.3.0](https://github.com/p1neappleXpress/OpenFlux/tree/v0.3.0).
- [FPTN 0.4.6](https://github.com/fptn-project/fptn/tree/0.4.6).
- [PingTunnel upstream](https://github.com/esrrhs/pingtunnel).
