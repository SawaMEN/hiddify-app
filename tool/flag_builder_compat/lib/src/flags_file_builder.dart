import 'dart:async';
import 'dart:io';

import 'package:build/build.dart';
import 'package:path/path.dart';

/// Combines generated flag fragments into circle_flags/lib/src/flags.dart.
class FlagsFileBuilder implements Builder {
  FlagsFileBuilder();

  static AssetId _allFileOutput(BuildStep buildStep) {
    return AssetId(
      buildStep.inputId.package,
      join('lib', 'src', 'flags.dart'),
    );
  }

  @override
  Map<String, List<String>> get buildExtensions => {
        r'$lib$': const ['src/flags.dart'],
      };

  @override
  FutureOr<void> build(BuildStep buildStep) async {
    final flagFile = File('lib/src/flags.dart');
    if (flagFile.existsSync()) {
      flagFile.deleteSync();
    }

    if (buildStep.inputId.package != 'circle_flags') {
      return;
    }

    final output = _allFileOutput(buildStep);
    final fileHeader = '''
// GENERATED FILE, timestamp: ${DateTime.now()}
// Regenerate with: dart run build_runner build

import 'package:circle_flags/circle_flags.dart';

/// List of available flags(iso codes) for [CircleFlag].
abstract class Flags {
''';

    var list = '''
  static const values = <String>[
''';
    var newFileContent = '';
    for (final file
        in Directory('.dart_tool/build/generated/circle_flags/assets/svg/')
            .listSync()
            .toList()
          ..sort((a, b) => a.path.compareTo(b.path))) {
      if (file is File) {
        newFileContent += file.readAsStringSync();
        var baseName = file.path.split('/').last;
        baseName = baseName.substring(0, baseName.length - 9);
        list += "    ${baseName.toUpperCase().replaceAll('-', '_')},\n";
      }
    }

    list += '  ];\n';
    newFileContent += '}\n';
    await buildStep.writeAsString(output, fileHeader + list + newFileContent);
  }
}
