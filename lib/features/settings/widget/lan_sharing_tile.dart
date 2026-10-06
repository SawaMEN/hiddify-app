import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:gap/gap.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/notification/in_app_notification_controller.dart';
import 'package:hiddify/core/router/dialog/dialog_notifier.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service_provider.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';

class LanSharingPreferenceWidget extends HookConsumerWidget {
  const LanSharingPreferenceWidget({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider).requireValue;
    final theme = Theme.of(context);
    final sharing = ref.watch(VpnPrivacyPreferences.wifiSharing);

    Future<String?> getSharingLink() async {
      final ipResult = await ref.read(hiddifyCoreServiceProvider).getLANIP().run();
      final ip = ipResult.fold((_) => null, (r) => r.ip);
      if (ip == null) {
        ref.read(inAppNotificationControllerProvider).showErrorToast(t.pages.settings.inbound.lanIPError);
        return null;
      }
      final port = ref.read(ConfigOptions.mixedPort);
      final password = ref.read(ConfigOptions.lanSharingPassword);
      return Uri(
        scheme: 'socks',
        host: ip,
        port: port,
        userInfo: password.isEmpty ? '' : 'hiddify:${Uri.encodeComponent(password)}',
      ).toString();
    }

    return ListTile(
      leading: const Icon(Icons.share_rounded),
      title: Text(t.pages.settings.inbound.lanSharing),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            ref.watch(ConfigOptions.lanSharingPassword).isEmpty
                ? t.pages.settings.inbound.lanSharingPasswordNotSet
                : ref.watch(ConfigOptions.lanSharingPassword),
          ),
          if (sharing ||
              (ref.watch(ConfigOptions.allowConnectionFromLan) && ref.watch(ConfigOptions.enableMixedPort))) ...[
            const Gap(12),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                ElevatedButton.icon(
                  onPressed: () async {
                    final link = await getSharingLink();
                    if (link != null) {
                      await Clipboard.setData(ClipboardData(text: link));
                      ref
                          .read(inAppNotificationControllerProvider)
                          .showSuccessToast(t.common.msg.export.clipboard.success);
                    }
                  },
                  icon: Icon(Icons.link_rounded, color: theme.colorScheme.primary),
                  label: Text(
                    t.pages.settings.inbound.copyLink,
                    style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.primary),
                  ),
                ),
                const Gap(10),
                ElevatedButton.icon(
                  onPressed: () async {
                    final link = await getSharingLink();
                    if (link != null) {
                      final qrLink = '#profile-title: LAN only\n$link#LAN only';
                      await ref.read(dialogNotifierProvider.notifier).showQrCode(qrLink, message: link);
                    }
                  },
                  icon: Icon(Icons.qr_code_rounded, color: theme.colorScheme.primary),
                  label: Text(
                    t.pages.settings.inbound.qrCode,
                    style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.primary),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
      trailing: Switch.adaptive(
        value: sharing || ref.watch(ConfigOptions.allowConnectionFromLan),
        onChanged: sharing ? null : ref.read(ConfigOptions.allowConnectionFromLan.notifier).update,
      ),
      onTap: () async {
        final inputValue = await ref
            .read(dialogNotifierProvider.notifier)
            .showSettingInput(
              title: t.pages.settings.inbound.lanSharingPassword,
              initialValue: ref.read(ConfigOptions.lanSharingPassword),
              onReset: ref.read(ConfigOptions.lanSharingPassword.notifier).reset,
            );
        if (inputValue != null) {
          await ref.read(ConfigOptions.lanSharingPassword.notifier).update(inputValue);
        }
      },
    );
  }
}
