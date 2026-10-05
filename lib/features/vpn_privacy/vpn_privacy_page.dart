import 'dart:io';

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/core/db/provider/db_providers.dart';
import 'package:hiddify/core/utils/preferences_utils.dart';
import 'package:hiddify/features/connection/health/android_vpn_settings.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/features/settings/widget/preference_tile.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
import 'package:hiddify/singbox/model/singbox_config_enum.dart';
import 'package:path_provider/path_provider.dart';

String _text(BuildContext context, String ru, String en) =>
    Localizations.localeOf(context).languageCode == 'ru' ? ru : en;

class VpnPrivacyPage extends ConsumerStatefulWidget {
  const VpnPrivacyPage({super.key});
  static String title(BuildContext context) => _text(context, 'Скрытие VPN', 'VPN privacy');
  @override
  ConsumerState<VpnPrivacyPage> createState() => _VpnPrivacyPageState();
}

class _VpnPrivacyPageState extends ConsumerState<VpnPrivacyPage> with WidgetsBindingObserver {
  static const _channel = MethodChannel('com.hiddify.app/method');
  Map<dynamic, dynamic> _identity = {};
  bool _busy = false;
  Map<dynamic, dynamic> _root = {};
  Map<dynamic, dynamic> _routing = {};

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _refreshIdentity();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refreshIdentity();
  }

  Future<void> _refreshIdentity() async {
    if (!Platform.isAndroid) return;
    try {
      final identity = await _channel.invokeMapMethod<dynamic, dynamic>('get_package_identity') ?? {};
      final routing =
          await _channel.invokeMapMethod<dynamic, dynamic>('get_regional_routing', {
            'region': ref.read(ConfigOptions.region).name,
          }) ??
          {};
      if (mounted)
        setState(() {
          _identity = identity;
          _routing = routing;
        });
    } catch (error) {
      if (mounted) _error(error);
    }
  }

  void _error(Object error) {
    final message = error is PlatformException ? error.message ?? error.code : error.toString();
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _perform(Future<void> Function() operation) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await operation();
      await _refreshIdentity();
    } catch (error) {
      if (mounted) _error(error);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _switch(
    String ru,
    String en,
    String hintRu,
    String hintEn,
    StateNotifierProvider<PreferencesNotifier<bool, bool>, bool> preference, {
    bool available = true,
  }) => SwitchListTile.adaptive(
    title: Text(_text(context, ru, en)),
    subtitle: Text(_text(context, hintRu, hintEn)),
    value: ref.watch(preference),
    onChanged: _busy || !available ? null : (value) => _perform(() => ref.read(preference.notifier).update(value)),
  );

  Future<void> _createCopy() async {
    final style = ref.read(VpnPrivacyPreferences.copyStyle);
    var packageName = await _channel.invokeMethod<String>('generate_private_package') ?? '';
    if (!mounted) return;
    final name = TextEditingController(text: style == 'meet' ? 'Meet' : 'Notes');
    final label = await showDialog<String>(
      context: context,
      builder: (context) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(_text(context, 'Скрыть пакет приложения', 'Hide application package')),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  _text(
                    context,
                    'Будет создана копия с новым случайным пакетом и подписью. VPN отключится. После установки нажмите «Открыть скрытую копию» здесь для переноса профилей. Исходная версия останется до её удаления вручную. Обычный APK обновления устанавливается как исходное приложение.',
                    'Creates a copy with a random package and new signature. VPN will disconnect. After installation, use Open private copy here to migrate profiles. The original stays until you remove it. Regular update APKs install as the original application.',
                  ),
                ),
                SelectableText(packageName),
                TextButton.icon(
                  icon: const Icon(Icons.shuffle),
                  label: Text(_text(context, 'Сгенерировать другой пакет', 'Generate another package')),
                  onPressed: () async {
                    try {
                      final next = await _channel.invokeMethod<String>('generate_private_package');
                      if (context.mounted && next != null) setDialogState(() => packageName = next);
                    } catch (error) {
                      if (mounted) _error(error);
                    }
                  },
                ),
                TextField(
                  controller: name,
                  maxLength: 40,
                  decoration: InputDecoration(labelText: _text(context, 'Название приложения', 'Application name')),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: Text(_text(context, 'Отмена', 'Cancel'))),
            FilledButton(
              onPressed: () {
                final value = name.text.trim();
                if (value.isNotEmpty) Navigator.pop(context, value);
              },
              child: Text(_text(context, 'Создать копию', 'Create copy')),
            ),
          ],
        ),
      ),
    );
    // The dialog can still be animating out when its controller stops being used.
    WidgetsBinding.instance.addPostFrameCallback((_) => name.dispose());
    if (label == null || !mounted) return;
    await _perform(() async {
      await ref.read(connectionNotifierProvider.notifier).disconnectForMigration();
      if (!await AndroidVpnSettings.stopService()) throw StateError('Unable to stop VPN before migration');
      final cache = await getTemporaryDirectory();
      final snapshot = File('${cache.path}/identity-db-${DateTime.now().microsecondsSinceEpoch}.sqlite');
      try {
        // SQLite's online snapshot is consistent even with WAL/background readers.
        await ref.read(dbProvider).customStatement('VACUUM INTO ?', [snapshot.path]);
        await _channel.invokeMethod<String>('prepare_private_package', {
          'label': label,
          'style': style,
          'package': packageName,
          'databaseSnapshot': snapshot.path,
        });
        await _channel.invokeMethod<void>('install_private_package');
      } finally {
        if (await snapshot.exists()) await snapshot.delete();
      }
    });
  }

  Future<void> _setRoot(bool enabled) => _perform(() async {
    if (enabled) {
      final status = await _channel.invokeMapMethod<dynamic, dynamic>('check_root') ?? {};
      if (mounted) setState(() => _root = status);
      if (status['granted'] != true)
        throw StateError(_text(context, 'Root-доступ не предоставлен', 'Root permission was not granted'));
      if (status['helper'] != true)
        throw StateError(_text(context, 'В этом APK отсутствует root-ядро', 'This APK has no root companion'));
    }
    await ref.read(VpnPrivacyPreferences.useRoot.notifier).update(enabled);
  });

  Widget _listSetting(
    String ru,
    String en,
    StateNotifierProvider<PreferencesNotifier<String, String>, String> preference,
  ) => ValuePreferenceWidget(
    value: ref.watch(preference),
    preferences: ref.watch(preference.notifier),
    enabled: !_busy,
    title: _text(context, ru, en),
    icon: Icons.rule,
    inputToValue: (value) => value.trim(),
    validateInput: (value) =>
        value.length <= 8192 &&
        value
            .split(RegExp(r'[\s,;]+'))
            .where((s) => s.isNotEmpty)
            .every((s) => RegExp(r'^(?:\*\.)?[a-zA-Z0-9_-]+(?:\.[a-zA-Z0-9_-]+)+$').hasMatch(s)),
  );

  @override
  Widget build(BuildContext context) {
    final android = Platform.isAndroid;
    return Scaffold(
      appBar: AppBar(title: Text(VpnPrivacyPage.title(context))),
      body: ListView(
        children: [
          if (_busy) const LinearProgressIndicator(),
          Padding(
            padding: const EdgeInsets.all(20),
            child: Text(
              _text(
                context,
                'Эти настройки уменьшают локальные признаки и утечки. В обычном режиме Android сообщает о сети VPN; root-режим избегает VpnService, но другие признаки могут остаться. Скрытие пакета не скрывает VpnService, IP сервера или геолокацию и не гарантирует прохождение RKNHardering.',
                'These settings reduce local signals and leaks. Normal mode reports a VPN network; root mode avoids VpnService, but other signals may remain. Renaming the package does not hide VpnService, server IP or location, and does not guarantee passing RKNHardering.',
              ),
            ),
          ),
          if (android) ...[
            SwitchListTile.adaptive(
              value: ref.watch(VpnPrivacyPreferences.useRoot),
              onChanged: _busy ? null : _setRoot,
              title: Text(_text(context, 'Подключение через root', 'Connect using root')),
              subtitle: Text(
                _text(
                  context,
                  'Запускает ядро с правами root без Android VpnService. Доступ подтверждается в вашем root-менеджере. Требуется поддержка TUN ядром устройства.',
                  'Runs the core as root without Android VpnService. Grant access in your root manager. Requires kernel TUN support.',
                ),
              ),
            ),
            if (_root.isNotEmpty)
              ListTile(
                subtitle: Text(
                  _text(
                    context,
                    'Root: ${_root['granted'] == true ? "доступен" : "нет доступа"}; ядро в APK: ${_root['helper'] == true ? "есть" : "нет"}',
                    'Root granted: ${_root['granted']}; bundled companion: ${_root['helper']}',
                  ),
                ),
              ),
            ChoicePreferenceWidget(
              selected: ref.watch(VpnPrivacyPreferences.routingMode),
              preferences: ref.watch(VpnPrivacyPreferences.routingMode.notifier),
              choices: const ['off', 'ru-bypass', 'proxy-selected'],
              enabled: !_busy,
              title: _text(context, 'Автомаршрутизация для РФ', 'Automatic routing for Russia'),
              icon: Icons.alt_route,
              presentChoice: (mode) => switch (mode) {
                'ru-bypass' => _text(context, 'Российские сервисы напрямую', 'Russian services directly'),
                'proxy-selected' => _text(
                  context,
                  'Прокси для выбранных зарубежных сервисов',
                  'Proxy selected foreign services',
                ),
                _ => _text(context, 'Выключено', 'Off'),
              },
            ),
            ListTile(
              subtitle: Text(
                _text(
                  context,
                  'Включается автоматически только при регионе РФ. Российские приложения из списка идут вне VPN. В режиме выбранных сервисов остальной трафик идёт напрямую. Домены и пакеты определяются при подключении; геосписки обновляет ядро. Полный туннель отключает исключения. Списки примерные: доступность сервисов и проверки могут меняться.',
                  'Activates only for the Russia region. Listed Russian apps bypass VPN. Selected-services mode sends other traffic directly. Packages are detected at connection; the core updates geo rules. Full tunnel overrides exceptions. Lists are suggestions: service availability and detection can change.',
                ),
              ),
            ),
            if (_routing['privacy-routing-mode'] != null)
              ListTile(
                title: Text(_text(context, 'Текущая автоматическая политика', 'Current automatic policy')),
                subtitle: Text(
                  '${_routing['privacy-routing-mode']}\n${(_routing['privacy-direct-packages'] as List? ?? []).join(', ')}',
                ),
              ),
            _listSetting(
              'Дополнительные прямые пакеты',
              'Additional direct packages',
              VpnPrivacyPreferences.customDirectPackages,
            ),
            _listSetting(
              'Дополнительные проксируемые пакеты',
              'Additional proxied packages',
              VpnPrivacyPreferences.customProxyPackages,
            ),
            _listSetting(
              'Дополнительные прямые домены',
              'Additional direct domains',
              VpnPrivacyPreferences.customDirectDomains,
            ),
            _listSetting(
              'Дополнительные проксируемые домены',
              'Additional proxied domains',
              VpnPrivacyPreferences.customProxyDomains,
            ),
            const Divider(),
          ],
          _switch(
            'Не открывать локальный прокси',
            'Disable local proxy',
            'В режиме VPN удаляет HTTP/SOCKS и direct listener. В режиме прокси порт сохраняется.',
            'Removes HTTP/SOCKS and direct listeners in VPN mode. Proxy mode keeps its listener.',
            VpnPrivacyPreferences.hideLocalProxy,
          ),
          _switch(
            'Не открывать Clash API',
            'Disable Clash API',
            'Отключает REST-порт управления. Выбор серверов и статистика остаются через защищённый API.',
            'Disables the REST control port. Server selection and statistics use the protected API.',
            VpnPrivacyPreferences.hideClashApi,
          ),
          _switch(
            'Не объявлять системный HTTP-прокси',
            'Disable system HTTP proxy',
            'В режиме VPN не добавляет HTTP-прокси в сведения о сети.',
            'Does not advertise an HTTP proxy on the VPN network.',
            VpnPrivacyPreferences.disableSystemProxy,
          ),
          _switch(
            'Полный туннель',
            'Full tunnel',
            'В режиме VPN все приложения, кроме самого клиента, и весь их TCP/UDP через туннель. Исключения приложений и прямые правила временно игнорируются. Для защиты при обрыве включите блокировку вне VPN в Android.',
            'In VPN mode, routes all apps except this client and all their TCP/UDP through the tunnel. App exclusions and direct rules are temporarily ignored. Enable Android lockdown to protect traffic when VPN stops.',
            VpnPrivacyPreferences.fullTunnel,
            available: android,
          ),
          _switch(
            'Публичный адрес DNS в сведениях о сети',
            'Public DNS address in network information',
            'Показывает 1.1.1.1 вместо адреса внутри туннеля. Запросы перехватываются ядром и идут через настроенный DNS.',
            'Advertises 1.1.1.1 instead of the tunnel address. The core intercepts queries and uses the configured DNS.',
            VpnPrivacyPreferences.publicDns,
            available: android,
          ),
          _switch(
            'Зашифрованный DNS через VPN',
            'Encrypted DNS through VPN',
            'Использует Cloudflare DoH через туннель. Bootstrap DNS имени VPN-сервера может оставаться прямым.',
            'Uses Cloudflare DoH through the tunnel. Bootstrap DNS for the VPN server hostname may remain direct.',
            VpnPrivacyPreferences.encryptedDns,
          ),
          ChoicePreferenceWidget(
            selected: ref.watch(ConfigOptions.ipv6Mode),
            preferences: ref.watch(ConfigOptions.ipv6Mode.notifier),
            choices: IPv6Mode.values,
            enabled: !_busy,
            title: 'IPv6',
            icon: Icons.language,
            presentChoice: (value) => value.name,
          ),
          ValuePreferenceWidget(
            value: ref.watch(ConfigOptions.mtu),
            preferences: ref.watch(ConfigOptions.mtu.notifier),
            enabled: !_busy,
            title: 'MTU',
            icon: Icons.settings_ethernet,
            inputToValue: int.tryParse,
            digitsOnly: true,
            validateInput: (value) {
              final n = int.tryParse(value);
              return n != null && n >= 1280 && n <= 65535;
            },
          ),
          ListTile(
            leading: const Icon(Icons.lock_outline),
            title: Text(_text(context, 'Управление ядром защищено', 'Core control is protected')),
            subtitle: Text(
              _text(
                context,
                'Локальные RPC требуют случайный ключ этой установки. Отключение защиты не предусмотрено.',
                'Local RPCs require a random key belonging to this installation. Protection is always enabled.',
              ),
            ),
          ),
          if (android)
            ListTile(
              leading: const Icon(Icons.security),
              title: Text(_text(context, 'Защита при обрыве VPN', 'Block traffic outside VPN')),
              subtitle: Text(
                _text(
                  context,
                  'Откройте Android → Always-on VPN → Блокировать подключения без VPN.',
                  'Open Android → Always-on VPN → Block connections without VPN.',
                ),
              ),
              onTap: _busy ? null : () => _perform(AndroidVpnSettings.open),
            ),
          const Divider(),
          if (android) ...[
            ChoicePreferenceWidget(
              selected: ref.watch(VpnPrivacyPreferences.copyStyle),
              preferences: ref.watch(VpnPrivacyPreferences.copyStyle.notifier),
              choices: const ['meet', 'notes'],
              enabled: !_busy,
              title: _text(context, 'Оформление скрытой копии', 'Private copy appearance'),
              icon: Icons.palette_outlined,
              presentChoice: (style) => style == 'meet'
                  ? _text(context, 'Meet — знакомства', 'Meet — dating')
                  : _text(context, 'Notes — заметки', 'Notes — notes'),
            ),
            ListTile(
              title: Text(_text(context, 'Пакет приложения', 'Application package')),
              subtitle: Text('${_identity['package'] ?? ''}'),
            ),
            ListTile(
              leading: const Icon(Icons.shuffle),
              title: Text(_text(context, 'Создать скрытую копию', 'Create private copy')),
              subtitle: Text(
                _text(
                  context,
                  'Случайный пакет и другое название, как в Magisk. Требуется отдельный APK, не split-установка.',
                  'Random package and custom label, like Magisk. Requires a standalone APK, not a split installation.',
                ),
              ),
              onTap: _busy ? null : _createCopy,
            ),
            if ((_identity['target'] as String? ?? '').isNotEmpty) ...[
              ListTile(
                title: Text(_text(context, 'Скрытый пакет', 'Private package')),
                subtitle: Text('${_identity['target']}'),
              ),
              ListTile(
                leading: const Icon(Icons.install_mobile),
                title: Text(_text(context, 'Установить скрытую копию', 'Install private copy')),
                onTap: _busy ? null : () => _perform(() => _channel.invokeMethod<void>('install_private_package')),
              ),
              ListTile(
                leading: const Icon(Icons.open_in_new),
                title: Text(_text(context, 'Открыть скрытую копию', 'Open private copy')),
                subtitle: Text(
                  _text(
                    context,
                    'При первом открытии переносит настройки и профили.',
                    'Migrates settings and profiles on first launch.',
                  ),
                ),
                enabled: _identity['installed'] == true,
                onTap: _busy || _identity['installed'] != true
                    ? null
                    : () => _perform(() => _channel.invokeMethod<void>('launch_private_package')),
              ),
            ],
            if ((_identity['original'] as String? ?? '').isNotEmpty)
              ListTile(
                leading: const Icon(Icons.delete_outline),
                title: Text(_text(context, 'Удалить исходное приложение', 'Remove original application')),
                subtitle: Text(
                  _text(
                    context,
                    'Сначала проверьте профили и подключение в этой копии. Android запросит подтверждение.',
                    'Verify profiles and connectivity in this copy first. Android will ask for confirmation.',
                  ),
                ),
                onTap: _busy ? null : () => _perform(() => _channel.invokeMethod<void>('remove_original_package')),
              ),
          ],
          Padding(
            padding: const EdgeInsets.all(20),
            child: Text(
              _text(
                context,
                'Репутацию выходного IP и признаки датацентра меняют выбором сервера. Root-режим всё равно создаёт TUN-интерфейс. Полное отсутствие локального туннеля возможно с роутером или внешним шлюзом.',
                'Exit IP reputation and hosting signals depend on the server. Root mode still creates a TUN interface. A router or external gateway can avoid a local tunnel.',
              ),
            ),
          ),
        ],
      ),
    );
  }
}
