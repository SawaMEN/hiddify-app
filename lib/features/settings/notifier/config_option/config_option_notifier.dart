import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/notification/in_app_notification_controller.dart';
import 'package:hiddify/features/connection/data/connection_data_providers.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/utils/custom_loggers.dart';
import 'package:json_path/json_path.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'config_option_notifier.g.dart';

@Riverpod(keepAlive: true)
class ConfigOptionNotifier extends _$ConfigOptionNotifier with AppLogger {
  @override
  Future<bool> build() async {
    ref.onDispose(() => _updateTimer?.cancel());
    ref.listen(ConfigOptions.singboxConfigOptions, (previous, next) {
      if (previous == null || next == previous) return;
      _updateTimer?.cancel();
      _updateTimer = Timer(const Duration(milliseconds: 300), () {
        unawaited(
          _applyOptions().catchError((Object error, StackTrace stackTrace) {
            loggy.warning('Unable to apply changed options', error, stackTrace);
          }),
        );
      });
    });
    return false;
  }

  Timer? _updateTimer;

  Future<void> _applyOptions() async {
    if (!ref.mounted || !ref.read(serviceRunningProvider)) return;
    final repository = ref.read(connectionRepositoryProvider);
    final options = ref.read(ConfigOptions.singboxConfigOptions);
    final snapshot = repository.configOptionsSnapshot;
    if (snapshot == options) return;
    final activeProfile = await ref.read(activeProfileProvider.future);
    if (!ref.mounted) return;
    final notifier = ref.read(connectionNotifierProvider.notifier);
    if (snapshot?.enableTun != options.enableTun) {
      await notifier.reconnectService(activeProfile);
    } else {
      await notifier.reconnect(activeProfile);
    }
  }

  Future<String?> _exportJson(bool excludePrivate) async {
    try {
      final options = ref.read(ConfigOptions.singboxConfigOptions);
      Map map = options.toJson();
      if (excludePrivate) {
        for (final key in ConfigOptions.privatePreferencesKeys) {
          final query = key.split('.').map((e) => '["$e"]').join();
          final res = JsonPath('\$$query').read(map).firstOrNull;
          if (res != null) {
            map = res.pointer.remove(map)! as Map;
          }
        }
      }
      const encoder = JsonEncoder.withIndent('  ');
      return encoder.convert(map);
    } catch (e, st) {
      loggy.warning("error creating config options json", e, st);
      return null;
    }
  }

  Future<bool> exportJsonClipboard({bool excludePrivate = true}) async {
    final t = ref.read(translationsProvider).requireValue;
    try {
      final json = await _exportJson(excludePrivate);
      if (json == null) return false;
      await Clipboard.setData(ClipboardData(text: json));
      ref.read(inAppNotificationControllerProvider).showSuccessToast(t.common.msg.export.clipboard.success);
      return true;
    } on PlatformException {
      ref
          .read(inAppNotificationControllerProvider)
          .showInfoToast(t.common.msg.export.clipboard.contentTooLarge, duration: const Duration(seconds: 5));
      return false;
    } catch (e, st) {
      loggy.warning("error exporting config options to clipboard", e, st);
      ref.read(inAppNotificationControllerProvider).showErrorToast(t.common.msg.export.clipboard.failure);
      return false;
    }
  }

  Future<bool> exportJsonFile({bool excludePrivate = true}) async {
    final t = ref.read(translationsProvider).requireValue;
    try {
      final json = await _exportJson(excludePrivate);
      if (json == null) return false;
      final bytes = Uint8List.fromList(utf8.encode(json));
      final outputFile = await FilePicker.saveFile(
        fileName: 'options.json',
        type: FileType.custom,
        allowedExtensions: ['json'],
        bytes: bytes,
      );
      if (outputFile == null) return false;
      ref.read(inAppNotificationControllerProvider).showSuccessToast(t.common.msg.export.file.success);
      return true;
    } catch (e, st) {
      loggy.warning("error exporting config options to json file", e, st);
      ref.read(inAppNotificationControllerProvider).showErrorToast(t.common.msg.export.file.failure);
      return false;
    }
  }

  Future<void> _importJson(String input) async {
    if (jsonDecode(input) case final Map<String, dynamic> map) {
      for (final option in ConfigOptions.preferences.entries) {
        final query = option.key.split('.').map((e) => '["$e"]').join();
        final res = JsonPath('\$$query').read(map).firstOrNull;
        if (res?.value case final value?) {
          try {
            await ref.read(option.value.notifier).updateRaw(value);
          } catch (e) {
            loggy.debug("error updating [${option.key}]: $e", e);
          }
        }
      }
    }
  }

  Future<bool> importFromClipboard() async {
    final t = ref.read(translationsProvider).requireValue;
    try {
      final input = await Clipboard.getData(Clipboard.kTextPlain).then((value) => value?.text);
      if (input == null) return false;
      await _importJson(input);
      ref.read(inAppNotificationControllerProvider).showSuccessToast(t.common.msg.import.success);
      return true;
    } catch (e, st) {
      loggy.warning("error importing config options from clipboard", e, st);
      ref.read(inAppNotificationControllerProvider).showErrorToast(t.common.msg.import.failure);
      return false;
    }
  }

  Future<bool> importFromJsonFile() async {
    final t = ref.read(translationsProvider).requireValue;
    try {
      final file = await FilePicker.pickFile(type: FileType.custom, allowedExtensions: ['json']);
      if (file == null) return false;
      final bytes = await file.readAsBytes();
      await _importJson(utf8.decode(bytes));
      ref.read(inAppNotificationControllerProvider).showSuccessToast(t.common.msg.import.success);
      return true;
    } catch (e, st) {
      loggy.warning("error importing config options from json file", e, st);
      ref.read(inAppNotificationControllerProvider).showErrorToast(t.common.msg.import.failure);
      return false;
    }
  }

  Future<void> resetOption() async {
    for (final option in ConfigOptions.preferences.values) {
      await ref.read(option.notifier).reset();
    }
    ref.invalidateSelf();
  }
}
