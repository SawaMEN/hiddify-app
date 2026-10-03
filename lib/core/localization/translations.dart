import 'package:hiddify/core/localization/locale_preferences.dart';
import 'package:hiddify/gen/translations.g.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

export 'package:hiddify/gen/translations.g.dart';

final translationsProvider = FutureProvider<Translations>((ref) async {
  return await ref.watch(localePreferencesProvider).build();
});
