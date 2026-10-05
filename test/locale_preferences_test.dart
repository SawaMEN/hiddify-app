import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/localization/locale_preferences.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('Only English and Russian are supported', () {
    expect(AppLocale.values.map((locale) => locale.languageCode).toSet(), {'en', 'ru'});
  });

  test('Removed saved languages fall back and language changes survive restart', () async {
    SharedPreferences.setMockInitialValues({'locale': 'zhCn'});
    final preferences = await SharedPreferences.getInstance();
    ProviderContainer createContainer() => ProviderContainer(overrides: [
      sharedPreferencesProvider.overrideWith((_) async => preferences),
    ]);
    final first = createContainer();
    final second = createContainer();
    try {
      await first.read(sharedPreferencesProvider.future);
      expect(AppLocale.values, contains(first.read(localePreferencesProvider)));
      await first.read(localePreferencesProvider.notifier).changeLocale(AppLocale.ru);
      expect(preferences.getString('locale'), 'ru');
      await second.read(sharedPreferencesProvider.future);
      expect(second.read(localePreferencesProvider), AppLocale.ru);
    } finally {
      first.dispose();
      second.dispose();
    }
  });
}
