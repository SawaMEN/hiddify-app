# Аудит Android/VPN — 2026-10-08

База: `d872e03b` (`main`). Исходное закреплённое ядро: `ea0838f1`. Новое ядро: `96e19a9c`; sing-box: `60e1fd48`.

Изменения: [app PR #55](https://github.com/SawaMEN/hiddify-app/pull/55), [core PR #6](https://github.com/SawaMEN/hiddify-core/pull/6), [sing-box PR #3](https://github.com/SawaMEN/hiddify-sing-box/pull/3).

В этом проходе исправлено **70 групп подтверждённых дефектов**. Это не 200 ошибок: отличия Dart/Kotlin, число тестовых случаев и ранее исправленные ошибки не прибавлялись. Подтверждение здесь означает воспроизводимую ветвь исходного кода или JVM regression test; испытание VPN на физическом Android не выполнялось.

## Проверки

- 240 JVM-тестов production Kotlin: PASS. Компиляция K2 JVM 2.4.10/JDK17 и JUnitCore; исходники перечислены в tool/kotlin-tests/build.gradle. Это проверка чистой Kotlin-логики, а не всего Android приложения.
- 6 Python tests утилит: PASS.
- tool/check_native_project.py: PASS; XML resources, standalone project, version 1.0.1+40201.
- tool/check_dart_parity_audit.py: PASS. Его 200 отличий не являются 200 ошибками.
- git diff --check: PASS.
- Gradle wrapper не смог загрузиться из Java (Network is unreachable); для локальных JVM-тестов compiler и зависимости загружены отдельно через доступный transport.
- Добавлена отдельная CI native-kotlin-tests.yml: JVM и Go-тесты без Android SDK, JNI и APK.
- Go suites v2/config, v2/hcore, platform/mobile и protocol/psiphon, protocol/hiddify/dnstt: PASS (в том числе -race).
- APK не собирался и не ожидался. compileReleaseKotlin/Android runtime не проверены локально.

## Ограничения цепочек

Новая реализация добавляет `chain-stage` в ядро. Kotlin перед запуском разрешает ID второго профиля в актуальное содержимое локального файла под блокировкой обновления/удаления. Ядро использует общий parser и создаёт отдельную группу с пространством имён для тегов. `extra_security`: трафик → второе звено → основной профиль. `unblocker`: трафик → основной профиль → второе звено. Во втором профиле используются outbound/endpoints; отдельные inbounds, DNS, rules и settings overrides второго профиля не объединяются с основным. Это изменение конфигурации и транспорта; реальный handshake на Android ещё не проверен.

Psiphon поддерживает TCP, не UDP. Psiphon bootstrap TCP проходит через частный аутентифицированный CONNECT bridge и sing-box dialer; локальный слушатель и существующие соединения закрываются вместе с outbound. Conduit pairing передаётся в `InproxyClientPersonalCompartmentID`, требует прямого транспорта; сочетание с detour и root routing marks отклоняется. Два экземпляра Psiphon в одной цепочке не допускаются из-за глобального хранилища библиотеки. Отдельная стадия несовместима с execute-config-as-is. Runtime JSON ограничен 3 MiB до RPC (его стандартный предел 4 MiB); обычные профили сохраняют предел 8 MiB. Самоссылки, удалённые профили и selectable direct в дополнительном профиле отклоняются без fallback.

WARP остаётся на старой проекции; handshake, лицензия, clean-IP и noise не проверены и не исправлялись в этом проходе. Импорт проверяет сам источник без подключения текущей цепочки; составленная цепочка проверяется при запуске ядра.

## Реестр

| ID | Дефект | Условие | Исправление | Исходник |
|---|---|---|---|---|
| A001 | Обновление подписки отменяет сделанный во время загрузки выбор активного профиля | Обновить активный профиль и выбрать другой до окончания HTTP | При commit используется актуальный active из БД | `nativeprofile/NativeProfileRepository.kt` |
| A002 | Обновление восстанавливает удалённый профиль | Удалить профиль до начала refresh или во время подготовки | Явный existingId и существование записи проверяются до commit | `nativeprofile/NativeProfileRepository.kt` |
| A003 | Позднее обновление затирает более свежие изменения профиля | Редактор или другой импорт завершился во время HTTP | Сравнение ожидаемого и актуального снимка без поля active | `nativeprofile/NativeProfileRepository.kt` |
| A004 | Старый открытый редактор сохраняет конфигурацию поверх свежей подписки | Открыть редактор, обновить подписку, сохранить старый текст | Передаётся expectedLastUpdate из редактора | `nativeprofile/NativeProfileRepository.kt; MainActivity.kt` |
| A005 | Два одновременных импорта создают две записи одной подписки | Две подготовки одного нового URL до сохранения | Повторная проверка URL под общим lock | `nativeprofile/NativeProfileRepository.kt` |
| A006 | Перекрывающиеся операции с файлами и БД могут восстановить чужую резервную копию | Две repository instances меняют один профиль | Общий mutationLock для commit/delete/setActive | `nativeprofile/NativeProfileRepository.kt` |
| A007 | Можно активировать профиль с отсутствующим файлом | Удалён файл configs/id.json | Проверка файла до изменения активной записи | `nativeprofile/NativeProfileRepository.kt` |
| A008 | Синхронизация активного профиля может записать в Settings устаревший выбор | setActive между чтением activeProfile и syncSettings | Чтение и публикация под mutationLock | `nativeprofile/NativeProfileRepository.kt` |
| A009 | Параллельное создание отсутствующего столбца завершает миграцию ошибкой | Две repository instances открывают старую схему одновременно | Сериализована ensureSchema | `nativeprofile/NativeProfileRepository.kt` |
| A010 | Ошибка миграции оставляет открытую SQLiteDatabase | ensureSchema бросает исключение | Закрытие db перед повторным выбросом | `nativeprofile/NativeProfileRepository.kt` |
| A011 | Ошибка записи временного файла/копирования backup оставляет мусор | Недостаточно места или ошибка IO до прежнего try | Весь файловый этап входит в try/finally | `nativeprofile/NativeProfileFileReplacement.kt` |
| A012 | Неудачный rollback удаляет единственную recovery-копию | Ошибка БД и невозможность вернуть backup | Backup сохраняется при неудаче восстановления | `nativeprofile/NativeProfileFileReplacement.kt` |
| A013 | Ошибка восстановления скрывает первоначальную ошибку БД | И commit, и rollback завершаются ошибкой | Вторая ошибка добавляется в suppressed | `nativeprofile/NativeProfileFileReplacement.kt` |
| A014 | Сбой публикации Settings откатывает файл уже после успешного commit БД | DB commit завершён, syncSettings бросает исключение | Публикация Settings выполняется вне файловой транзакции | `nativeprofile/NativeProfileRepository.kt` |
| A015 | Ограничение размера проверяется после записи слишком большого конфига | content больше 8 MiB | Лимит проверяется до создания файлов | `nativeprofile/NativeProfileFileReplacement.kt` |
| A016 | ID профиля может выйти за пределы каталога configs | ID с ../ или разделителем пути | Допускается безопасный ограниченный формат ID | `nativeprofile/NativeProfileRepository.kt` |
| A017 | UTF-8 BOM остаётся в загруженной подписке | HTTP body начинается с BOM | BOM удаляется так же, как при файловом импорте | `nativeprofile/NativeProfileRepository.kt` |
| A018 | Некорректные порты подписки принимаются как допустимый HTTP URL | URL с :0 или :65536 | Проверка диапазона порта | `nativeprofile/NativeProfileRepository.kt` |
| A019 | Проверка публичности вложенной подписки обходится повторным DNS lookup | DNS возвращает публичный адрес при проверке и приватный при подключении | Проверяются адреса, реально возвращённые DNS для OkHttp | `nativeprofile/NativeProfileRepository.kt` |
| A020 | Автообновление зависит от смены часового пояса/DST после сохранения | lastUpdate записан как локальное время без offset | Новые timestamps сохраняются как UTC Instant; старые читаются | `nativeprofile/NativeProfileRepository.kt` |
| A021 | JSON null в флаге подписки вызывает исключение при запуске | enable-warp или enable-fragment равен null | Чтение только строковых и boolean flags | `nativeprofile/NativeProfileOverrides.kt` |
| A022 | Текстовые boolean headers теряются из-за регистра и пробелов | enable-warp: TRUE или строка с пробелами | Trim и case-insensitive сравнение | `nativeprofile/NativeProfileOverrides.kt` |
| A023 | Неверный тип header заменяет рабочий DNS/TLS default | remote-dns-address объект/boolean либо tls-tricks массив | Проверяются ожидаемые формы overrides | `nativeprofile/NativeProfileOverrides.kt` |
| A024 | Fallback control port совпадает с front port | front=17079 и недопустимый back | Выбирается свободный из двух стандартных портов | `Settings.kt` |
| A025 | Изменения TUN/inbound не учитываются при проверке переподключения | Сменить MTU, TUN stack или параметры слушателей | В signature включены полные inbound и tunnel options | `Settings.kt` |
| A026 | Изменения privacy/connection/regional policy не учитываются | Поменять full tunnel, encrypted DNS, правила или policy | В signature включены все перечисленные политики | `Settings.kt` |
| A027 | Обновление используемого профиля не предлагает применить новый конфиг | Редактировать/обновить активный либо chained profile при работающем ядре | В signature включены версии используемых профилей; проверка после сохранения | `Settings.kt; MainActivity.kt` |
| A028 | Legacy preferences видны в UI, но не передаются ядру | Поле присутствует только во flutter.* | Runtime получает эффективный snapshot с legacy aliases | `nativecore/NativeCoreControl.kt` |
| A029 | Свежая установка показывает настройки, отличающиеся от работающего ядра | Пустой config_options_json | Проекция native defaults для DNS, URL probe, TLS и TUN | `nativecore/NativeCoreOptionsProjection.kt` |
| A030 | Выключенные слушатели остаются включёнными в ядре | enable-mixed/direct-port=false при ненулевом port | Для ядра disabled switch преобразуется в port=0 | `nativecore/NativeCoreOptionsProjection.kt` |
| A031 | Режим исполнения исходного конфига игнорируется | execute-config-as-is=true | Преобразуется в enable-full-config, известный закреплённому ядру | `nativecore/NativeCoreOptionsProjection.kt` |
| A032 | WARP stage из нового формата chain settings игнорируется | chain-status extra_security/unblocker с mode=warp | Проекция в warp.enable/mode с направлением цепочки | `nativecore/NativeCoreOptionsProjection.kt` |
| A033 | Неподдерживаемая стадия цепочки сообщает обычное успешное подключение | Отдельная Psiphon/Profile stage в новом формате | Реализованы обе стадии в ядре и разрешение профиля перед запуском; несовместимые сочетания отклоняются | `nativecore/NativeCoreOptionsProjection.kt` |
| A034 | Отключение новой цепочки оставляет legacy WARP включённым | chain-status=off и warp.enable=true | Явный off выключает legacy warp/warp2 | `nativecore/NativeCoreOptionsProjection.kt` |
| A035 | Ранее сохранённый IPv6 auto не распознаётся ядром | ipv6-mode=auto | Перед запуском преобразуется в prefer_ipv4 | `nativecore/NativeServiceModeOptions.kt` |
| A036 | Проверка импорта использует другие настройки, чем запуск | Legacy/default/mode options отличаются от runtime | Одинаковая проекция и service-mode policy при validation и startup | `nativeprofile/NativeProfileRepository.kt` |
| A037 | Редактор цепочек незаметно затирает повреждённый JSON | Сохранить chain settings поверх невалидного документа | При мутации ошибка передаётся; read-only load сохраняет fallback | `nativecore/NativeChainRepository.kt` |
| A038 | Редактор цепочек принимает недопустимые диапазоны и зажимает порт | Перевёрнутый noise range или port вне 0..65535 | Проверка до сохранения | `nativecore/NativeChainRepository.kt` |
| A039 | Загрузчик TUN возвращает MTU ниже минимума IPv6 | Legacy prefer_ipv6 с mtu=576 | Эффективный loaded MTU не меньше 1280 | `nativecore/NativeTunnelOptionsRepository.kt` |
| A040 | Редактор принимает URL probe, который health controller отвергает | URL с credentials или fragment | Валидация согласована с NativeProbePolicy | `nativecore/NativeGeneralOptions.kt` |
| A041 | Импорт разрешает включённую Clash API с портом 0 | enable-clash-api=true; clash-api-port=0 | Комбинация отклоняется до записи | `nativecore/NativeSettingsDocument.kt` |
| A042 | Сервис может сообщить Started без запуска самого ядра | BoxService.start вызван со старым startCoreAfterStartingService=false | Нативный start всегда выставляет запуск ядра | `bg/BoxService.kt` |
| A043 | Ошибка закрытия при reload возвращает ложный Started | closeCore дважды завершился ошибкой | Соединение останавливается и публикуется ошибка | `bg/BoxService.kt` |
| A044 | Выход из Doze может вызвать wake на уже закрытом ядре | Idle broadcast одновременно с teardown | Wake выполняется под lifecycle mutex с проверкой владельца | `bg/BoxService.kt` |
| A045 | Заменённый сервис остаётся Started после передачи ядра | Переключение VPN/Proxy service | Старый владелец освобождает foreground/status/receiver и stopSelf | `bg/BoxService.kt` |
| A046 | Boot restart игнорирует явное намерение пользователя отключиться | startedByUser=true и connectionDesired=false | Проверяются оба признака | `bg/BootReceiver.kt` |
| A047 | ProxyService не вызывает lifecycle onDestroy родителя | Уничтожение proxy service | Добавлен super.onDestroy | `bg/ProxyService.kt` |
| A048 | Отказ bindService не переводит UI в корректное состояние | Android возвращает false, не бросая исключение | False обрабатывается как отказ | `bg/ServiceConnection.kt` |
| A049 | Повторное подключение Binder и поздние callbacks нарушают lifecycle | Повторный connect либо onServiceConnected после disconnect | Учёт bindingRequested, idempotent connect и снятие null binding | `bg/ServiceConnection.kt` |
| A050 | Tile объявлен direct-boot-aware, но читает credential-protected Settings | Quick Settings bind до первого unlock после reboot | Убрано неподдерживаемое direct boot объявление | `AndroidManifest.xml` |
| A051 | Start/stop мониторинга возвращается до выполнения actor-команды | Setup/teardown продолжается до регистрации/снятия callback | Start/Stop ожидают acknowledgement actor | `bg/DefaultNetworkListener.kt` |
| A052 | Событие старого callback возвращает старую сеть после рестарта | Queued onAvailable от предыдущей регистрации | У каждой регистрации epoch; устаревшие события игнорируются | `bg/DefaultNetworkListener.kt` |
| A053 | Fallback монитор теряет последующие изменения физической сети | OEM отклоняет registerBestMatchingNetworkCallback | Ограниченный по времени polling с отменой при stop | `bg/DefaultNetworkListener.kt` |
| A054 | Новый native listener не получает прежний интерфейс | Заменён listener, но name/index/network совпадают | Сброс предыдущего snapshot при смене listener | `bg/DefaultNetworkMonitor.kt` |
| A055 | Metered/constrained изменение не доходит до ядра | Capabilities изменились при той же сети и interface index | Стоимость сети включена в сравниваемый snapshot | `bg/DefaultNetworkMonitor.kt` |
| A056 | Per-app маршрутизация не обновляется после установки приложений | Per-app включён, regional off/full tunnel или ранее исключено отсутствующее приложение | Перечитываются выборы из БД и перезапускается core | `bg/BoxService.kt` |
| A057 | Диагностика проверяет DNS/TCP не в той сети, которую использует VPN | Активная VPN сеть либо смена underlying network | DNS и TCP probe явно привязаны к физической сети | `nativediagnostics/NativeDiagnosticsRepository.kt` |
| A058 | Диагностика накапливает задания и может оставить открытый Socket | Зависшие DNS workers, повторные запуски, отказ executor.submit | Очередь ограничена, cancelled tasks удаляются, socket закрывается в outer finally | `nativediagnostics/NativeDiagnosticsRepository.kt` |
| A059 | Server ranker теряет свежие valid history и растит live cache без лимита | History >128, ранние invalid samples либо множество новых тегов | Сортировка, лимит после проверки и bounded live eviction | `nativeconnection/NativeServerRanker.kt` |
| A060 | Отключение memory limit сбрасывается при Mobile.start | disableMemoryLimit=true; wrapper создаёт StartRequest с false | После запуска восстанавливается выбранный флаг | `bg/BoxService.kt` |
| A061 | Psiphon создаёт dialer, но bootstrap контроллера обходит его | Psiphon outbound с detour либо root routing mark | Частный CONNECT bridge направляет bootstrap TCP через заданный dialer | `hiddify-core/hiddify-sing-box/protocol/psiphon/upstream_bridge.go` |
| A062 | Закрытие незапущенного Psiphon вызывает nil panic | Проверка конфига/ошибка запуска до Start | Cancel создаётся в конструкторе; Close идемпотентен | `hiddify-core/hiddify-sing-box/protocol/psiphon/psiphon.go` |
| A063 | Смена интерфейса до готовности Psiphon вызывает nil panic | Network callback приходит до создания controller | Синхронизированный guard в NetworkChanged | `hiddify-core/hiddify-sing-box/protocol/psiphon/psiphon.go` |
| A064 | Psiphon может публиковать controller после закрытия и читать connected с гонкой | Остановка во время Start/notice/Dial | Cancel, mutex, ожидание завершения Start и controller.Run | `hiddify-core/hiddify-sing-box/protocol/psiphon/psiphon.go` |
| A065 | Утрата туннеля сохраняет connected=true | Notice Tunnels count=0 после подключения | Состояние обновляется и при нулевом числе туннелей | `hiddify-core/hiddify-sing-box/protocol/psiphon/psiphon.go` |
| A066 | Start повторно открывает datastore, не отслеживая ownership | PreStart успешно открыл store, затем Start/ошибка/Close | Одно открытие в PreStart; закрытие после остановки контроллера | `hiddify-core/hiddify-sing-box/protocol/psiphon/psiphon.go` |
| A067 | Удаление chained profile подменяет его основным | Удалён профиль, nextActive выбран вместо него | Ссылка очищается; main и stage должны различаться | `nativecore/NativeChainRepository.kt; NativeCoreOptionsProjection.kt` |
| A068 | Параллельные BuildConfig смешивают глобальные цели DNS/route | RPC generate одновременно со стартом либо вторым build | Полные снимки сериализованы; parser WARP читает постоянный bootstrap target | `hiddify-core/v2/config/builder.go; warp.go; chain_test.go` |
| A069 | Создание нескольких libbox contexts одновременно вызывает fatal concurrent map writes | Параллельная проверка импортов/запуск создают OutboundRegistry | DNSTT resolver tables загружаются один раз через sync.Once | `hiddify-core/hiddify-sing-box/protocol/hiddify/dnstt/tools.go` |
| A070 | Экспорт без приватных настроек раскрывает Conduit pairing ID | exportJson(includePrivate=false) при заполненном Psiphon pairing | Pairing ID удаляется из обеих стадий при публичном экспорте | `nativecore/NativeSettingsTransferRepository.kt` |

## Дополнительная проверка RPC

Для применения настроек, чтения статистики и UrlTestActive добавлены явные deadlines и cancel в finally. Это усиление управления ресурсами; оно не добавляется отдельными строками к счётчику дефектов.

## Первичные источники

- [Android VpnService.Builder](https://developer.android.com/reference/android/net/VpnService.Builder): требования к Builder и setMetered (false наследует meteredness underlying network; прежнее поведение сохранено).
- [Android direct boot](https://developer.android.com/privacy-and-security/direct-boot): credential-protected storage недоступен до unlock.
- Закреплённые исходники: hiddify-core/platform/mobile/mobile.go, hiddify-core/v2/hcore/start.go, hiddify-core/v2/config/hiddify_option.go и builder.go. Gitlink обновлён; изменения Psiphon также закреплены в зависимом hiddify-sing-box.
