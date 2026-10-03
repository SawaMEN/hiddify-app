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
    // keep backward compatibility with chinese after changing zh to zh_CN
    if (persisted == "zh") {
      return AppLocale.zhCn;
    }
    try {
      return AppLocale.values.byName(persisted);
    } catch (e) {
      loggy.error("error setting locale: [$persisted]", e);
      return AppLocale.en;
    }
  }

  Future<void> changeLocale(AppLocale value) async {
    state = value;
    await ref.read(sharedPreferencesProvider).requireValue.setString("locale", value.name);
  }
}
