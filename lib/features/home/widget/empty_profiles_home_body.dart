import 'package:material_ui/material_ui.dart';
import 'package:gap/gap.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/router/bottom_sheets/bottom_sheets_notifier.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

class EmptyProfilesHomeBody extends HookConsumerWidget {
  const EmptyProfilesHomeBody({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider).requireValue;

    return SliverFillRemaining(
      hasScrollBody: false,
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.add_moderator_outlined, size: 72, color: Theme.of(context).colorScheme.primary),
          const Gap(24),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 28),
            child: Text(
              t.dialogs.noActiveProfile.msg,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
          const Gap(16),
          FilledButton.icon(
            onPressed: () => ref.read(bottomSheetsNotifierProvider.notifier).showAddProfile(),
            icon: const Icon(Icons.add_rounded),
            label: Text(t.pages.profiles.add),
          ),
        ],
      ),
    );
  }
}
