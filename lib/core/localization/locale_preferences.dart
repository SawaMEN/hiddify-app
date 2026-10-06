import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/gen/translations.g.dart';
import 'package:hiddify/utils/custom_loggers.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

final localePreferencesProvider = NotifierProvider<LocalePreferences, AppLocale>(
  LocalePreferences.new,
);

class LocalePreferences extends Notifier<AppLocale> with AppLogger {
  @override
  AppLocale build() {
    final persisted = ref.watch(sharedPreferencesProvider).requireValue.getString("locale");
    if (persisted == null) return AppLocaleUtils.findDeviceLocale();
    // A saved locale removed from this build follows the supported device locale.
    return AppLocale.values.firstWhere(
      (locale) => locale.name == persisted,
      orElse: AppLocaleUtils.findDeviceLocale,
    );
  }

  Future<void> changeLocale(AppLocale value) async {
    final saved = await ref.read(sharedPreferencesProvider).requireValue.setString("locale", value.name);
    if (!saved) throw StateError('Unable to save language');
    if (ref.mounted) state = value;
  }
}
