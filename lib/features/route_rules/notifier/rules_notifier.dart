import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/services.dart';
import 'package:hiddify/core/directories/directories_provider.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/notification/in_app_notification_controller.dart';
import 'package:hiddify/core/router/dialog/dialog_notifier.dart';
import 'package:hiddify/hiddifycore/generated/v2/config/route_rule.pb.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:hiddify/core/utils/keyed_operations.dart';
import 'package:uuid/uuid.dart';

part 'rules_notifier.g.dart';

@riverpod
class RulesNotifier extends _$RulesNotifier with AppLogger {
  late File file;

  @override
  List<Rule> build() {
    final directories = ref.watch(appDirectoriesProvider).requireValue;
    file = File('${directories.baseDir.path}/route_rule.proto');
    if (file.existsSync()) {
      try {
        return _updateListOrder(RouteRule.fromBuffer(file.readAsBytesSync()).rules.toList());
      } catch (error, stack) {
        // Preserve the corrupt original; saving subsequent edits writes a new file.
        file.copySync('${file.path}.corrupt-${DateTime.now().microsecondsSinceEpoch}');
        loggy.error('Unable to read route rules, preserved recovery copy', error, stack);
        return <Rule>[];
      }
    } else {
      return <Rule>[];
    }
  }

  final _operations = KeyedOperations();

  Future<void> _mutate(void Function(List<Rule>) change) => _operations.run('rules', () async {
    final next = state.map((rule) => rule.deepCopy()).toList();
    change(next);
    await _persist(_updateListOrder(next));
  });

  Future<void> replaceRules(List<Rule> rules) => _operations.run('rules', () async {
    await _persist(_updateListOrder(rules.map((rule) => rule.deepCopy()).toList()));
  });

  Future<void> addRule(Rule rule) => _mutate((current) {
    if (!rule.hasName() || !rule.hasOutbound()) throw const FormatException('Incomplete rule');
    current.add(rule.deepCopy()..enabled = true);
  });

  Future<void> updateRule(Rule rule, {Rule? expected}) => _mutate((current) {
    final index = current.indexWhere((element) => element.listOrder == rule.listOrder);
    if (index == -1 || expected != null && current[index] != expected) {
      throw StateError('Rule changed or was deleted; reopen the editor');
    }
    current[index] = rule.deepCopy();
  });

  Future<void> deleteRule(int listOrder) => _mutate((current) {
    current.removeWhere((rule) => rule.listOrder == listOrder);
  });

  Future<void> reorder(int oldIndex, int newIndex) => _mutate((current) {
    final rule = current.removeAt(oldIndex);
    current.insert(oldIndex < newIndex ? newIndex - 1 : newIndex, rule);
  });

  Future<void> updateEnabled(bool enabled, int listOrder) => _mutate((current) {
    final index = current.indexWhere((rule) => rule.listOrder == listOrder);
    if (index == -1) throw StateError('Rule was deleted');
    current[index].enabled = enabled;
  });

  Future<bool> exportJsonToClipboard() async {
    final t = ref.read(translationsProvider).requireValue;
    try {
      final routeRules = RouteRule(rules: state);
      final base64Data = base64.encode(utf8.encode(jsonEncode(routeRules.writeToJson())));
      await Clipboard.setData(ClipboardData(text: 'hiddify:///settings/routing-options?routeRule=$base64Data'));
      ref.read(inAppNotificationControllerProvider).showSuccessToast(t.common.msg.export.clipboard.success);
      return true;
    } on PlatformException {
      ref
          .read(inAppNotificationControllerProvider)
          .showInfoToast(t.common.msg.export.clipboard.contentTooLarge, duration: const Duration(seconds: 5));
      return false;
    } catch (e, st) {
      loggy.warning("error exporting route rules to clipboard", e, st);
      ref.read(inAppNotificationControllerProvider).showErrorToast(t.common.msg.export.clipboard.failure);
      return false;
    }
  }

