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
import 'package:hiddify/utils/platform_utils.dart';
import 'package:hiddify/features/vpn_privacy/vpn_privacy_preferences.dart';
import 'package:json_path/json_path.dart';
import 'package:hiddify/core/utils/preferences_utils.dart';
import 'package:hiddify/features/route_rules/notifier/rules_notifier.dart';
import 'package:hiddify/hiddifycore/generated/v2/config/route_rule.pb.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'config_option_notifier.g.dart';

@Riverpod(keepAlive: true)
class ConfigOptionNotifier extends _$ConfigOptionNotifier with AppLogger {
  @override
  Future<bool> build() async {
    ref.onDispose(() => _updateTimer?.cancel());
    ref.listen(ConfigOptions.singboxConfigOptions, (previous, next) {
      if (previous == null || next == previous) return;
      _desiredRevision++;
      _scheduleUpdate();
    });
    ref.listen(serviceRunningProvider, (_, running) {
      if (running && _desiredRevision > _appliedRevision) _scheduleUpdate();
    });
    for (final preference in [
      VpnPrivacyPreferences.useRoot,
      VpnPrivacyPreferences.fullTunnel,
      VpnPrivacyPreferences.hideLocalProxy,
      VpnPrivacyPreferences.hideClashApi,
      VpnPrivacyPreferences.disableSystemProxy,
      VpnPrivacyPreferences.publicDns,
      VpnPrivacyPreferences.encryptedDns,
    ]) {
      ref.listen(preference, (previous, next) {
        if (previous == next) return;
        _nativePolicyChanged = true;
        _desiredRevision++;
        _scheduleUpdate();
      });
    }
    for (final preference in [
      VpnPrivacyPreferences.routingMode,
      VpnPrivacyPreferences.customDirectPackages,
      VpnPrivacyPreferences.customProxyPackages,
      VpnPrivacyPreferences.customDirectDomains,
      VpnPrivacyPreferences.customProxyDomains,
    ]) {
      ref.listen(preference, (previous, next) {
        if (previous == next) return;
        _nativePolicyChanged = true;
        _desiredRevision++;
        _scheduleUpdate();
      });
    }
    ref.listen(ConfigOptions.region, (previous, next) {
      if (previous == next) return;
      _nativePolicyChanged = true;
    });
    return false;
  }

  Timer? _updateTimer;
  int _desiredRevision = 0;
  int _appliedRevision = 0;
  bool _nativePolicyChanged = false;
  bool _importing = false;
  bool _applying = false;
  Future<void> _updates = Future<void>.value();

  void _scheduleUpdate() {
    if (_importing || _applying) return;
    _updateTimer?.cancel();
    _updateTimer = Timer(const Duration(milliseconds: 300), () {
      _updates = _updates.then((_) => _applyOptions()).catchError((Object error, StackTrace stackTrace) {
        loggy.warning('Unable to apply changed options', error, stackTrace);
      });
    });
  }

  Future<void> _applyOptions() async {
    if (!ref.mounted || !ref.read(serviceRunningProvider) || _desiredRevision <= _appliedRevision) return;
    final revision = _desiredRevision;
    final repository = ref.read(connectionRepositoryProvider);
    final options = ref.read(ConfigOptions.singboxConfigOptions);
    final snapshot = repository.configOptionsSnapshot;
    final activeProfile = await ref.read(activeProfileProvider.future);
    if (!ref.mounted || !ref.read(serviceRunningProvider)) return;
    final notifier = ref.read(connectionNotifierProvider.notifier);
    _applying = true;
    try {
      final success =
          ((PlatformUtils.isAndroid && (_nativePolicyChanged || snapshot?.ipv6Mode != options.ipv6Mode)) ||
              snapshot?.enableTun != options.enableTun)
          ? await notifier.reconnectService(activeProfile)
          : await notifier.reconnect(activeProfile);
      if (success) {
        _appliedRevision = revision;
        if (_desiredRevision == revision) _nativePolicyChanged = false;
      }
    } finally {
      _applying = false;
    }
    if (ref.mounted && _desiredRevision > revision) _scheduleUpdate();
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

  Future<void> importJson(String input) async {
    final decoded = jsonDecode(input);
    if (decoded is! Map<String, dynamic>) throw const FormatException('Settings must be a JSON object');
    final updates = <(PreferencesNotifier, dynamic, dynamic)>[];
    // Validate every supplied value before writing any of them.
    for (final option in ConfigOptions.preferences.entries) {
      final query = option.key.split('.').map((e) => '["$e"]').join();
      final res = JsonPath('\$$query').read(decoded).firstOrNull;
      if (res == null) continue;
      final notifier = ref.read(option.value.notifier);
      notifier.entry.parseRaw(res.value);
      updates.add((notifier, res.value, notifier.raw()));
    }
    final routeValue = decoded['route-rule'];
    final routes = routeValue == null ? null : (RouteRule()..mergeFromProto3Json(routeValue));
    if (updates.isEmpty && routes == null) throw const FormatException('No supported settings');
    final previousRules = routes == null ? null : ref.read(rulesNotifierProvider).map((r) => r.deepCopy()).toList();
    _importing = true;
    _updateTimer?.cancel();
    final applied = <(PreferencesNotifier, dynamic)>[];
    try {
      for (final (notifier, raw, previous) in updates) {
        if (!ref.mounted) throw StateError('Settings import disposed');
        await notifier.updateRaw(raw);
        applied.add((notifier, previous));
      }
      if (routes != null) await ref.read(rulesNotifierProvider.notifier).replaceRules(routes.rules);
    } catch (_) {
      for (final (notifier, previous) in applied.reversed) {
        await notifier.updateRaw(previous);
      }
      if (previousRules != null && ref.mounted) {
        await ref.read(rulesNotifierProvider.notifier).replaceRules(previousRules);
      }
      rethrow;
    } finally {
      _importing = false;
      if (ref.mounted) _scheduleUpdate();
    }
  }

  Future<bool> importFromClipboard() async {
    final t = ref.read(translationsProvider).requireValue;
    try {
      final input = await Clipboard.getData(Clipboard.kTextPlain).then((value) => value?.text);
      if (input == null) return false;
      await importJson(input);
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
      await importJson(utf8.decode(bytes));
      ref.read(inAppNotificationControllerProvider).showSuccessToast(t.common.msg.import.success);
      return true;
    } catch (e, st) {
      loggy.warning("error importing config options from json file", e, st);
      ref.read(inAppNotificationControllerProvider).showErrorToast(t.common.msg.import.failure);
      return false;
    }
  }

  Future<void> resetOption() async {
    _importing = true;
    _updateTimer?.cancel();
    try {
      for (final option in ConfigOptions.preferences.values) {
        await ref.read(option.notifier).reset();
      }
    } finally {
      _importing = false;
      if (ref.mounted) _scheduleUpdate();
    }
  }
}
