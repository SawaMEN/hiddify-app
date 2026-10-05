import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_actions.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_page.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
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

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _refreshRoutingStatus());
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
      final policy =
          await _channel.invokeMapMethod<dynamic, dynamic>('get_regional_routing', {
            'region': ref.read(ConfigOptions.region).name,
          }) ??
          const <dynamic, dynamic>{};
      if (!mounted) return;
      final packages = policy['privacy-direct-packages'];
      setState(() {
        _routingReady = policy['privacy-routing-mode'] == 'ru-bypass';
        _directApps = packages is List ? packages.length : 0;
      });
    } catch (_) {
      if (mounted) setState(() => _routingReady = null);
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
      if (verifyRouting && Platform.isAndroid && _routingReady == false) {
        throw StateError(
          _privacyText(
            context,
            'Маршрутизация сохранена, но Android не подтвердил активную политику РФ.',
            'Routing was saved, but Android did not confirm the Russia policy.',
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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final configured = VpnPrivacyActions.isConfigured(ref);
    final canRestore = ref.watch(VpnPrivacyPreferences.automaticSetupBackup).isNotEmpty;
    final android = Platform.isAndroid;

    final statusText = configured
        ? _privacyText(context, 'Автоматическая защита включена', 'Automatic protection is enabled')
        : _privacyText(context, 'Можно настроить в одно нажатие', 'Ready for one-tap setup');
    final statusDescription = configured
        ? _privacyText(
            context,
            _routingReady == false
                ? 'Настройки сохранены, но маршрутизация требует повторного применения.'
                : 'Российские приложения идут напрямую, а сервисы из списка ограничений — через VPN.',
            _routingReady == false
                ? 'Settings are saved, but routing needs to be applied again.'
                : 'Russian apps use the direct network while restricted services use the VPN.',
          )
        : _privacyText(
            context,
            'Приложение само выставит рекомендуемые параметры маршрутизации, DNS и защиты от утечек.',
            'The app will choose recommended routing, DNS, and leak-protection settings.',
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
                  if (configured && android && _routingReady != null) ...[
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
                          Icon(
                            _routingReady == true ? Icons.check_circle_outline : Icons.error_outline,
                            size: 20,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              _routingReady == true
                                  ? _privacyText(
                                      context,
                                      _directApps > 0
                                          ? 'Маршрутизация активна · $_directApps установленных приложений исключено из VPN'
                                          : 'Маршрутизация РФ активна',
                                      _directApps > 0
                                          ? 'Routing active · $_directApps installed apps bypass the VPN'
                                          : 'Russia routing is active',
                                    )
                                  : _privacyText(
                                      context,
                                      'Маршрутизация РФ сейчас не активна',
                                      'Russia routing is not active',
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
                    'Известные приложения с проверками VPN автоматически идут напрямую.',
                    'Known VPN-aware apps automatically use the direct network.',
                  ),
                ),
                const Divider(height: 1, indent: 56),
                _FeatureRow(
                  icon: Icons.route_rounded,
                  title: _privacyText(context, 'Заблокированные сервисы', 'Restricted services'),
                  subtitle: _privacyText(
                    context,
                    'Приложения и домены из списка ограничений продолжают работать через VPN.',
                    'Apps and domains from the restricted list continue through the VPN.',
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
                const Divider(height: 1, indent: 56),
                _FeatureRow(
                  icon: Icons.sync_rounded,
                  title: _privacyText(context, 'Стабильное восстановление', 'Reliable recovery'),
                  subtitle: _privacyText(
                    context,
                    'Включается автоматическое переподключение и безопасный IPv4-режим.',
                    'Automatic reconnection and a conservative IPv4 mode are enabled.',
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
                  'Root, ручная маршрутизация, MTU, пакеты и домены',
                  'Root, manual routing, MTU, packages and domains',
                ),
              ),
              childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
              children: [
                Text(
                  _privacyText(
                    context,
                    'Меняйте эти параметры только если понимаете, как они влияют на подключение. Обычной автонастройке они не нужны.',
                    'Change these options only if you understand their effect on connectivity. Normal automatic setup does not require them.',
                  ),
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: _busy
                        ? null
                        : () async {
                            await Navigator.of(context).push(
                              MaterialPageRoute<void>(builder: (_) => const VpnPrivacyPage()),
                            );
                            if (mounted) await _refreshRoutingStatus();
                          },
                    icon: const Icon(Icons.tune),
                    label: Text(_privacyText(context, 'Открыть расширенные настройки', 'Open advanced settings')),
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
  const _FeatureRow({required this.icon, required this.title, required this.subtitle});

  final IconData icon;
  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) => Padding(
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
      ],
    ),
  );
}
