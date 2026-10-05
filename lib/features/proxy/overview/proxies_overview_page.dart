import 'dart:math';

import 'package:flutter_hooks/flutter_hooks.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';

import 'package:fluentui_system_icons/fluentui_system_icons.dart';
import 'package:material_ui/material_ui.dart';
import 'package:gap/gap.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/model/failures.dart';
import 'package:hiddify/features/proxy/overview/proxies_overview_notifier.dart';
import 'package:hiddify/features/proxy/widget/proxy_tile.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

class ProxiesOverviewPage extends HookConsumerWidget with PresLogger {
  const ProxiesOverviewPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider).requireValue;
    final query = useState("");

    final proxies = ref.watch(proxiesOverviewNotifierProvider);
    final sortBy = ref.watch(proxiesSortNotifierProvider);

    // final selectActiveProxyMutation = useMutation(
    //   initialOnFailure: (error) => CustomToast.error(t.presentShortError(error)).show(context),
    // );

    return Scaffold(
      appBar: AppBar(
        title: Text(t.pages.proxies.title),
        actions: [
          PopupMenuButton<ProxiesSort>(
            initialValue: sortBy,
            onSelected: ref.read(proxiesSortNotifierProvider.notifier).update,
            icon: const Icon(FluentIcons.arrow_sort_24_regular),
            tooltip: t.pages.proxies.sort,
            itemBuilder: (context) {
              return [...ProxiesSort.values.map((e) => PopupMenuItem(value: e, child: Text(e.present(t))))];
            },
          ),
          const Gap(8),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () async {
          final tag = proxies.value?.tag;
          if (tag == null) return;
          try {
            await ref.read(proxiesOverviewNotifierProvider.notifier).urlTest(tag);
          } catch (e) {
            if (context.mounted)
              ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(t.presentShortError(e))));
          }
        },
        tooltip: t.pages.proxies.testDelay,
        child: const Icon(FluentIcons.flash_24_filled),
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
            child: TextField(
              decoration: InputDecoration(
                labelText: t.client.searchServers,
                prefixIcon: const Icon(Icons.search_rounded),
              ),
              onChanged: (value) => query.value = value.toLowerCase(),
            ),
          ),
          SwitchListTile.adaptive(
            title: Text(t.client.smartSelection),
            subtitle: Text(t.client.smartSelectionHint),
            value: ref.watch(Preferences.smartServerSelection),
            onChanged: ref.read(Preferences.smartServerSelection.notifier).update,
          ),
          Expanded(
            child: proxies.when(
              data: (group) => group != null
                  ? LayoutBuilder(
                      builder: (context, constraints) {
                        final items = group.items
                            .where(
                              (p) =>
                                  p.tag.toLowerCase().contains(query.value) ||
                                  p.type.toLowerCase().contains(query.value),
                            )
                            .toList();
                        final width = constraints.maxWidth;
                        final crossAxisCount = PlatformUtils.isMobile && width < 600
                            ? 1
                            : max(1, (width / 268).floor());
                        return GridView.builder(
                          padding: const EdgeInsets.fromLTRB(20, 12, 20, 100),
                          itemCount: items.length,
                          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
                            crossAxisCount: crossAxisCount,
                            mainAxisExtent: 80 * MediaQuery.textScalerOf(context).scale(1).clamp(1, 2),
                            mainAxisSpacing: 12,
                            crossAxisSpacing: 12,
                          ),
                          itemBuilder: (context, index) {
                            final proxy = items[index];
                            return ProxyTile(
                              proxy,
                              selected: group.selected == proxy.tag,
                              onTap: () async {
                                try {
                                  await ref
                                      .read(proxiesOverviewNotifierProvider.notifier)
                                      .changeProxy(group.tag, proxy.tag);
                                } catch (e) {
                                  if (context.mounted)
                                    ScaffoldMessenger.of(context)
                                        .showSnackBar(SnackBar(content: Text(t.presentShortError(e))));
                                }
                                // if (selectActiveProxyMutation.state.isInProgress) return;
                                // selectActiveProxyMutation.setFuture(
                                // );
                              },
                            );
                          },
                        );
                      },
                    )
                  : Center(child: Text(t.pages.proxies.empty)),
              error: (error, stackTrace) => Center(child: Text(t.presentShortError(error))),
              loading: () => const Center(child: CircularProgressIndicator()),
            ),
          ),
        ],
      ),
    );
  }
}
