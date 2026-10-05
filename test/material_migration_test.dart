import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart' as legacy;
import 'package:flutter_test/flutter_test.dart';
import 'package:material_ui/material_ui.dart';

void main() {
  testWidgets('legacy plugin widgets receive the modern theme and localization', (tester) async {
    final colors = ColorScheme.fromSeed(seedColor: const Color(0xff18d6bd), brightness: Brightness.dark);
    late legacy.ThemeData legacyTheme;
    late legacy.MaterialLocalizations legacyLocalizations;
    late String modernOkLabel;
    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData(colorScheme: colors),
        locale: const Locale('ru'),
        supportedLocales: const [Locale('ru')],
        localizationsDelegates: GlobalMaterialLocalizations.delegates,
        builder: (context, child) => MaterialUiCompatibilityBridge(child: child!),
        home: legacy.Builder(
          builder: (context) {
            legacyTheme = legacy.Theme.of(context);
            legacyLocalizations = legacy.MaterialLocalizations.of(context);
            modernOkLabel = MaterialLocalizations.of(context).okButtonLabel;
            return const legacy.Scaffold(body: legacy.Text('legacy plugin'));
          },
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(legacyTheme.colorScheme.primary, colors.primary);
    expect(legacyTheme.brightness, Brightness.dark);
    expect(legacyLocalizations.okButtonLabel, modernOkLabel);
  });

  testWidgets('dynamic_color uses the standalone Material ColorScheme', (tester) async {
    await tester.pumpWidget(
      DynamicColorBuilder(
        builder: (ColorScheme? light, ColorScheme? dark) => MaterialApp(
          theme: ThemeData(colorScheme: light ?? ColorScheme.fromSeed(seedColor: const Color(0xff18d6bd))),
          darkTheme: ThemeData(
            colorScheme: dark ?? ColorScheme.fromSeed(seedColor: const Color(0xff18d6bd), brightness: Brightness.dark),
          ),
          home: const Scaffold(body: Text('Material UI')),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.text('Material UI'), findsOneWidget);
  });
}
