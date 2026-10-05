import 'package:dartx/dartx.dart';
import 'package:material_ui/material_ui.dart';
import 'package:hiddify/features/home/widget/home_speed.dart';
import 'package:hiddify/features/connection/health/health_status_widget.dart';
import 'package:hiddify/features/connection/diagnostics/diagnostics_page.dart';
import 'package:gap/gap.dart';
import 'package:hiddify/core/app_info/app_info_provider.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/router/bottom_sheets/bottom_sheets_notifier.dart';
import 'package:hiddify/core/widget/glass_surface.dart';
import 'package:hiddify/features/home/widget/connection_button.dart';
import 'package:hiddify/features/home/widget/empty_profiles_home_body.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/features/profile/widget/profile_tile.dart';
import 'package:hiddify/features/proxy/active/active_proxy_card.dart';
import 'package:hiddify/features/proxy/active/active_proxy_delay_indicator.dart';
import 'package:hiddify/gen/assets.gen.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final t = ref.watch(translationsProvider).requireValue;
    final activeProfile = ref.watch(activeProfileProvider);
    final hasProfiles = ref.watch(hasAnyProfileProvider).value ?? false;

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            Assets.images.logo.image(
              height: 28,
            ),
            const Gap(12),
            Flexible(child: Text(t.common.appTitle, overflow: TextOverflow.ellipsis)),
            const Gap(8),
            const AppVersionLabel(),
          ],
        ),
        actions: [
          IconButton(
            tooltip: t.client.diagnostics,
            icon: const Icon(Icons.health_and_safety_rounded),
            onPressed: () =>
                Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const DiagnosticsPage())),
          ),
          IconButton.filledTonal(
            key: const ValueKey('profile_add_button'),
            tooltip: t.pages.profiles.add,
            icon: const Icon(Icons.add_rounded),
            onPressed: () => ref.read(bottomSheetsNotifierProvider.notifier).showAddProfile(),
          ),
          const Gap(16),
        ],
      ),
      body: SafeArea(
        top: false,
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 680),
            child: CustomScrollView(
              slivers: [
                if (!hasProfiles && !activeProfile.isLoading)
                  const EmptyProfilesHomeBody()
                else ...[
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
                      child: switch (activeProfile) {
                        AsyncData(value: final profile?) => ProfileTile(profile: profile, isMain: true),
                        AsyncError() => Text(t.dialogs.noActiveProfile.msg, textAlign: TextAlign.center),
                        AsyncData() => ListTile(
                          title: Text(t.dialogs.noActiveProfile.msg),
                          trailing: IconButton(
                            tooltip: t.pages.profiles.viewAllProfiles,
                            icon: const Icon(Icons.view_list_rounded),
                            onPressed: () => ref.read(bottomSheetsNotifierProvider.notifier).showProfilesOverview(),
                          ),
                        ),
                        _ => const SizedBox(height: 56, child: Center(child: CircularProgressIndicator())),
                      },
                    ),
                  ),
                  SliverToBoxAdapter(
                    child: Padding(
                      padding: const EdgeInsets.fromLTRB(20, 24, 20, 20),
                      child: Column(
                        children: [
                          const Padding(
                            padding: EdgeInsets.symmetric(vertical: 24),
                            child: Column(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                RepaintBoundary(child: ConnectionButton()),
                                Gap(12),
                                ActiveProxyDelayIndicator(),
                                HealthStatusWidget(),
                              ],
                            ),
                          ),
                          const ActiveProxyFooter(),
                          const Gap(12),
                          const HomeSpeed(),
                          const Gap(16),
                          GlassSurface(
                            blur: true,
                            radius: 20,
                            child: InkWell(
                              onTap: () => ref.read(bottomSheetsNotifierProvider.notifier).showQuickSettings(),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                                child: Row(
                                  children: [
                                    Icon(Icons.tune_rounded, color: theme.colorScheme.primary),
                                    const Gap(12),
                                    Expanded(
                                      child: Text(t.pages.home.quickSettings, style: theme.textTheme.titleSmall),
                                    ),
                                    const Icon(Icons.keyboard_arrow_up_rounded),
                                  ],
                                ),
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class AppVersionLabel extends HookConsumerWidget {
  const AppVersionLabel({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider).requireValue;
    final theme = Theme.of(context);

    final version = ref.watch(appInfoProvider).requireValue.presentVersion;
    if (version.isBlank) return const SizedBox();

    return Semantics(
      label: t.common.version,
      button: false,
      child: Container(
        decoration: BoxDecoration(color: theme.colorScheme.secondaryContainer, borderRadius: BorderRadius.circular(4)),
        padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 1),
        child: Text(
          version,
          textDirection: TextDirection.ltr,
          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSecondaryContainer),
        ),
      ),
    );
  }
}
