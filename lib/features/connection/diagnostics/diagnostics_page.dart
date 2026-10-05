import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:material_ui/material_ui.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:share_plus/share_plus.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/privacy/redactor.dart';
import 'package:hiddify/core/app_info/app_info_provider.dart';
import 'package:hiddify/features/connection/health/connection_health.dart';
import 'package:hiddify/features/connection/health/android_vpn_settings.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/profile/data/profile_data_providers.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/features/profile/import/import_summary.dart';
import 'package:hiddify/features/proxy/active/active_proxy_notifier.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';

class DiagnosticResult {
  const DiagnosticResult(this.stage, this.result, this.detail);
  final String stage, result, detail;
}

class DiagnosticsPage extends ConsumerStatefulWidget {
  const DiagnosticsPage({super.key});
  @override
  ConsumerState<DiagnosticsPage> createState() => _DiagnosticsPageState();
}

class _DiagnosticsPageState extends ConsumerState<DiagnosticsPage> {
  bool _running = false;
  int _generation = 0;
  List<DiagnosticResult> _results = [];
  @override
  void dispose() {
    _generation++;
    super.dispose();
  }

  Future<void> _run() async {
    if (_running) return;
    final t = ref.read(translationsProvider).requireValue;
    final generation = ++_generation;
    setState(() {
      _running = true;
      _results = [];
    });
    void add(String stage, String result, String detail) {
      if (mounted && generation == _generation)
        setState(() => _results = [..._results, DiagnosticResult(stage, result, detail)]);
    }

    try {
      try {
        final native = await AndroidVpnSettings.networkAvailable().timeout(const Duration(seconds: 4));
        final available =
            native ??
            (await NetworkInterface.list(includeLoopback: false).timeout(const Duration(seconds: 4))).isNotEmpty;
        add(t.client.network, available ? t.client.passed : t.client.failed, 'Underlying network interface');
      } catch (e) {
        add(t.client.network, t.client.failed, e.runtimeType.toString());
      }
      final probe = Uri.tryParse(ref.read(ConfigOptions.connectionTestUrl));
      try {
        if (probe == null || probe.host.isEmpty) throw const FormatException('Invalid test URL');
        final addresses = await InternetAddress.lookup(probe.host).timeout(const Duration(seconds: 5));
        add(
          t.client.dns,
          addresses.isNotEmpty ? t.client.passed : t.client.failed,
          'System DNS only; tunneled DNS is included in the VPN HTTP check',
        );
      } catch (e) {
        add(t.client.dns, t.client.failed, e.runtimeType.toString());
      }
      if (!mounted || generation != _generation) return;
      try {
        final profile = await ref.read(activeProfileProvider.future);
        ServerEndpoint? endpoint;
        var chained = false;
        if (profile != null) {
          final raw = (await ref.read(profileRepositoryProvider).requireValue.getRawConfig(profile.id).run()).match(
            (e) => throw e,
            (v) => v,
          );
          final summary = ImportSummary.parse(raw, profile.populatedHeaders ?? {});
          final tag = ref.read(activeProxyNotifierProvider).value?.tag;
          final matches = summary.servers.where((s) => s.tag == tag).toList();
          if (matches.length == 1)
            endpoint = matches.first;
          else if (summary.servers.length == 1)
            endpoint = summary.servers.first;
          chained =
              summary.overrides.any((k) => k.contains('chain') || k.contains('enable-')) ||
              raw.contains('"detour"') ||
              raw.contains('"dialerProxy"');
        }
        if (endpoint == null || endpoint.host.isEmpty || endpoint.port < 1 || endpoint.port > 65535) {
          add(t.client.server, t.client.skipped, t.client.noEndpoint);
        } else if (endpoint.udp || chained) {
          add(t.client.server, t.client.skipped, t.client.transportSkipped);
        } else {
          final socket = await Socket.connect(endpoint.host, endpoint.port, timeout: const Duration(seconds: 5));
          socket.destroy();
          add(
            t.client.server,
            t.client.passed,
            'TCP reachability only; this does not validate authentication or the VPN protocol',
          );
        }
      } catch (e) {
        add(t.client.server, t.client.failed, e.runtimeType.toString());
      }
      if (!mounted || generation != _generation) return;
      if (!ref.read(serviceRunningProvider)) {
        add(t.client.tunnel, t.client.skipped, t.client.disconnected);
      } else {
        await ref.read(connectionHealthProvider.notifier).check();
        if (!mounted || generation != _generation) return;
        final health = ref.read(connectionHealthProvider);
        add(
          t.client.tunnel,
          health == InternetHealth.available
              ? t.client.passed
              : health == InternetHealth.unavailable
              ? t.client.failed
              : t.client.skipped,
          'HTTP check through local VPN proxy, without direct fallback',
        );
      }
    } finally {
      if (mounted && generation == _generation) setState(() => _running = false);
    }
  }

  String _report() {
    final info = ref.read(appInfoProvider).requireValue;
    return const JsonEncoder.withIndent('  ').convert(
      DiagnosticRedactor.value({
        'version': info.version,
        'platform': Platform.operatingSystem,
        'time': DateTime.now().toUtc().toIso8601String(),
        'vpnStarted': ref.read(serviceRunningProvider),
        'internet': ref.read(connectionHealthProvider).name,
        'checks': _results.map((r) => {'stage': r.stage, 'result': r.result, 'detail': r.detail}).toList(),
      }),
    );
  }

  @override
  Widget build(BuildContext context) {
    final t = ref.watch(translationsProvider).requireValue;
    return Scaffold(
      appBar: AppBar(title: Text(t.client.diagnostics)),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          FilledButton.icon(
            onPressed: _running ? null : _run,
            icon: _running
                ? const SizedBox.square(dimension: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.play_arrow_rounded),
            label: Text(t.client.runDiagnostics),
          ),
          const SizedBox(height: 16),
          for (final result in _results)
            ListTile(title: Text(result.stage), subtitle: Text(result.detail), trailing: Text(result.result)),
          const SizedBox(height: 16),
          Text(t.client.reportHint),
          OutlinedButton.icon(
            onPressed: _running || _results.isEmpty
                ? null
                : () async {
                    final report = _report();
                    await showDialog<void>(
                      context: context,
                      builder: (context) => AlertDialog(
                        title: Text(t.client.reportTitle),
                        content: SizedBox(width: 500, child: SingleChildScrollView(child: SelectableText(report))),
                        actions: [
                          TextButton(onPressed: () => Navigator.pop(context), child: Text(t.client.cancel)),
                          FilledButton(
                            onPressed: () => SharePlus.instance.share(
                              ShareParams(
                                text: report,
                                title: t.client.reportTitle,
                                sharePositionOrigin: const Rect.fromLTWH(0, 0, 1, 1),
                              ),
                            ),
                            child: Text(t.client.shareReport),
                          ),
                        ],
                      ),
                    );
                  },
            icon: const Icon(Icons.ios_share_rounded),
            label: Text(t.client.shareReport),
          ),
        ],
      ),
    );
  }
}