  Future<bool> importRulesFromClipboard() async {
    final t = ref.read(translationsProvider).requireValue;
    try {
      final clipboardData = await Clipboard.getData(Clipboard.kTextPlain).then((value) => value?.text);
      if (clipboardData == null) return false;
      final encodedBase64 = Uri.parse(clipboardData).queryParameters['routeRule'];
      if (encodedBase64 == null) return false;
      return await importRules(encodedBase64);
    } catch (e, st) {
      loggy.warning("error importing route rules from clipboard", e, st);
      ref.read(inAppNotificationControllerProvider).showErrorToast(t.common.msg.import.failure);
      return false;
    }
  }

  Future<bool> importRulesFromDeepLink(String encodedBase64) async {
    final t = ref.read(translationsProvider).requireValue;
    try {
      final isConfirmed = await ref
          .read(dialogNotifierProvider.notifier)
          .showConfirmation(
            title: t.dialogs.confirmation.importRouteRuleByDeepLinkWarning.title,
            message: t.dialogs.confirmation.importRouteRuleByDeepLinkWarning.message,
          );
      if (isConfirmed) {
        return await importRules(encodedBase64);
      }
      return false;
    } catch (e, st) {
      loggy.warning("error importing route rules from deep link", e, st);
      ref.read(inAppNotificationControllerProvider).showErrorToast(t.common.msg.import.failure);
      return false;
    }
  }

  Future<bool> importRules(String encodedBase64) async {
    final t = ref.read(translationsProvider).requireValue;
    final base64Content = base64.decode(encodedBase64);
    final routeRules = RouteRule.fromJson(jsonDecode(utf8.decode(base64Content)) as String);
    await replaceRules(routeRules.rules);
    ref.read(inAppNotificationControllerProvider).showSuccessToast(t.common.msg.import.success);
    return true;
  }

  Future<bool> saveRulesAsJsonFile() async {
    final t = ref.read(translationsProvider).requireValue;
    try {
      final bytes = Uint8List.fromList(utf8.encode(RouteRule(rules: state).writeToJson()));
      final outputFile = await FilePicker.saveFile(
        fileName: 'route_rules.json',
        type: FileType.custom,
        allowedExtensions: ['json'],
        bytes: bytes,
      );
      if (outputFile == null) return false;
      ref.read(inAppNotificationControllerProvider).showSuccessToast(t.common.msg.export.file.success);
      return true;
    } catch (e, st) {
      loggy.warning("error exporting route rules to json file", e, st);
      ref.read(inAppNotificationControllerProvider).showErrorToast(t.common.msg.export.file.failure);
      return false;
    }
  }

  Future<bool> importRulesFromJsonFile() async {
    final t = ref.read(translationsProvider).requireValue;
    try {
      final selectedFile = await FilePicker.pickFile(type: FileType.custom, allowedExtensions: ['json']);
      if (selectedFile == null) return false;
      final bytes = await selectedFile.readAsBytes();
      final routeRules = RouteRule.fromJson(utf8.decode(bytes));
      await replaceRules(routeRules.rules);
      ref.read(inAppNotificationControllerProvider).showSuccessToast(t.common.msg.import.success);
      return true;
    } catch (e, st) {
      loggy.warning("error importing route rules from json file", e, st);
      ref.read(inAppNotificationControllerProvider).showErrorToast(t.common.msg.import.failure);
      return false;
    }
  }

  Future<void> resetRules() => replaceRules([]);

  Future<void> _persist(List<Rule> rules) async {
    await file.parent.create(recursive: true);
    final temp = File('${file.path}.${const Uuid().v4()}.tmp');
    try {
      await temp.writeAsBytes(RouteRule(rules: rules).writeToBuffer(), flush: true);
      await temp.rename(file.path);
      if (ref.mounted) state = rules;
    } finally {
      if (await temp.exists()) await temp.delete();
    }
  }

  List<Rule> _updateListOrder(List<Rule> rules) {
    for (var i = 0; i < rules.length; i++) {
      rules[i].listOrder = i;
    }
    return rules;
  }
}
