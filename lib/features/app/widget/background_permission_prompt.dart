import 'dart:io';

import 'package:flutter/services.dart';
import 'package:hiddify/features/settings/data/battery_optimization_repository.dart';
import 'package:material_ui/material_ui.dart';

/// Android coordinates the prompt with VPN startup and notification permission.
/// The explanation uses the app's theme; only the actual permission is system UI.
class BackgroundPermissionPrompt extends StatefulWidget {
  const BackgroundPermissionPrompt({required this.navigatorKey, required this.languageCode, required this.child, super.key});
  final GlobalKey<NavigatorState> navigatorKey;
  final String languageCode;
  final Widget child;

  @override
  State<BackgroundPermissionPrompt> createState() => _BackgroundPermissionPromptState();
}

class _BackgroundPermissionPromptState extends State<BackgroundPermissionPrompt> {
  static const _channel = MethodChannel('vetroff/background_permissions');
  bool _showing = false;

  @override
  void initState() {
    super.initState();
    if (!Platform.isAndroid) return;
    _channel.setMethodCallHandler(_handle);
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      try {
        await _channel.invokeMethod<void>('ready');
      } on PlatformException {
        // Optional permission education never prevents startup.
      } on MissingPluginException {
        // No Android activity is currently attached.
      }
    });
  }

  Future<bool> _handle(MethodCall call) async {
    if (call.method != 'batteryPrompt' || !mounted || _showing) return false;
    final context = widget.navigatorKey.currentContext;
    if (context == null || !context.mounted) return false;
    _showing = true;
    try {
      final allow = await showBackgroundPermissionExplanation(context, widget.languageCode);
      if (!mounted) return false;
      if (allow) await BatteryOptimizationRepositoryImpl().requestIgnoreBatteryOptimizations();
      return true;
    } finally {
      _showing = false;
    }
  }

  @override
  void dispose() {
    if (Platform.isAndroid) _channel.setMethodCallHandler(null);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}

Future<bool> showBackgroundPermissionExplanation(BuildContext context, String languageCode) async =>
    await showModalBottomSheet<bool>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (context) => BackgroundPermissionSheet(languageCode: languageCode),
    ) ?? false;

class BackgroundPermissionSheet extends StatelessWidget {
  const BackgroundPermissionSheet({required this.languageCode, super.key});
  final String languageCode;

  @override
  Widget build(BuildContext context) {
    final ru = languageCode == 'ru';
    final theme = Theme.of(context);
    return SafeArea(child: SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Align(alignment: Alignment.centerLeft, child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(color: theme.colorScheme.primaryContainer, borderRadius: BorderRadius.circular(20)),
          child: Icon(Icons.battery_charging_full_rounded, size: 32, color: theme.colorScheme.onPrimaryContainer),
        )),
        const SizedBox(height: 20),
        Text(ru ? 'Стабильная работа в фоне' : 'Stay connected in the background', style: theme.textTheme.headlineSmall),
        const SizedBox(height: 12),
        Text(ru
          ? 'Разрешите VetrOFF Client работать без ограничений батареи, чтобы Android не прерывал соединение, когда экран выключен.'
          : 'Allow VetrOFF Client to use the battery without restrictions so Android does not interrupt your connection when the screen is off.',
          style: theme.textTheme.bodyLarge),
        const SizedBox(height: 12),
        Text(ru ? 'Далее откроется системный запрос Android. Решение всегда можно изменить в настройках.'
          : 'Android will ask for permission next. You can change this later in system settings.',
          style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        const SizedBox(height: 24),
        FilledButton.icon(onPressed: () => Navigator.of(context).pop(true),
          icon: const Icon(Icons.battery_charging_full_rounded),
          label: Text(ru ? 'Разрешить работу в фоне' : 'Allow background activity')),
        const SizedBox(height: 8),
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: Text(ru ? 'Позже' : 'Later')),
      ]),
    ));
  }
}
