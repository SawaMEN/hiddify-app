# Совместимость клиента с SawaMEN/3x-ui

Аудит 2026-10-09. Исходная панель: `cf730af652387a2e99fa1cda20674b038a6edfdf`.
Исходный клиент: `2d6622f4`. Проверены код панели, генераторы подписок,
реестр транспортов закреплённого ядра и фактические преобразователи ссылок.

Это **не полная реализация всех протоколов панели**. Панель содержит 25 типов
inbound, включая служебные входы и протоколы отдельных внешних процессов.
Добавлены отсутствовавшие пути импорта для уже реализованных транспортов;
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
| Sudoku | Не реализован | Требуется отдельный транспорт Sudoku, а не только декодирование `sudoku://` |
| FPTN | Не реализован | `fptn:<base64>`; отдельная реализация протокола и проверки сертификата |
| OpenFlux | Не реализован | `openflux://v1/<deflate+base64url>`; нужен OpenFlux transport/codec |
| VK TURN Proxy | Не реализован | Панель выдаёт **`wingsv://`**, protobuf + DEFLATE; нужен TURN и внешний WireGuard-транспорт |
| PingTunnel | Не реализован | Отдельный ICMP-транспорт; отсутствует в штатном генераторе подписки панели |

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
