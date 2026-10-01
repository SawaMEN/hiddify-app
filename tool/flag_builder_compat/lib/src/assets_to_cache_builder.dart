import 'dart:async';

import 'package:build/build.dart';

/// Generates a small cache fragment for each flag SVG from circle_flags.
class AssetToCacheBuilder implements Builder {
  AssetToCacheBuilder();

  @override
  Map<String, List<String>> get buildExtensions => {
        '.svg': const ['.part.txt'],
      };

  @override
  FutureOr<void> build(BuildStep buildStep) async {
    if (buildStep.inputId.package != 'circle_flags') {
      return;
    }

    final inputId = buildStep.inputId;
    final baseName = inputId.pathSegments.last;
    final name = baseName.substring(0, baseName.length - 4);
    final mapEntry =
        "  static const String ${name.toUpperCase().replaceAll('-', '_')} = '$name';\n";

    await buildStep.writeAsString(
      inputId.changeExtension('.part.txt'),
      mapEntry,
    );
  }
}
