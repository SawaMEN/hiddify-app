import 'dart:io';

import 'package:material_ui/material_ui.dart';
import 'package:flutter_hooks/flutter_hooks.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/features/per_app_proxy/model/per_app_proxy_mode.dart';
import 'package:hiddify/features/route_rules/notifier/rules_notifier.dart';
import 'package:hiddify/features/route_rules/inspection/route_inspector.dart';
import 'package:hiddify/hiddifycore/generated/v2/config/route_rule.pb.dart';

class RouteInspectorPage extends HookConsumerWidget {
  const RouteInspectorPage({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider).requireValue;
    final host = useTextEditingController(), package = useTextEditingController(), port = useTextEditingController();
    final network = useState(Network.all), result = useState<String?>(null), error = useState<String?>(null);
    final rules = ref.watch(rulesNotifierProvider);
    final mode = ref.watch(Preferences.perAppProxyMode);
    final include = ref.watch(Preferences.includeApps), exclude = ref.watch(Preferences.excludeApps);
    useEffect(() {
      result.value = null;
      return null;
    }, [rules, mode, include, exclude]);
    void inspect() {
      final domain = RouteQuery.normalize(host.text);
      final p = port.text.trim().isEmpty ? null : int.tryParse(port.text.trim());
      if (domain == null || port.text.trim().isNotEmpty && (p == null || p < 1 || p > 65535)) {
        error.value = t.client.invalidDomain;
        return;
      }
      error.value = null;
      final pkg = package.text.trim();
      if (Platform.isAndroid && mode.enabled) {
        if (pkg.isEmpty) {
          result.value = t.client.routeUnknown;
          return;
        }
        if (mode == PerAppProxyMode.include && !include.contains(pkg) ||
            mode == PerAppProxyMode.exclude && exclude.contains(pkg)) {
          result.value = t.client.outsideVpn;
          return;
        }
      }
      final decision = RouteInspector.inspect(rules, RouteQuery(domain, package: pkg, port: p, network: network.value));
      result.value = decision.rule == null
          ? t.client.routeDefault
          : decision.match == MatchResult.unknown
          ? '${decision.rule!.name}: ${t.client.routeUnknown}'
          : '${decision.rule!.name} → ${decision.rule!.outbound.name}';
    }

    return Scaffold(
      appBar: AppBar(title: Text(t.client.inspectRoute)),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          Text(t.client.routeHint),
          const SizedBox(height: 20),
          TextField(
            controller: host,
            decoration: InputDecoration(labelText: t.client.routeDomain, errorText: error.value),
            onChanged: (_) => result.value = null,
          ),
          TextField(
            controller: package,
            decoration: InputDecoration(labelText: t.client.routePackage),
            onChanged: (_) => result.value = null,
          ),
          TextField(
            controller: port,
            keyboardType: TextInputType.number,
            decoration: InputDecoration(labelText: t.client.routePort),
            onChanged: (_) => result.value = null,
          ),
          DropdownButtonFormField<Network>(
            initialValue: network.value,
            items: Network.values.map((v) => DropdownMenuItem(value: v, child: Text(v.name))).toList(),
            onChanged: (v) {
              network.value = v ?? Network.all;
              result.value = null;
            },
          ),
          const SizedBox(height: 20),
          FilledButton(onPressed: inspect, child: Text(t.client.inspectRoute)),
          if (result.value != null)
            Padding(
              padding: const EdgeInsets.only(top: 20),
              child: Semantics(liveRegion: true, child: Text(result.value!)),
            ),
        ],
      ),
    );
  }
}
