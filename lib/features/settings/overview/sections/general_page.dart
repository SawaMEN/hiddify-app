import 'package:dartx/dartx.dart';
import 'package:flutter/services.dart';
import 'package:hiddify/hiddifycore/core_interface/core_interface_mobile.dart';
import 'package:material_ui/material_ui.dart';
import 'package:hiddify/core/haptic/haptic_service.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/core/router/dialog/dialog_notifier.dart';
import 'package:hiddify/features/common/general_pref_tiles.dart';
import 'package:hiddify/features/log/model/log_level.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/features/settings/widget/preference_tile.dart';
import 'package:hiddify/features/vpn_privacy/network_anonymization_page.dart';
import 'package:hiddify/singbox/model/singbox_config_enum.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:humanizer/humanizer.dart';

class GeneralPage extends HookConsumerWidget {
  const GeneralPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider).requireValue;
    final isRussian = Localizations.localeOf(context).languageCode == 'ru';

    return Scaffold(
      appBar: AppBar(title: Text(t.pages.settings.general.title)),
      body: ListView(
        children: [
          const LocalePrefTile(),
          const ThemeModePrefTile(),
          SwitchListTile.adaptive(
            title: Text(t.client.autoReconnect),
            subtitle: Text(t.client.autoReconnectHint),
            value: ref.watch(Preferences.autoReconnect),
            onChanged: ref.read(Preferences.autoReconnect.notifier).update,
          ),
          SwitchListTile.adaptive(
            title: Text(t.client.smartSelection),
            subtitle: Text(t.client.smartSelectionHint),
            value: ref.watch(Preferences.smartServerSelection),
            onChanged: ref.read(Preferences.smartServerSelection.notifier).update,
          ),
          ChoicePreferenceWidget(
            title: t.pages.settings.routing.generalOptions.balancerStrategy.title,
            icon: Icons.balance_rounded,
            selected: ref.watch(ConfigOptions.balancerStrategy),
            preferences: ref.watch(ConfigOptions.balancerStrategy.notifier),
            choices: BalancerStrategy.values,
            presentChoice: (value) => value.present(t),
          ),
          SwitchListTile.adaptive(
            title: Text(t.pages.settings.routing.generalOptions.resolveDestination),
            secondary: const Icon(Icons.find_replace_rounded),
            value: ref.watch(ConfigOptions.resolveDestination),
            onChanged: ref.read(ConfigOptions.resolveDestination.notifier).update,
          ),
          SwitchListTile.adaptive(
            title: Text(t.pages.settings.general.autoIpCheck),
            value: ref.watch(Preferences.autoCheckIp),
            secondary: const Icon(Icons.flag_rounded),
            onChanged: ref.read(Preferences.autoCheckIp.notifier).update,
          ),
          if (PlatformUtils.isAndroid)
            ListTile(
              title: Text(t.client.vpnNotification),
              subtitle: Text(t.client.vpnNotificationHint),
              leading: const Icon(Icons.notifications_rounded),
              trailing: const Icon(Icons.open_in_new_rounded),
              onTap: () async {
                try {
                  await CoreInterfaceMobile.methodChannel.invokeMethod<void>('open_notification_settings');
                } on PlatformException catch (error) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message ?? error.code)));
                  }
                }
              },
            ),
          SwitchListTile.adaptive(
            title: Text(t.pages.settings.general.dynamicNotification),
            secondary: const Icon(Icons.speed_rounded),
            value: ref.watch(Preferences.dynamicNotification),
            onChanged: (value) async {
              await ref.read(Preferences.dynamicNotification.notifier).update(value);
              if (PlatformUtils.isAndroid) {
                try {
                  await CoreInterfaceMobile.methodChannel.invokeMethod<void>('refresh_notification');
                } on PlatformException catch (error) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(error.message ?? error.code)));
                  }
                }
              }
            },
          ),
          SwitchListTile.adaptive(
            title: Text(t.pages.settings.general.hapticFeedback),
            secondary: const Icon(Icons.vibration_rounded),
            value: ref.watch(hapticServiceProvider),
            onChanged: ref.read(hapticServiceProvider.notifier).updatePreference,
          ),
          const BatteryOptimizationWidget(),
          if (PlatformUtils.isAndroid)
            ListTile(
              leading: const Icon(Icons.privacy_tip_outlined),
              title: Text(isRussian ? 'Анонимизация в сети' : 'Network anonymization'),
              subtitle: Text(
                isRussian
                    ? 'Экспериментально · не рекомендуется к применению'
                    : 'Experimental · not recommended for normal use',
              ),
              trailing: const Icon(Icons.chevron_right_rounded),
              onTap: () => Navigator.of(context).push(
                MaterialPageRoute<void>(builder: (_) => const NetworkAnonymizationPage()),
              ),
            ),
          SwitchListTile.adaptive(
            title: Text(t.pages.settings.general.memoryLimit),
            subtitle: Text(t.pages.settings.general.memoryLimitMsg),
            secondary: const Icon(Icons.memory_rounded),
            value: !ref.watch(Preferences.disableMemoryLimit),
            onChanged: (value) async => await ref.read(Preferences.disableMemoryLimit.notifier).update(!value),
          ),
          SwitchListTile.adaptive(
            title: Text(t.pages.settings.general.debugMode),
            secondary: const Icon(Icons.bug_report_rounded),
            value: ref.watch(debugModeNotifierProvider),
            onChanged: (value) async {
              if (value) {
                await ref
                    .read(dialogNotifierProvider.notifier)
                    .showOk(t.pages.settings.general.debugMode, t.pages.settings.general.debugModeMsg);
              }
              await ref.read(debugModeNotifierProvider.notifier).update(value);
            },
          ),
          ChoicePreferenceWidget(
            selected: ref.watch(ConfigOptions.logLevel),
            preferences: ref.watch(ConfigOptions.logLevel.notifier),
            choices: LogLevel.choices,
            title: t.pages.settings.general.logLevel,
            icon: Icons.description_rounded,
            presentChoice: (value) => value.name.toUpperCase(),
          ),
          ValuePreferenceWidget(
            value: ref.watch(ConfigOptions.connectionTestUrl),
            preferences: ref.watch(ConfigOptions.connectionTestUrl.notifier),
            title: t.pages.settings.general.connectionTestUrl,
            icon: Icons.link_rounded,
          ),
          ListTile(
            title: Text(t.pages.settings.general.urlTestInterval),
            subtitle: Text(ref.watch(ConfigOptions.urlTestInterval).toApproximateTime(isRelativeToNow: false)),
            leading: const Icon(Icons.timer_rounded),
            onTap: () async => await ref
                .read(dialogNotifierProvider.notifier)
                .showSettingSlider(
                  title: t.pages.settings.general.urlTestInterval,
                  initialValue: ref.watch(ConfigOptions.urlTestInterval).inMinutes.coerceIn(0, 60).toDouble(),
                  onReset: ref.read(ConfigOptions.urlTestInterval.notifier).reset,
                  min: 1,
                  max: 60,
                  divisions: 60,
                  labelGen: (value) => Duration(minutes: value.toInt()).toApproximateTime(isRelativeToNow: false),
                )
                .then((value) async {
                  if (value == null) return;
                  await ref.read(ConfigOptions.urlTestInterval.notifier).update(Duration(minutes: value.toInt()));
                }),
          ),
          ValuePreferenceWidget(
            value: ref.watch(ConfigOptions.clashApiPort),
            preferences: ref.watch(ConfigOptions.clashApiPort.notifier),
            title: t.pages.settings.general.clashApiPort,
            icon: Icons.api_rounded,
            validateInput: isPort,
            digitsOnly: true,
            inputToValue: int.tryParse,
          ),
          SwitchListTile.adaptive(
            title: Text(t.pages.settings.general.useXrayCoreWhenPossible),
            subtitle: Text(t.pages.settings.general.useXrayCoreWhenPossibleMsg),
            secondary: const Icon(Icons.extension_rounded),
            value: ref.watch(ConfigOptions.useXrayCoreWhenPossible),
            onChanged: ref.read(ConfigOptions.useXrayCoreWhenPossible.notifier).update,
          ),
        ],
      ),
    );
  }
}
