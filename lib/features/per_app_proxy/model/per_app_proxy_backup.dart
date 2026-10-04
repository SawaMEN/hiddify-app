import 'dart:convert';

import 'package:freezed_annotation/freezed_annotation.dart';

part 'per_app_proxy_backup.freezed.dart';
part 'per_app_proxy_backup.g.dart';

@freezed
abstract class PerAppProxyBackup with _$PerAppProxyBackup {
  const PerAppProxyBackup._();

  factory PerAppProxyBackup.fromBackupText(String text) {
    var decoded = jsonDecode(text);
    // Accept legacy double-encoded exports as well as ordinary JSON documents.
    if (decoded is String) decoded = jsonDecode(decoded);
    if (decoded is! Map<String, dynamic>) throw const FormatException('Backup must be a JSON object');
    return PerAppProxyBackup.fromJson(decoded);
  }

  String toBackupText() => const JsonEncoder.withIndent('  ').convert(toJson());

  const factory PerAppProxyBackup({
    @Default(PerAppProxyBackupMode()) PerAppProxyBackupMode include,
    @Default(PerAppProxyBackupMode()) PerAppProxyBackupMode exclude,
  }) = _PerAppProxyBackup;

  factory PerAppProxyBackup.fromJson(Map<String, Object?> json) => _$PerAppProxyBackupFromJson(json);
}

@freezed
abstract class PerAppProxyBackupMode with _$PerAppProxyBackupMode {
  const factory PerAppProxyBackupMode({@Default([]) List<String> selected, @Default([]) List<String> deselected}) =
      _PerAppProxyBackupMode;

  factory PerAppProxyBackupMode.fromJson(Map<String, Object?> json) => _$PerAppProxyBackupModeFromJson(json);
}
