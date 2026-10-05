import 'package:hiddify/gen/fonts.gen.dart';
import 'package:hiddify/gen/translations.g.dart';

extension AppLocaleX on AppLocale {
  String get preferredFontFamily => FontFamily.manrope;

  String get localeName => switch (this) {
    AppLocale.en => 'English',
    AppLocale.ru => 'Русский',
  };
}
