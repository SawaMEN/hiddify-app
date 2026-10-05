import 'package:material_ui/material_ui.dart';
import 'package:hiddify/features/connection/health/android_vpn_settings.dart';
import 'package:gap/gap.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/router/bottom_sheets/bottom_sheets_notifier.dart';
import 'package:hiddify/core/router/dialog/dialog_notifier.dart';
import 'package:hiddify/core/theme/theme_extensions.dart';
import 'package:hiddify/core/theme/visual_effects.dart';
import 'package:hiddify/core/widget/glass_surface.dart';
import 'package:hiddify/core/widget/animated_text.dart';
import 'package:hiddify/features/connection/model/connection_status.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/features/proxy/active/active_proxy_notifier.dart';
import 'package:hiddify/features/settings/notifier/config_option/config_option_notifier.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

class ConnectionButton extends HookConsumerWidget {
  const ConnectionButton({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final t = ref.watch(translationsProvider).requireValue;
    final connectionStatus = ref.watch(connectionNotifierProvider);
    final delay = ref.watch(activeProxyNotifierProvider.select((value) => value.value?.urlTestDelay ?? 0));
    final requiresReconnect = ref.watch(configOptionNotifierProvider).value;
    final scheme = Theme.of(context).colorScheme;
    final buttonTheme =
        Theme.of(context).extension<ConnectionButtonTheme>() ??
        (Theme.of(context).brightness == Brightness.dark ? ConnectionButtonTheme.dark : ConnectionButtonTheme.light);

    final buttonColor = switch (connectionStatus) {
      AsyncData(value: Connected()) when requiresReconnect == true => scheme.secondary,
      AsyncData(value: Connected()) when delay <= 0 || delay >= 65000 => const Color(0xFFFFC857),
      AsyncData(value: Connected()) => buttonTheme.connectedColor!,
      AsyncData(value: _) => buttonTheme.idleColor!,
      AsyncError() => scheme.error,
      _ => scheme.primary,
    };

    return _ConnectionButton(
      onTap: switch (connectionStatus) {
        AsyncData(value: Connected()) when requiresReconnect == true => () async {
          final activeProfile = await ref.read(activeProfileProvider.future);
          await ref.read(connectionNotifierProvider.notifier).reconnect(activeProfile);
        },
        AsyncData(value: Disconnected()) || AsyncError() => () async {
          if (ref.read(activeProfileProvider).value == null) {
            await ref.read(dialogNotifierProvider.notifier).showNoActiveProfile();
            ref.read(bottomSheetsNotifierProvider.notifier).showAddProfile();
            return;
          }
          if (await ref.read(dialogNotifierProvider.notifier).showExperimentalFeatureNotice()) {
            return await ref.read(connectionNotifierProvider.notifier).toggleConnection();
          }
        },
        AsyncData(value: Connected()) => () async {
          try {
            final protection = await AndroidVpnSettings.protection();
            if (protection['alwaysOn'] == true) {
              await AndroidVpnSettings.open();
              return;
            }
          } catch (_) {}
          if (requiresReconnect == true &&
              await ref.read(dialogNotifierProvider.notifier).showExperimentalFeatureNotice()) {
            await ref.read(connectionNotifierProvider.notifier).reconnect(await ref.read(activeProfileProvider.future));
          }
          return await ref.read(connectionNotifierProvider.notifier).toggleConnection();
        },
        _ => null,
      },
      enabled: switch (connectionStatus) {
        AsyncData(value: Connected()) || AsyncData(value: Disconnected()) || AsyncError() => true,
        _ => false,
      },
      label: switch (connectionStatus) {
        AsyncData(value: Connected()) when requiresReconnect == true => t.connection.reconnect,
        AsyncData(value: final status) => status.present(t),
        _ => t.connection.connecting,
      },
      buttonColor: buttonColor,
    );
  }
}

class _ConnectionButton extends StatelessWidget {
  const _ConnectionButton({
    required this.onTap,
    required this.enabled,
    required this.label,
    required this.buttonColor,
  });

  final VoidCallback? onTap;
  final bool enabled;
  final String label;
  final Color buttonColor;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final duration = VisualEffects.durationOf(context);
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Semantics(
          button: true,
          enabled: enabled,
          label: label,
          child: AnimatedScale(
            duration: duration,
            scale: enabled ? 1 : .94,
            child: SizedBox.square(
              dimension: 176,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  ExcludeSemantics(
                    child: TweenAnimationBuilder<Color?>(
                      tween: ColorTween(end: buttonColor),
                      duration: duration,
                      builder: (context, color, child) => DecoratedBox(
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(color: color!.withValues(alpha: .28), width: 2),
                          gradient: RadialGradient(colors: [color.withValues(alpha: .12), color.withValues(alpha: 0)]),
                        ),
                      ),
                    ),
                  ),
                  if (!enabled) const ExcludeSemantics(child: CircularProgressIndicator(strokeWidth: 2)),
                  Padding(
                    padding: const EdgeInsets.all(14),
                    child: GlassSurface(
                      radius: 100,
                      blur: true,
                      accent: buttonColor,
                      child: InkWell(
                        key: const ValueKey('home_connection_button'),
                        customBorder: const CircleBorder(),
                        onTap: enabled ? onTap : null,
                        child: Center(
                          child: Icon(Icons.power_settings_new_rounded, size: 64, color: buttonColor),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        const Gap(24),
        ExcludeSemantics(
          child: AnimatedText(label, style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600)),
        ),
      ],
    );
  }
}
