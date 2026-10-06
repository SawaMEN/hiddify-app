import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:fpdart/fpdart.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/features/connection/data/connection_repository.dart';
import 'package:hiddify/features/connection/model/connection_failure.dart';
import 'package:hiddify/features/profile/data/profile_path_resolver.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service.dart';
import 'package:hiddify/singbox/model/singbox_config_option.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Core extends HiddifyCoreService {
  _Core(super.ref);
  bool fail = false;

  @override
  TaskEither<String, Unit> setup() => TaskEither.of(unit);
  @override
  TaskEither<String, Unit> changeOptions(SingboxConfigOption options) => TaskEither.of(unit);
  @override
  TaskEither<ConnectionFailure, Unit> start(String path, String name, bool disabled) =>
      fail ? TaskEither.left(const ConnectionFailure.backgroundCoreNotAvailable()) : TaskEither.of(unit);
  @override
  TaskEither<String, Unit> restart(String path, String name, bool disabled) =>
      fail ? TaskEither.left('failed startup') : TaskEither.of(unit);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('Relaunch retains successful profile digest but failed startup cannot overwrite it', () async {
    SharedPreferences.setMockInitialValues({'started_by_user': true});
    final preferences = await SharedPreferences.getInstance();
    final directory = await Directory.systemTemp.createTemp('profile-signature-');
    final paths = ProfilePathResolver(directory);
    await paths.directory.create();
    final file = paths.file('test');
    await file.writeAsString('{"outbounds":[]}');
    final profile = ProfileEntity.local(id: 'test', active: true, name: 'test', lastUpdate: DateTime(2026));
    late _Core core;
    final provider = Provider<ConnectionRepositoryImpl>(
      (ref) => ConnectionRepositoryImpl(
        ref: ref,
        directories: (baseDir: directory, workingDir: directory, tempDir: directory),
        singbox: core = _Core(ref),
        configOptionRepository: ConfigOptionRepository(
          preferences: preferences,
          getConfigOptions: () => ref.read(ConfigOptions.singboxConfigOptions),
        ),
        profilePathResolver: paths,
      ),
    );
    final container = ProviderContainer(
      overrides: [sharedPreferencesProvider.overrideWith((ref) async => preferences)],
    );
    addTearDown(() async {
      container.dispose();
      await directory.delete(recursive: true);
    });
    await container.read(sharedPreferencesProvider.future);
    expect((await container.read(provider).connect(profile, false).run()).isRight(), isTrue);
    final successful = preferences.getString('connection_runtime_signature');
    expect(successful, isNotNull);

    container.invalidate(provider); // A fresh repository has no in-memory digest.
    final metadataOnly = profile.copyWith(lastUpdate: DateTime(2026, 10, 6), name: 'new name');
    expect(await container.read(provider).profileRequiresReconnect(metadataOnly), isFalse);
    await file.writeAsString('{"outbounds":[{"type":"direct"}]}');
    expect(await container.read(provider).profileRequiresReconnect(metadataOnly), isTrue);
    core.fail = true;
    expect((await container.read(provider).reconnect(metadataOnly, false).run()).isLeft(), isTrue);
    expect(preferences.getString('connection_runtime_signature'), successful);
    expect(await container.read(provider).profileRequiresReconnect(metadataOnly), isTrue);

    container.invalidate(provider);
    expect(await container.read(provider).profileRequiresReconnect(metadataOnly), isTrue);
    expect((await container.read(provider).connect(metadataOnly, false).run()).isRight(), isTrue);
    container.invalidate(provider);
    expect(await container.read(provider).profileRequiresReconnect(metadataOnly), isFalse);
  });
}
