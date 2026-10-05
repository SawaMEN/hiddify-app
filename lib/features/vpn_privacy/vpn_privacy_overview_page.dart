import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/features/settings/notifier/config_option/config_option_notifier.dart';
import 'package:hiddify/features/vpn_privacy/privacy_app_selection_page.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_actions.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
import 'package:hiddify/singbox/model/singbox_config_enum.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

String _privacyText(BuildContext context, String ru, String en) =>
    Localizations.localeOf(context).languageCode == 'ru' ? ru : en;

class VpnPrivacyOverviewPage extends ConsumerStatefulWidget {
  const VpnPrivacyOverviewPage({super.key});

  static String title(BuildContext context) => _privacyText(context, 'Скрытие VPN', 'VPN privacy');

  @override
  ConsumerState<VpnPrivacyOverviewPage> createState() => _VpnPrivacyOverviewPageState();
}

class _VpnPrivacyOverviewPageState extends ConsumerState<VpnPrivacyOverviewPage> {
  static const _channel = MethodChannel('com.hiddify.app/method');

  bool _busy = false;
  bool? _routingReady;
  int _directApps = 0;
  int _proxyApps = 0;
  Map<dynamic, dynamic> _root = const {};
  bool _rootChecking = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await _refreshRoutingStatus();
      await _refreshRoot();
    });
  }

  void _showError(Object error) {
    if (!mounted) return;
    final message = error is PlatformException ? error.message ?? error.code : error.toString();
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _refreshRoutingStatus() async {
    if (!Platform.isAndroid) {
      if (mounted) setState(() => _routingReady = null);
      return;
    }
    try {
      final policy = await _channel.invokeMapMethod<dynamic, dynamic>('get_regional_routing') ?? const <dynamic, dynamic>{};
      if (!mounted) return;
      final direct = policy['privacy-direct-packages'];
      final proxy = policy['privacy-proxy-packages'];
      setState(() {
        _routingReady = policy['privacy-routing-mode'] == 'ru-bypass';
        _directApps = direct is List ? direct.length : 0;
        _proxyApps = proxy is List ? proxy.length : 0;
      });
    } catch (_) {
      if (mounted) setState(() => _routingReady = null);
    }
  }

  Future<void> _refreshRoot() async {
    if (!Platform.isAndroid) {
      if (mounted) setState(() => _rootChecking = false);
      return;
    }
    try {
      final root = await _channel.invokeMapMethod<dynamic, dynamic>('detect_root') ?? const {};
      if (!mounted) return;
      if (root['detected'] != true || root['helper'] != true) {
        await ref.read(VpnPrivacyPreferences.useRoot.notifier).update(false);
      }
      if (!mounted) return;
      setState(() {
        _root = root;
        _rootChecking = false;
      });
    } catch (_) {
      if (mounted) setState(() => _rootChecking = false);
    }
  }

  Future<void> _run(
    Future<void> Function() operation, {
    required String successRu,
    required String successEn,
    bool verifyRouting = false,
  }) async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      await operation();
      await _refreshRoutingStatus();
      if (!mounted) return;
      if (verifyRouting && Platform.isAndroid && _routingReady == false) {
        throw StateError(
          _privacyText(
            context,
            'Настройки сохранены, но Android не подтвердил активную политику маршрутизации.',
            'Settings were saved, but Android did not confirm the routing policy.',
          ),
        );
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_privacyText(context, successRu, successEn))),
        );
      }
    } catch (error) {
      _showError(error);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _openSelection(PrivacyAppSelectionKind kind) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => PrivacyAppSelectionPage(kind: kind)),
    );
    if (mounted) await _refreshRoutingStatus();
  }

  Future<void> _setRoot(bool enabled) async {
    await _run(
      () async {
        if (enabled) {
          final status = await _channel.invokeMapMethod<dynamic, dynamic>('check_root') ?? const {};
          if (!mounted) return;
          if (mounted) setState(() => _root = status);
          if (status['granted'] != true) {
            throw StateError(_privacyText(context, 'Root-доступ не предоставлен', 'Root permission was not granted'));
          }
          if (status['helper'] != true) {
            throw StateError(
              _privacyText(context, 'В этом APK отсутствует root-ядро', 'This APK has no root companion'),
            );
          }
        }
        await ref.read(configOptionNotifierProvider.notifier).updateTogether(
          () async {
            if (enabled) await ref.read(ConfigOptions.serviceMode.notifier).update(ServiceMode.tun);
            await ref.read(VpnPrivacyPreferences.useRoot.notifier).update(enabled);
          },
          applyImmediately: true,
        );
      },
      successRu: enabled ? 'Root-режим включён.' : 'Root-режим отключён.',
      successEn: enabled ? 'Root mode enabled.' : 'Root mode disabled.',
    );
  }

  Future<void> _setPrivacyBool(
    Future<void> Function() update, {
    required String successRu,
    required String successEn,
  }) =>
      _run(
        () => ref.read(configOptionNotifierProvider.notifier).updateTogether(update, applyImmediately: true),
        successRu: successRu,
        successEn: successEn,
      );

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final configured = VpnPrivacyActions.isConfigured(ref);
    final canRestore = ref.watch(VpnPrivacyPreferences.automaticSetupBackup).isNotEmpty;
    final android = Platform.isAndroid;
    final russianNetwork = ref.watch(VpnPrivacyPreferences.russianNetworkBypass);
    final russianApps = ref.watch(VpnPrivacyPreferences.russianAppsBypass);
    final restrictedServices = ref.watch(VpnPrivacyPreferences.restrictedServicesProxy);

    final statusText = configured
        ? _privacyText(context, 'Автоматическая защита включена', 'Automatic protection is enabled')
        : _privacyText(context, 'Можно настроить в одно нажатие', 'Ready for one-tap setup');
    final statusDescription = configured
        ? _privacyText(
            context,
            _routingReady == false
                ? 'Настройки сохранены, но политика маршрутизации требует повторного применения.'
                : 'Российский сетевой обход, приложения и заблокированные сервисы управляются независимыми правилами.',
            _routingReady == false
                ? 'Settings are saved, but routing needs to be applied again.'
                : 'Russian network bypass, apps, and restricted services are controlled independently.',
          )
        : _privacyText(
            context,
            'Приложение само выставит рекомендуемые параметры DNS, маршрутизации и защиты от утечек.',
            'The app will choose recommended DNS, routing, and leak-protection settings.',
          );

    return Scaffold(
      appBar: AppBar(title: Text(VpnPrivacyOverviewPage.title(context))),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
        children: [
          Card(
            clipBehavior: Clip.antiAlias,
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 52,
                        height: 52,
                        decoration: BoxDecoration(
                          color: configured
                              ? theme.colorScheme.primaryContainer
                              : theme.colorScheme.surfaceContainerHighest,
                          borderRadius: BorderRadius.circular(16),
                        ),
                        child: Icon(
                          configured ? Icons.shield_rounded : Icons.shield_outlined,
                          color: configured ? theme.colorScheme.onPrimaryContainer : theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(statusText, style: theme.textTheme.titleLarge),
                            const SizedBox(height: 6),
                            Text(statusDescription, style: theme.textTheme.bodyMedium),
                          ],
                        ),
                      ),
                    ],
                  ),
                  if (android && _routingReady != null) ...[
                    const SizedBox(height: 16),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                      decoration: BoxDecoration(
                        color: _routingReady == true
                            ? theme.colorScheme.secondaryContainer
                            : theme.colorScheme.errorContainer,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Row(
                        children: [
                          Icon(_routingReady == true ? Icons.check_circle_outline : Icons.error_outline, size: 20),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _routingReady == true
                                  ? _privacyText(
                                      context,
                                      'Политика активна · напрямую: $_directApps · через VPN: $_proxyApps',
                                      'Policy active · direct: $_directApps · through VPN: $_proxyApps',
                                    )
                                  : _privacyText(
                                      context,
                                      'Политика маршрутизации сейчас отключена',
                                      'Routing policy is currently disabled',
                                    ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          SizedBox(
            height: 52,
            child: FilledButton.icon(
              onPressed: _busy
                  ? null
                  : () => _run(
                      () => VpnPrivacyActions.configureAutomatically(ref),
                      successRu: 'Автоматическая защита настроена и применена.',
                      successEn: 'Automatic protection was configured and applied.',
                      verifyRouting: true,
                    ),
              icon: const Icon(Icons.auto_awesome),
              label: Text(
                _privacyText(
                  context,
                  configured ? 'Применить автоматические настройки снова' : 'Настроить всё автоматически',
                  configured ? 'Apply automatic setup again' : 'Configure automatically',
                ),
              ),
            ),
          ),
          const SizedBox(height: 10),
          SizedBox(
            height: 48,
            child: OutlinedButton.icon(
              onPressed: _busy || !canRestore
                  ? null
                  : () => _run(
                      () => VpnPrivacyActions.restorePrevious(ref),
                      successRu: 'Предыдущие настройки восстановлены.',
                      successEn: 'Previous settings were restored.',
                    ),
              icon: const Icon(Icons.settings_backup_restore),
              label: Text(_privacyText(context, 'Вернуть всё как было', 'Restore previous settings')),
            ),
          ),
          if (_busy) ...[
            const SizedBox(height: 14),
            const LinearProgressIndicator(),
          ],
          const SizedBox(height: 24),
          Text(_privacyText(context, 'Что настраивается', 'What gets configured'), style: theme.textTheme.titleMedium),
          const SizedBox(height: 10),
          Card(
            child: Column(
              children: [
                _FeatureRow(
                  icon: Icons.apps_rounded,
                  title: _privacyText(context, 'Российские приложения', 'Russian apps'),
                  subtitle: _privacyText(
                    context,
                    russianApps
                        ? '$_directApps приложений идут напрямую, вне VPN. Нажмите, чтобы изменить список.'
                        : 'Обход для приложений выключен. Сохранённый список не удалён.',
                    russianApps
                        ? '$_directApps apps go directly outside the VPN. Tap to edit the list.'
                        : 'App bypass is disabled. The saved list is preserved.',
                  ),
                  onTap: android && !_busy ? () => _openSelection(PrivacyAppSelectionKind.direct) : null,
                ),
                const Divider(height: 1, indent: 56),
                _FeatureRow(
                  icon: Icons.route_rounded,
                  title: _privacyText(context, 'Заблокированные сервисы', 'Restricted services'),
                  subtitle: _privacyText(
                    context,
                    restrictedServices
                        ? '$_proxyApps приложений принудительно идут через VPN. Нажмите, чтобы изменить список.'
                        : 'Принудительный обход блокировок выключен. Сохранённый список не удалён.',
                    restrictedServices
                        ? '$_proxyApps apps are forced through the VPN. Tap to edit the list.'
                        : 'Forced VPN routing is disabled. The saved list is preserved.',
                  ),
                  onTap: android && !_busy ? () => _openSelection(PrivacyAppSelectionKind.proxy) : null,
                ),
                const Divider(height: 1, indent: 56),
                _FeatureRow(
                  icon: Icons.language_outlined,
                  title: _privacyText(context, 'Российская сеть', 'Russian network'),
                  subtitle: _privacyText(
                    context,
                    russianNetwork
                        ? '.ru, .рф, .su, российские geosite и IP идут напрямую.'
                        : 'Российские домены и IP идут по обычным правилам VPN.',
                    russianNetwork
                        ? '.ru, .рф, .su, Russian geosite and IP ranges go direct.'
                        : 'Russian domains and IP ranges follow normal VPN rules.',
                  ),
                ),
                const Divider(height: 1, indent: 56),
                _FeatureRow(
                  icon: Icons.dns_outlined,
                  title: _privacyText(context, 'DNS и сетевые признаки', 'DNS and network signals'),
                  subtitle: _privacyText(
                    context,
                    'Включается зашифрованный DNS, скрывается системный HTTP-прокси и локальные служебные порты.',
                    'Encrypted DNS is enabled while the system HTTP proxy and local control ports are hidden.',
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Card(
            child: ExpansionTile(
              leading: const Icon(Icons.tune_rounded),
              title: Text(_privacyText(context, 'Для опытных пользователей', 'For advanced users')),
              subtitle: Text(
                _privacyText(
                  context,
                  'Маршрутизация, Root, DNS и сетевые параметры',
                  'Routing, Root, DNS and network options',
                ),
              ),
              children: [
                SwitchListTile.adaptive(
                  value: russianNetwork,
                  onChanged: _busy
                      ? null
                      : (value) => _setPrivacyBool(
                            () => ref.read(VpnPrivacyPreferences.russianNetworkBypass.notifier).update(value),
                            successRu: value
                                ? 'Прямой доступ к российским доменам и IP включён.'
                                : 'Российские домены и IP теперь идут по обычным правилам VPN.',
                            successEn: value
                                ? 'Direct routing for Russian domains and IP ranges enabled.'
                                : 'Russian domains and IP ranges now follow normal VPN rules.',
                          ),
                  secondary: const Icon(Icons.language_outlined),
                  title: Text(
                    _privacyText(context, 'Российские домены и IP напрямую', 'Russian domains and IPs direct'),
                  ),
                  subtitle: Text(
                    _privacyText(
                      context,
                      'Старое поведение региона «Россия»: .ru, .рф, .su, geosite-ru и geoip-ru. Выключение соответствует прежнему режиму «Другой».',
                      'Old Russia-region behavior: .ru, .рф, .su, geosite-ru and geoip-ru. Turning it off matches the old Other mode.',
                    ),
                  ),
                ),
                SwitchListTile.adaptive(
                  value: russianApps,
                  onChanged: _busy
                      ? null
                      : (value) => _setPrivacyBool(
                            () => ref.read(VpnPrivacyPreferences.russianAppsBypass.notifier).update(value),
                            successRu: value
                                ? 'Прямой доступ для российских приложений включён.'
                                : 'Прямой доступ для российских приложений отключён.',
                            successEn: value
                                ? 'Direct routing for Russian apps enabled.'
                                : 'Direct routing for Russian apps disabled.',
                          ),
                  secondary: const Icon(Icons.apps_outlined),
                  title: Text(
                    _privacyText(context, 'Российские приложения напрямую', 'Russian apps direct'),
                  ),
                  subtitle: Text(
                    _privacyText(
                      context,
                      'Применяет список «Российские приложения». При отключении выбранные галочки сохраняются.',
                      'Applies the Russian apps list. Saved selections are preserved while disabled.',
                    ),
                  ),
                ),
                SwitchListTile.adaptive(
                  value: restrictedServices,
                  onChanged: _busy
                      ? null
                      : (value) => _setPrivacyBool(
                            () => ref.read(VpnPrivacyPreferences.restrictedServicesProxy.notifier).update(value),
                            successRu: value
                                ? 'Маршрутизация заблокированных сервисов через VPN включена.'
                                : 'Маршрутизация заблокированных сервисов через VPN отключена.',
                            successEn: value
                                ? 'VPN routing for restricted services enabled.'
                                : 'VPN routing for restricted services disabled.',
                          ),
                  secondary: const Icon(Icons.route_outlined),
                  title: Text(
                    _privacyText(context, 'Заблокированные сервисы через VPN', 'Restricted services through VPN'),
                  ),
                  subtitle: Text(
                    _privacyText(
                      context,
                      'Применяет список «Заблокированные сервисы». При отключении выбранные галочки сохраняются.',
                      'Applies the Restricted services list. Saved selections are preserved while disabled.',
                    ),
                  ),
                ),
                const Divider(height: 1),
                if (android)
                  SwitchListTile.adaptive(
                    value: ref.watch(VpnPrivacyPreferences.useRoot),
                    onChanged: _busy || _rootChecking || _root['detected'] != true || _root['helper'] != true
                        ? null
                        : _setRoot,
                    secondary: const Icon(Icons.admin_panel_settings_outlined),
                    title: Text(_privacyText(context, 'Подключение через root', 'Connect using root')),
                    subtitle: Text(
                      _privacyText(
                        context,
                        _rootChecking
                            ? 'Проверяется наличие root…'
                            : _root['detected'] != true
                            ? 'Root на устройстве не обнаружен.'
                            : _root['helper'] != true
                            ? 'В этом APK нет исполняемого root-ядра.'
                            : 'Запускает ядро без Android VpnService. Требуется разрешение root.',
                        _rootChecking
                            ? 'Checking root availability…'
                            : _root['detected'] != true
                            ? 'Root was not detected on this device.'
                            : _root['helper'] != true
                            ? 'This APK has no executable root companion.'
                            : 'Runs the core without Android VpnService. Root permission is required.',
                      ),
                    ),
                  ),
                SwitchListTile.adaptive(
                  value: ref.watch(VpnPrivacyPreferences.fullTunnel),
                  onChanged: _busy
                      ? null
                      : (value) => _setPrivacyBool(
                            () => ref.read(VpnPrivacyPreferences.fullTunnel.notifier).update(value),
                            successRu: value ? 'Полный туннель включён.' : 'Полный туннель отключён.',
                            successEn: value ? 'Full tunnel enabled.' : 'Full tunnel disabled.',
                          ),
                  secondary: const Icon(Icons.security_outlined),
                  title: Text(_privacyText(context, 'Полный туннель', 'Full tunnel')),
                  subtitle: Text(
                    _privacyText(
                      context,
                      'Отправляет весь пользовательский трафик через VPN и временно имеет приоритет над прямыми российскими исключениями.',
                      'Routes all user traffic through the VPN and temporarily takes priority over direct Russian bypass rules.',
                    ),
                  ),
                ),
                SwitchListTile.adaptive(
                  value: ref.watch(VpnPrivacyPreferences.encryptedDns),
                  onChanged: _busy
                      ? null
                      : (value) => _setPrivacyBool(
                            () => ref.read(VpnPrivacyPreferences.encryptedDns.notifier).update(value),
                            successRu: 'Настройка DNS применена.',
                            successEn: 'DNS setting applied.',
                          ),
                  secondary: const Icon(Icons.dns_outlined),
                  title: Text(_privacyText(context, 'Зашифрованный DNS через VPN', 'Encrypted DNS through VPN')),
                  subtitle: Text(
                    _privacyText(
                      context,
                      'Использует DoH внутри VPN. Рекомендуется оставить включённым.',
                      'Uses DoH inside the VPN. Recommended to keep enabled.',
                    ),
                  ),
                ),
                if (android)
                  SwitchListTile.adaptive(
                    value: ref.watch(VpnPrivacyPreferences.publicDns),
                    onChanged: _busy
                        ? null
                        : (value) => _setPrivacyBool(
                              () => ref.read(VpnPrivacyPreferences.publicDns.notifier).update(value),
                              successRu: 'Отображение DNS применено.',
                              successEn: 'DNS network display setting applied.',
                            ),
                    secondary: const Icon(Icons.public),
                    title: Text(
                      _privacyText(context, 'Публичный DNS в сведениях о сети', 'Public DNS in network information'),
                    ),
                    subtitle: Text(
                      _privacyText(
                        context,
                        'Показывает публичный DNS вместо внутреннего адреса туннеля; запросы всё равно перехватывает ядро.',
                        'Shows a public DNS address instead of the tunnel address; the core still intercepts DNS queries.',
                      ),
                    ),
                  ),
                SwitchListTile.adaptive(
                  value: ref.watch(VpnPrivacyPreferences.hideLocalProxy),
                  onChanged: _busy
                      ? null
                      : (value) => _setPrivacyBool(
                            () => ref.read(VpnPrivacyPreferences.hideLocalProxy.notifier).update(value),
                            successRu: 'Настройка локального прокси применена.',
                            successEn: 'Local proxy setting applied.',
                          ),
                  secondary: const Icon(Icons.portable_wifi_off_outlined),
                  title: Text(_privacyText(context, 'Не открывать локальный прокси', 'Disable local proxy')),
                  subtitle: Text(
                    _privacyText(
                      context,
                      'Не поднимает лишние HTTP/SOCKS/direct listener в режиме VPN.',
                      'Avoids extra HTTP/SOCKS/direct listeners in VPN mode.',
                    ),
                  ),
                ),
                SwitchListTile.adaptive(
                  value: ref.watch(VpnPrivacyPreferences.hideClashApi),
                  onChanged: _busy
                      ? null
                      : (value) => _setPrivacyBool(
                            () => ref.read(VpnPrivacyPreferences.hideClashApi.notifier).update(value),
                            successRu: 'Настройка Clash API применена.',
                            successEn: 'Clash API setting applied.',
                          ),
                  secondary: const Icon(Icons.api_outlined),
                  title: Text(_privacyText(context, 'Не открывать Clash API', 'Disable Clash API')),
                  subtitle: Text(
                    _privacyText(
                      context,
                      'Не открывает внешний REST-порт управления; внутренняя статистика сохраняется.',
                      'Does not expose the external REST control port; internal statistics remain available.',
                    ),
                  ),
                ),
                SwitchListTile.adaptive(
                  value: ref.watch(VpnPrivacyPreferences.disableSystemProxy),
                  onChanged: _busy
                      ? null
                      : (value) => _setPrivacyBool(
                            () => ref.read(VpnPrivacyPreferences.disableSystemProxy.notifier).update(value),
                            successRu: 'Настройка системного прокси применена.',
                            successEn: 'System proxy setting applied.',
                          ),
                  secondary: const Icon(Icons.http_outlined),
                  title: Text(
                    _privacyText(context, 'Не объявлять системный HTTP-прокси', 'Disable system HTTP proxy'),
                  ),
                  subtitle: Text(
                    _privacyText(
                      context,
                      'Не добавляет HTTP-прокси в сведения о VPN-сети.',
                      'Does not advertise an HTTP proxy on the VPN network.',
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4),
            child: Text(
              _privacyText(
                context,
                'Скрытие VPN снижает количество локальных признаков, но не может гарантировать полную незаметность для любого приложения или сервера.',
                'VPN privacy reduces local signals but cannot guarantee complete invisibility to every app or server.',
              ),
              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ),
        ],
      ),
    );
  }
}

class _FeatureRow extends StatelessWidget {
  const _FeatureRow({required this.icon, required this.title, required this.subtitle, this.onTap});

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    child: Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(padding: const EdgeInsets.only(top: 2), child: Icon(icon, size: 22)),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: Theme.of(context).textTheme.titleSmall),
                const SizedBox(height: 3),
                Text(subtitle, style: Theme.of(context).textTheme.bodyMedium),
              ],
            ),
          ),
          if (onTap != null) ...[
            const SizedBox(width: 8),
            const Icon(Icons.chevron_right_rounded),
          ],
        ],
      ),
    ),
  );
}
