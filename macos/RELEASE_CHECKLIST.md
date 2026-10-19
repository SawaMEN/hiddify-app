# VetrOFF macOS — проверочный список релиза (этап 3/3)

Результат этапа: **один** `VetrOFF-macOS-arm64.dmg` с подписью
Developer ID и нотариализацией Apple. Без реальных сертификатов и теста
VPN на физическом Apple Silicon этот документ не означает, что релиз готов.

## 1. Подготовить подписывающую команду Apple

- Developer Program должен разрешать `packet-tunnel-provider`.
- Создать `Developer ID Application` с доступным приватным ключом.
- Выпустить **два разных** provisioning profiles типа Developer ID,
  один для `app.vetroff.client.macos`, другой для
  `app.vetroff.client.macos.PacketTunnel`.
- Проверить в **обоих** профилях Network Extension
  `packet-tunnel-provider` и Keychain access group
  `<TEAM_ID>.app.vetroff.client.macos.shared`.
- Настроить Apple notarization App Store Connect API key с нужными правами.

## 2. Настроить защищённое окружение GitHub Actions

На GitHub создать Environment `macos-signing`, включить обязательное
одобрение доверенным ревьюером и ограничить запуск веткой `main`.
Следующие значения добавить как **Environment secrets** (не в код,
не в артефакты и не в текст ошибок):

| Secret | Содержимое |
| --- | --- |
| `MACOS_APPLE_TEAM_ID` | Team ID из десяти символов |
| `MACOS_CERTIFICATE_P12_BASE64` | Base64 от Developer ID Application .p12 |
| `MACOS_CERTIFICATE_PASSWORD` | Пароль экспортированного .p12 |
| `MACOS_APP_PROVISION_PROFILE_BASE64` | Base64 Developer ID профиля приложения |
| `MACOS_TUN_PROVISION_PROFILE_BASE64` | Base64 профиля Packet Tunnel |
| `MACOS_NOTARY_API_KEY_BASE64` | Base64 от App Store Connect .p8 API key |
| `MACOS_NOTARY_KEY_ID` | Notary API Key ID |
| `MACOS_NOTARY_ISSUER_ID` | App Store Connect issuer UUID |

Профили проходят проверку UUID, срока действия, Team ID,
Bundle ID, Network Extension и совпадения группы Keychain.
Сертификат импортируется во временный keychain CI и удаляется.
Скрипт прерывает выпуск, если подпись приложения **или расширения**
неверная либо не совпадают разрешения.

## 3. Собрать приватный тестовый установщик

После слияния кода выпуска в `main` вручную запустить workflow
`macOS Signed DMG (Developer ID + Notarization)`,
`distribution=private-test`. Результат — один DMG в закрытом артефакте
конкретного GitHub Actions run со сроком хранения семь дней, **не**
публичный GitHub Release.

Проверки до загрузки:
- `codesign --verify --deep --strict` для host app и Packet Tunnel;
- Hardened Runtime, Apple Team ID и разрешения подписки;
- macOS arm64 и валидность Bundle ID;
- `notarytool submit --wait` должен вернуть `Accepted`;
- `stapler staple`, `stapler validate`, `hdiutil verify`,
  `spctl` и контроль «ровно один файл DMG».

## 4. Подписанные испытания VPN на физическом Mac

Выполнить `macos/ON_DEVICE_TESTING.md`, сохранив результаты
**для точного 40-символьного SHA коммита**. Не публиковать секреты,
URI серверов, пароли и настоящие токены подписок.

Принять этап только после успешных тестов:
- TCP и UDP с настоящей передачей IP-пакетов через Go/libbox;
- DNS/IPv4/IPv6 без утечек, оба маршрута по умолчанию;
- минимум 50 циклов старт/остановка, отмена запуска;
- переход Wi-Fi↔Ethernet и сон/пробуждение;
- Keychain, профили, подписки и отсутствие секретов в диагностике;
- Gatekeeper при установке и открытии DMG на другом Mac.

Текущий флаг `VPNController.isCoreReady` возвращает `false` в Release.
Это **намеренная блокировка**, которую нельзя убирать только ради CI.
Утверждённые испытания Debug VPN являются отдельным предварительным
условием. Изменение Release gate потребует самостоятельной проверки.

## 5. Одобрить публичный *technical preview* (не production VPN)

Если PR #83 и PR #84 слиты и для **самого выпускаемого коммита**
имеется документ с результатами реальных испытаний, можно вручную
запустить `distribution=public-preview`,
`acceptance_commit=<текущий SHA>` и
`acceptance_report_url=https://github.com/...`.

Workflow отклонит другую ветку, неверный SHA, отсутствующий отчёт
либо незавершённые PR #83/#84. После одобрения окружения он
загрузит ровно один нотариализованный DMG в отдельный **pre-release**.
Тег вида `macos-v0.1.0-<12-символьный SHA>`, SHA-256 помещён в
описание релиза, без второго файла SHA или XCFramework.

Пока Release VPN заблокирован, публичная сборка должна называться
**technical preview**, а не полнофункциональным безопасным VPN.

## 6. Обновления macOS

В приложении есть кнопка поиска обновлений:
- опрашивает только `api.github.com/repos/SawaMEN/hiddify-app/releases`;
- учитывает лишь версионированные `macos-vX.Y.Z-SHA12` релизы
  с **ровно одним** `VetrOFF-macOS-arm64.dmg`;
- сравнивает числовые версии с `CFBundleShortVersionString`;
- открывает страницу релиза по явному действию пользователя.

Никакие бинарные файлы не запускаются и не устанавливаются
автоматически. При следующем выпуске следует повысить
`CFBundleShortVersionString`, иначе checker не предложит обновление.

## Текущее положение

Программная инфраструктура добавлена в PR разработки, но публичного
подписанного DMG **нет**. Нужны Developer ID, Apple-approved entitlements,
подключённые секреты `macos-signing` и результаты испытаний.
