# VetrOFF macOS — один установочный DMG

Предварительную сборку для macOS 14+ на Apple Silicon можно найти в
[GitHub Releases](https://github.com/SawaMEN/hiddify-app/releases)
как `VetrOFF-macOS-arm64.dmg`. Внутри находится одно приложение
`VetrOFF.app` со встроенным `PacketTunnel.appex`. Перетащите
приложение в папку Applications.

Workflow `.github/workflows/macos-dmg.yml` автоматически создаёт
отдельный **pre-release** при изменении процесса упаковки в `main`,
а также поддерживает ручной запуск. Он компилирует **Debug**-вариант
с pinned Darwin-only `HiddifyCore.xcframework` и создаёт DMG через
`hdiutil`. В GitHub Release загружается ровно **один** файл .dmg.

## Ограничение экспериментальной сборки

GitHub Actions не имеет конфигурации Apple Developer ID / provisioning
для подписи Network Extension. Этот DMG **не подписан и не
нотариализован**; его нельзя считать готовым VPN. macOS может
запретить запуск до подтверждения пользователем, а Packet Tunnel
может не получить нужные системные права. Нельзя обещать, что трафик
будет проходить через VPN, пока приложение и расширение не будут
подписаны требуемыми сертификатами и проверены на физическом Mac.

Production-релиз потребует сертификат Developer ID Application,
совместимые provisioning profiles с Network Extension и Keychain
entitlements, подпись app+appex, Apple notarization и проверки
TCP/UDP, DNS, IPv6, восстановления после сна и смены сети.

**Наличие DMG — это доставка файла, а не подтверждение безопасности VPN.**

## Этап 3: подписанный и нотариализованный DMG

Подготовлен защищённый ручной workflow
`.github/workflows/macos-signed-dmg.yml`, который создаёт один подписанный
`VetrOFF-macOS-arm64.dmg`. По умолчанию это **приватный артефакт для
тестов**, без публичного релиза. Нотариализация обязательно должна
завершиться успешно. Публичная публикация требует подтверждения тестов
на физическом Mac и точного SHA. Подробности: [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).

Это **не означает**, что опубликованный старый unsigned-preview DMG был
обновлён или теперь стал пригоден для VPN.
