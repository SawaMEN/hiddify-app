# Подтверждённые ошибки Kotlin: 2026-10-08

База: `2ba470cb1e728cbd2c0fafecdf202ecd9468c39d` (`main`).

В этой итерации подтверждены и исправлены **40 дефектов**. Это не 200 ошибок.
Матрица импорта настроек содержит **211 неверных входных комбинаций**; число
входов и число тестов не прибавляются к числу независимых дефектов.
Предыдущий реестр 200 отличий Dart/Kotlin не считается реестром ошибок.

Две ошибки компиляции подтверждены завершённым CI job `113301360715`.
Остальные основаны на конкретных ветвях исходного кода и перечисленных входах.
Добавлены регрессионные JVM-тесты. Локальные проверки ресурсов, реестра и whitespace прошли. Новые JVM-тесты
локально не стартовали: загрузка Gradle завершилась Network is unreachable. APK не ожидается. Отдельные тесты source-only не проверяют устройство.

| ID | Ошибка | Условие | Исправление | Файл |
|---|---|---|---|---|
| E001 | Неверный пакет JNI не позволяет скомпилировать приложение | CI compileReleaseKotlin: unresolved mobile.Mobile | Использован пакет com.hiddify.core.mobile из -javapkg | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeProfileValidator.kt` |
| E002 | Параметр кнопки JSON-редактора не поддерживается общей обёрткой | CI: No parameter with name contentPadding | Обёртка принимает contentPadding с прежним значением по умолчанию | `android/app/src/main/kotlin/com/hiddify/hiddify/nativeui/NativeDesignComponents.kt` |
| E003 | Повторяющийся ключ JSON теряет первое значение без предупреждения | {"a":1,"a":2} | Дубликаты ключей отклоняются на любой глубине | `android/app/src/main/kotlin/com/hiddify/hiddify/nativeprofile/NativeJsonDocument.kt` |
| E004 | Мутации JSON принимают путь без начального / | replace/remove/rename с путём a | Проверяется формат JSON pointer | `android/app/src/main/kotlin/com/hiddify/hiddify/nativeprofile/NativeJsonDocument.kt` |
| E005 | Некорректные escapes JSON pointer трактуются как ключи | Пути /a~ и /a~2 | Допускаются только escapes ~0 и ~1 | `android/app/src/main/kotlin/com/hiddify/hiddify/nativeprofile/NativeJsonDocument.kt` |
| E006 | Неканонические индексы массива указывают на чужой элемент | Индексы +1, 01, -0, пробел | Проверяется канонический десятичный индекс | `android/app/src/main/kotlin/com/hiddify/hiddify/nativeprofile/NativeJsonDocument.kt` |
| E007 | Резервная копия приложений принимается как нестрогий JSON | Комментарии и некавыченные ключи | Используется общий строгий JSON parser для обеих форм экспорта | `android/app/src/main/kotlin/com/hiddify/hiddify/nativerouting/NativePerAppBackup.kt` |
| E008 | Вложенность резервной копии приложений не ограничена | Массив, вложенный более 128 раз | Общий parser ограничивает глубину до рекурсивного разбора | `android/app/src/main/kotlin/com/hiddify/hiddify/nativerouting/NativePerAppBackup.kt` |
| E009 | Невозможно очистить допустимый необязательный TLS-диапазон | Очистить fragment-size, fragment-sleep или padding-size | Редактирование разрешает пустой диапазон, как validated() | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeTlsOptions.kt` |
| E010 | Западный часовой пояс ломает уровень и время в логах | -0500 2026-10-08 12:00:00 WARN retry | Разбираются оба знака offset | `android/app/src/main/kotlin/com/hiddify/hiddify/nativelog/NativeLogPresentation.kt` |
| E011 | Старая SS-ссылка не даёт адрес для TCP-диагностики | ss://base64(method:password@host:port) | Декодируется полный legacy SS authority | `android/app/src/main/kotlin/com/hiddify/hiddify/nativediagnostics/NativeDiagnosticEndpoint.kt` |
| E012 | Legacy Hysteria2 ошибочно считается TCP-протоколом | Тип hysteria2_legacy | Тип включён в UDP-only список | `android/app/src/main/kotlin/com/hiddify/hiddify/nativediagnostics/NativeDiagnosticEndpoint.kt` |
| E013 | TLS-фрагментация ошибочно отключает проверку сервера | Заголовок enable-fragment без цепочки | Только заголовки цепочки влияют на пропуск TCP | `android/app/src/main/kotlin/com/hiddify/hiddify/nativediagnostics/NativeDiagnosticEndpoint.kt` |
| E014 | Поиск слова detour даёт ложное обнаружение цепочки | Пустой detour или note со значением detour | Структурно проверяется непустой detour/dialerProxy | `android/app/src/main/kotlin/com/hiddify/hiddify/nativediagnostics/NativeDiagnosticEndpoint.kt` |
| E015 | UTC timestamp делает автообновление всегда просроченным | lastUpdate с Z или offset | Дата с зоной сравнивается как Instant; локальная дата совместима с Dart | `android/app/src/main/kotlin/com/hiddify/hiddify/nativeprofile/NativeSubscriptionMetadata.kt` |
| E016 | Обрезка файла во время чтения роняет просмотр логов | Файл сокращён после получения length | Чтение ограничено исходным размером и нормально завершает EOF | `android/app/src/main/kotlin/com/hiddify/hiddify/nativelog/NativeLogFiles.kt` |
| E017 | Полная строка на границе хвоста лога теряется | Начальная позиция сразу после newline | Сохраняется строка, начинающаяся на границе | `android/app/src/main/kotlin/com/hiddify/hiddify/nativelog/NativeLogFiles.kt` |
| E018 | Обрезанная строка без newline показывается как испорченный текст | Хвост начинается посреди UTF-8 строки без следующего newline | Неполная начальная строка пропускается | `android/app/src/main/kotlin/com/hiddify/hiddify/nativelog/NativeLogFiles.kt` |
| E019 | Один недоступный файл скрывает остальные источники логов | Удаление или отказ доступа к одному файлу | Остальные файлы и service logs остаются доступными | `android/app/src/main/kotlin/com/hiddify/hiddify/nativelog/NativeLogRepository.kt` |
| E020 | Очистка логов сообщает успех после отказа записи | Один источник невозможно очистить | Все источники обработаны; ошибки агрегируются и передаются в UI | `android/app/src/main/kotlin/com/hiddify/hiddify/nativelog/NativeLogFiles.kt` |
| E021 | Старое чтение логов возвращает удалённые или устаревшие записи | Очистка или повторное обновление, пока чтение идёт на IO | Предыдущее чтение отменяется; применяется только текущее поколение | `android/app/src/main/kotlin/com/hiddify/hiddify/MainActivity.kt` |
| E022 | Импорт настроек сохраняет неверные типы и null | Строка вместо boolean, объект вместо DNS-адреса и другие неверные типы | Типы проверяются до записи всего документа | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeSettingsDocument.kt` |
| E023 | Импорт настроек сохраняет дробные и переполненные числовые поля | Порт 1.5, 65536, отрицательное значение или Long overflow | Проверяются целочисленность и границы портов, MTU и интервала | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeSettingsDocument.kt` |
| E024 | Импорт не проверяет перечисления и вложенные значения TLS/цепочки | Неверный ipv6-mode, перевёрнутый TLS-диапазон, неверный chain mode | Известные значения проверяются; неизвестные текущие поля сохраняются | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeSettingsDocument.kt` |
| E025 | Импорт позволяет два включённых слушателя на одном порту | mixed-port=12337 при включённом direct-port=12337 | Проверяется уникальность портов включённых слушателей | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeSettingsDocument.kt` |
| E026 | Импорт позволяет IPv6 с MTU ниже допустимого минимума | prefer_ipv6 с mtu=576 | Проверяется сочетание IPv6 и MTU >= 1280 | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeSettingsDocument.kt` |
| E027 | Частичный импорт затирает соседние вложенные настройки | Импорт enable-fragment удалял padding и импорт warp-port удалял license | Вложенные объекты объединяются в копии документа | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeSettingsDocument.kt` |
| E028 | Экспорт настроек теряет старые настройки Dart | Настройка есть только во flutter.* | В экспорт включаются эффективные legacy aliases, включая TLS и LAN password | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeSettingsTransferRepository.kt` |
| E029 | Импорт сообщает успех до подтверждённой записи настроек | Ошибка записи SharedPreferences | Используется commit с проверкой результата | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeSettingsTransferRepository.kt` |
| E030 | Повреждённый JSON настроек незаметно заменяется пустым документом | Импорт поверх некорректного config_options_json | Ошибка передаётся пользователю; старый документ не затирается | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeSettingsTransferRepository.kt` |
| E031 | Тестовый релиз может распознаваться как стабильный | Тег v1.0.1-rc.1 при prerelease=false | Допускается только точный формат production tag | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeReleasePolicy.kt` |
| E032 | Некорректный build или версия релиза принимается за действительный | +oops, переполнение build, отрицательный компонент версии | Некорректный тег полностью отклоняется | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeReleasePolicy.kt` |
| E033 | Проверка URL релиза принимает credentials, посторонний порт и traversal | user@github.com, :123 или ../ и %2e%2e в path | Проверяются authority и нормализованный decoded path | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeReleasePolicy.kt` |
| E034 | HTTP-ответ проверки обновлений читается без ограничения размера | Очень большой ответ API | Чтение ограничено общим лимитом 8 MiB | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeUpdateRepository.kt` |
| E035 | Инструкция Wi-Fi теряет реквизиты из старых настроек | Порт/пароль есть во flutter.*, JSON-ключ отсутствует | Используются legacy port/password fallback | `android/app/src/main/kotlin/com/hiddify/hiddify/nativecore/NativeWifiSharingRepository.kt` |
| E036 | Маршрутизация допускает недопустимые доменные метки | -bad.example, bad-.example, underscore, метка >63 | Проверяются ASCII DNS labels и длина каждой метки | `android/app/src/main/kotlin/com/hiddify/hiddify/privacy/NativeRegionalOptions.kt` |
| E037 | Кириллические домены невозможно добавить в маршрутизацию | пример.рф | Домен преобразуется в IDNA ASCII до проверки и дедупликации | `android/app/src/main/kotlin/com/hiddify/hiddify/privacy/NativeRegionalOptions.kt` |
| E038 | Правила пакетов принимают неверные Android package names и отвергают android | 1com.example, com.bad-name, android | Отдельная проверка пакетов вместо общего domain/package regexp | `android/app/src/main/kotlin/com/hiddify/hiddify/privacy/NativeRegionalOptions.kt` |
| E039 | Поздняя запись истории удаляет новые результаты другой сессии | Старая Activity сохраняет subset тегов после новой | Объединяются обе карты; для каждого тега сохраняется самый новый результат | `android/app/src/main/kotlin/com/hiddify/hiddify/nativeconnection/NativeServerHistoryRepository.kt` |
| E040 | Лимит истории сохраняет первые, а не самые свежие результаты | Более 128 образцов в истории | Перед ограничением образцы сортируются по updated descending | `android/app/src/main/kotlin/com/hiddify/hiddify/nativeconnection/NativeServerRanker.kt` |
