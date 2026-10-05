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
      if (mounted) setState(() => _identity = identity);
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
    final name = TextEditingController(text: 'Application');
    final label = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_text(context, 'Скрыть пакет приложения', 'Hide application package')),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              _text(
                context,
                'Будет создана копия с новым случайным пакетом и подписью. VPN отключится. После установки нажмите «Открыть скрытую копию» здесь для переноса профилей. Исходная версия останется до её удаления вручную. Обычный APK обновления устанавливается как исходное приложение.',
                'Creates a copy with a random package and new signature. VPN will disconnect. After installation, use Open private copy here to migrate profiles. The original stays until you remove it. Regular update APKs install as the original application.',
              ),
            ),
            TextField(
              controller: name,
              maxLength: 40,
              decoration: InputDecoration(labelText: _text(context, 'Название приложения', 'Application name')),
            ),
          ],
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
          'databaseSnapshot': snapshot.path,
        });
        await _channel.invokeMethod<void>('install_private_package');
      } finally {
        if (await snapshot.exists()) await snapshot.delete();
      }
    });
  }

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
                'Эти настройки уменьшают локальные признаки и утечки. Android всё равно сообщает о сети VPN. Скрытие пакета не скрывает VpnService, IP сервера или геолокацию и не гарантирует прохождение RKNHardering.',
                'These settings reduce local signals and leaks. Android still reports the VPN network. Renaming the package does not hide VpnService, server IP or location, and does not guarantee passing RKNHardering.',
              ),
            ),
          ),
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
                'Репутацию выходного IP и признаки датацентра меняют выбором сервера. Для отсутствия локального VPN-интерфейса туннель нужно перенести на роутер или внешний шлюз.',
                'Exit IP reputation and hosting signals depend on the server. To avoid a local VPN interface, move the tunnel to a router or external gateway.',
              ),
            ),
          ),
        ],
      ),
    );
  }
}
