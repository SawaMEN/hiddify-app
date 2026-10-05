import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hiddify/core/model/region.dart';
import 'package:hiddify/features/per_app_proxy/model/app_package_info.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/features/settings/notifier/config_option/config_option_notifier.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:installed_apps/index.dart';

enum PrivacyAppSelectionKind { direct, proxy }

String _selectionText(BuildContext context, String ru, String en) =>
    Localizations.localeOf(context).languageCode == 'ru' ? ru : en;

class PrivacyAppSelectionPage extends ConsumerStatefulWidget {
  const PrivacyAppSelectionPage({super.key, required this.kind});

  final PrivacyAppSelectionKind kind;

  @override
  ConsumerState<PrivacyAppSelectionPage> createState() => _PrivacyAppSelectionPageState();
}

class _PrivacyAppSelectionPageState extends ConsumerState<PrivacyAppSelectionPage> {
  static const _channel = MethodChannel('com.hiddify.app/method');

  final _search = TextEditingController();
  List<AppPackageInfo> _apps = const [];
  Set<String> _selected = <String>{};
  Set<String> _automatic = <String>{};
  bool _loading = true;
  bool _saving = false;
  bool _manual = false;
  String _query = '';

  bool get _direct => widget.kind == PrivacyAppSelectionKind.direct;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    if (!PlatformUtils.isAndroid) {
      if (mounted) setState(() => _loading = false);
      return;
    }
    try {
      final installed = await InstalledApps.getInstalledApps(false, true);
      final policy =
          await _channel.invokeMapMethod<dynamic, dynamic>('get_regional_routing', {
            'region': ref.read(ConfigOptions.region).name,
          }) ??
          const <dynamic, dynamic>{};
      final effectiveKey = _direct ? 'privacy-direct-packages' : 'privacy-proxy-packages';
      final automaticKey = _direct ? 'privacy-default-direct-packages' : 'privacy-default-proxy-packages';
      final manualKey = _direct ? 'privacy-direct-packages-manual' : 'privacy-proxy-packages-manual';
      if (!mounted) return;
      final selected = (policy[effectiveKey] as List? ?? const []).whereType<String>().toSet();
      final automatic = (policy[automaticKey] as List? ?? const []).whereType<String>().toSet();
      final apps = installed
          .map((app) => AppPackageInfo(packageName: app.packageName, name: app.name, icon: app.icon))
          .where((app) => app.packageName != 'com.hiddify.app')
          .toList();
      apps.sort((a, b) {
        final selectedOrder = (selected.contains(b.packageName) ? 1 : 0) - (selected.contains(a.packageName) ? 1 : 0);
        if (selectedOrder != 0) return selectedOrder;
        return a.name.toLowerCase().compareTo(b.name.toLowerCase());
      });
      setState(() {
        _apps = apps;
        _selected = selected;
        _automatic = automatic;
        _manual = policy[manualKey] == true;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() => _loading = false);
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.toString())));
    }
  }

  Future<void> _save() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      final encoded = VpnPrivacyPreferences.encodeManualPackages(_selected);
      await ref.read(configOptionNotifierProvider.notifier).updateTogether(
        () async {
          await ref.read(ConfigOptions.region.notifier).update(Region.ru);
          await ref.read(VpnPrivacyPreferences.routingMode.notifier).update('ru-bypass');
          if (_direct) {
            await ref.read(VpnPrivacyPreferences.customDirectPackages.notifier).update(encoded);
            final other = ref.read(VpnPrivacyPreferences.customProxyPackages);
            if (VpnPrivacyPreferences.hasManualPackages(other)) {
              final cleaned = VpnPrivacyPreferences.decodeManualPackages(other).difference(_selected);
              await ref
                  .read(VpnPrivacyPreferences.customProxyPackages.notifier)
                  .update(VpnPrivacyPreferences.encodeManualPackages(cleaned));
            }
          } else {
            await ref.read(VpnPrivacyPreferences.customProxyPackages.notifier).update(encoded);
            final other = ref.read(VpnPrivacyPreferences.customDirectPackages);
            if (VpnPrivacyPreferences.hasManualPackages(other)) {
              final cleaned = VpnPrivacyPreferences.decodeManualPackages(other).difference(_selected);
              await ref
                  .read(VpnPrivacyPreferences.customDirectPackages.notifier)
                  .update(VpnPrivacyPreferences.encodeManualPackages(cleaned));
            }
          }
        },
        applyImmediately: true,
      );
      if (!mounted) return;
      setState(() => _manual = true);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _selectionText(
              context,
              'Список сохранён и синхронизирован с маршрутизацией.',
              'The list was saved and synchronized with routing.',
            ),
          ),
        ),
      );
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.toString())));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _resetAutomatic() async {
    if (_saving) return;
    setState(() => _saving = true);
    try {
      await ref.read(configOptionNotifierProvider.notifier).updateTogether(
        () async {
          await ref.read(ConfigOptions.region.notifier).update(Region.ru);
          await ref.read(VpnPrivacyPreferences.routingMode.notifier).update('ru-bypass');
          if (_direct) {
            await ref.read(VpnPrivacyPreferences.customDirectPackages.notifier).update('');
          } else {
            await ref.read(VpnPrivacyPreferences.customProxyPackages.notifier).update('');
          }
        },
        applyImmediately: true,
      );
      if (!mounted) return;
      setState(() {
        _selected = Set<String>.from(_automatic);
        _manual = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _selectionText(
              context,
              'Восстановлен встроенный автовыбор из APK. Интернет не используется.',
              'Bundled APK defaults were restored. No internet list is used.',
            ),
          ),
        ),
      );
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.toString())));
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = _direct
        ? _selectionText(context, 'Российские приложения', 'Russian apps')
        : _selectionText(context, 'Заблокированные сервисы', 'Restricted services');
    final hint = _direct
        ? _selectionText(
            context,
            'Отмеченные приложения идут напрямую, вне VPN. Сохранение ручного списка отключает автовыбор до сброса.',
            'Checked apps go directly, outside the VPN. Saving a manual list disables automatic selection until reset.',
          )
        : _selectionText(
            context,
            'Отмеченные приложения принудительно идут через VPN для обхода блокировок. Сохранение ручного списка отключает автовыбор до сброса.',
            'Checked apps are forced through the VPN to bypass blocking. Saving a manual list disables automatic selection until reset.',
          );
    final query = _query.trim().toLowerCase();
    final visible = _apps.where((app) {
      if (query.isEmpty) return true;
      return app.name.toLowerCase().contains(query) || app.packageName.toLowerCase().contains(query);
    }).toList();

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: TextField(
              controller: _search,
              onChanged: (value) => setState(() => _query = value),
              decoration: InputDecoration(
                prefixIcon: const Icon(Icons.search),
                hintText: _selectionText(context, 'Поиск приложения', 'Search apps'),
                suffixIcon: _query.isEmpty
                    ? null
                    : IconButton(
                        onPressed: () {
                          _search.clear();
                          setState(() => _query = '');
                        },
                        icon: const Icon(Icons.close),
                      ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: Row(
              children: [
                Icon(_manual ? Icons.edit_outlined : Icons.auto_awesome, size: 18),
                const SizedBox(width: 8),
                Expanded(child: Text(hint)),
              ],
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : ListView.builder(
                    itemCount: visible.length,
                    itemBuilder: (context, index) {
                      final app = visible[index];
                      final checked = _selected.contains(app.packageName);
                      return CheckboxListTile.adaptive(
                        value: checked,
                        onChanged: _saving
                            ? null
                            : (value) => setState(() {
                                if (value == true) {
                                  _selected.add(app.packageName);
                                } else {
                                  _selected.remove(app.packageName);
                                }
                              }),
                        title: Text(app.name, maxLines: 1, overflow: TextOverflow.ellipsis),
                        subtitle: Text(app.packageName, maxLines: 1, overflow: TextOverflow.ellipsis),
                        secondary: app.icon == null
                            ? const Icon(Icons.android)
                            : Image.memory(app.icon!, width: 42, height: 42, cacheWidth: 42, cacheHeight: 42),
                      );
                    },
                  ),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _saving || _loading ? null : _resetAutomatic,
                icon: const Icon(Icons.restart_alt),
                label: Text(_selectionText(context, 'Сбросить (автовыбор)', 'Reset (automatic)')),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: FilledButton.icon(
                onPressed: _saving || _loading ? null : _save,
                icon: const Icon(Icons.save_outlined),
                label: Text(_selectionText(context, 'Сохранить', 'Save')),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
