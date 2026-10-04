import 'dart:io';

import 'package:dio/dio.dart';
import 'package:hiddify/core/utils/keyed_operations.dart';
import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';
import 'package:fpdart/fpdart.dart';
import 'package:hiddify/core/db/db.dart';
import 'package:hiddify/core/utils/exception_handler.dart';
import 'package:hiddify/features/profile/data/profile_data_mapper.dart';
import 'package:hiddify/features/profile/data/profile_data_source.dart';
import 'package:hiddify/features/profile/data/profile_parser.dart';
import 'package:hiddify/features/profile/data/profile_path_resolver.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';
import 'package:hiddify/features/profile/model/profile_failure.dart';
import 'package:hiddify/features/profile/model/profile_sort_enum.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service.dart';
import 'package:hiddify/utils/custom_loggers.dart';
import 'package:uuid/uuid.dart';

abstract interface class ProfileRepository {
  TaskEither<ProfileFailure, Unit> init();
  TaskEither<ProfileFailure, ProfileEntity?> getById(String id);
  TaskEither<ProfileFailure, Unit> setAsActive(String id);
  TaskEither<ProfileFailure, Unit> deleteById(String id, bool isActive);
  Stream<Either<ProfileFailure, ProfileEntity?>> watchActiveProfile();
  Stream<Either<ProfileFailure, bool>> watchHasAnyProfile();
  Stream<Either<ProfileFailure, List<ProfileEntity>>> watchAll({
    ProfilesSort sort = ProfilesSort.lastUpdate,
    SortMode sortMode = SortMode.ascending,
  });
  TaskEither<ProfileFailure, Unit> upsertRemote(String url, {UserOverride? userOverride, CancelToken? cancelToken});
  TaskEither<ProfileFailure, Unit> addLocal(String content, {UserOverride? userOverride});
  TaskEither<ProfileFailure, Unit> offlineUpdate(ProfileEntity nProfile, String nContent);
  TaskEither<ProfileFailure, Unit> validateConfig(String path, String tempPath, String? profileOverride, bool debug);
  TaskEither<ProfileFailure, String> generateConfig(String id);
  TaskEither<ProfileFailure, String> getRawConfig(String id);
}

class ProfileRepositoryImpl with ExceptionHandler, InfraLogger implements ProfileRepository {
  ProfileRepositoryImpl({
    required ProfileDataSource profileDataSource,
    required ProfilePathResolver profilePathResolver,
    required HiddifyCoreService singbox,
    required ConfigOptionRepository configOptionRepository,
    required ProfileParser profileParser,
  }) : _profileParser = profileParser,
       _configOptionRepo = configOptionRepository,
       _singbox = singbox,
       _profilePathResolver = profilePathResolver,
       _profileDataSource = profileDataSource;

  // URL lookup and file replacement share one queue, including delete/edit operations.
  final _operations = KeyedOperations();
  final ProfileDataSource _profileDataSource;
  final ProfilePathResolver _profilePathResolver;
  final HiddifyCoreService _singbox;
  final ConfigOptionRepository _configOptionRepo;
  final ProfileParser _profileParser;

  @override
  TaskEither<ProfileFailure, Unit> init() {
    return exceptionHandler(() async {
      if (!kIsWeb) {
        if (!await _profilePathResolver.directory.exists()) {
          await _profilePathResolver.directory.create(recursive: true);
        }
      }
      return right(unit);
    }, ProfileUnexpectedFailure.new);
  }

  @override
  TaskEither<ProfileFailure, ProfileEntity?> getById(String id) {
    return TaskEither.tryCatch(
      () => _profileDataSource.getById(id).then((value) => value?.toEntity()),
      ProfileUnexpectedFailure.new,
    );
  }

  @override
  TaskEither<ProfileFailure, Unit> setAsActive(String id) {
    return TaskEither.tryCatch(() async {
      await _profileDataSource.edit(id, const ProfileEntriesCompanion(active: Value(true)));
      return unit;
    }, ProfileUnexpectedFailure.new);
  }

  @override
  TaskEither<ProfileFailure, Unit> deleteById(String id, bool isActive) => TaskEither.tryCatch(
    () => _operations.run('profiles', () async {
      final file = _profilePathResolver.file(id);
      final backup = File('${file.path}.delete-${const Uuid().v4()}');
      if (await file.exists()) await file.rename(backup.path);
      try {
        await _profileDataSource.deleteById(id, isActive);
      } catch (_) {
        if (await backup.exists()) await backup.rename(file.path);
        rethrow;
      }
      await _cleanup(backup);
      return unit;
    }),
    ProfileUnexpectedFailure.new,
  );

  @override
  Stream<Either<ProfileFailure, ProfileEntity?>> watchActiveProfile() {
    return _profileDataSource.watchActiveProfile().map((event) => event?.toEntity()).handleExceptions((
      error,
      stackTrace,
    ) {
      loggy.error('error watching active profile', error, stackTrace);
      return ProfileUnexpectedFailure(error, stackTrace);
    });
  }

  @override
  Stream<Either<ProfileFailure, bool>> watchHasAnyProfile() {
    return _profileDataSource
        .watchProfilesCount()
        .map((event) => event != 0)
        .handleExceptions(ProfileUnexpectedFailure.new);
  }

  @override
  Stream<Either<ProfileFailure, List<ProfileEntity>>> watchAll({
    ProfilesSort sort = ProfilesSort.lastUpdate,
    SortMode sortMode = SortMode.ascending,
  }) {
    return _profileDataSource
        .watchAll(sort: sort, sortMode: sortMode)
        .map((event) => event.map((e) => e.toEntity()).toList())
        .handleExceptions(ProfileUnexpectedFailure.new);
  }

  Future<void> _cleanup(File file) async {
    try {
      if (await file.exists()) await file.delete();
    } catch (e, st) {
      loggy.warning('Unable to remove temporary profile file', e, st);
    }
  }

  void _checkCancelled(CancelToken? token) {
    if (token?.isCancelled ?? false) {
      throw const ProfileFailure.cancelByUser('Profile update cancelled');
    }
  }

  // Native parse writes only to a staging destination. The old source stays intact
  // until validation and metadata parsing have both succeeded.
  Future<void> _validate(File source, String? overrides) async {
    final output = File('${source.path}.validated');
    try {
      final result = await validateConfig(output.path, source.path, overrides, false).run();
      result.match((error) => throw error, (_) {});
    } finally {
      await _cleanup(output);
    }
  }

  Future<void> _commitProfile(
    File source,
    File destination,
    Future<void> Function() writeMetadata,
    CancelToken? token,
  ) async {
    _checkCancelled(token);
    final backup = File('${destination.path}.backup-${const Uuid().v4()}');
    final existed = await destination.exists();
    if (existed) await destination.copy(backup.path);
    var removeBackup = true;
    try {
      _checkCancelled(token);
      // On Android this is an atomic replacement on the same filesystem.
      await source.rename(destination.path);
      await writeMetadata();
    } catch (_) {
      if (existed && await backup.exists()) {
        removeBackup = false; // preserve recovery data if restoration itself fails
        await backup.rename(destination.path);
        removeBackup = true;
      } else {
        await _cleanup(destination);
      }
      rethrow;
    } finally {
      if (removeBackup) await _cleanup(backup);
    }
  }

  @override
  TaskEither<ProfileFailure, Unit> upsertRemote(String url, {UserOverride? userOverride, CancelToken? cancelToken}) =>
      TaskEither.tryCatch(
        () => _operations.run('profiles', () async {
          _checkCancelled(cancelToken);
          final entry = await _profileDataSource.getByUrl(url);
          final existing = entry?.toEntity();
          final id = existing?.id ?? const Uuid().v4();
          final file = _profilePathResolver.file(id);
          final temp = _profilePathResolver.tempFile('$id-${const Uuid().v4()}');
          try {
            final task = existing is RemoteProfileEntity
                ? _profileParser.updateRemote(
                    rp: userOverride == null ? existing : existing.copyWith(userOverride: userOverride),
                    tempFilePath: temp.path,
                    cancelToken: cancelToken,
                  )
                : _profileParser.addRemote(
                    id: id,
                    url: url,
                    tempFilePath: temp.path,
                    userOverride: userOverride,
                    cancelToken: cancelToken,
                  );
            final profile = (await task.run()).match((error) => throw error, (value) => value);
            _checkCancelled(cancelToken);
            await _validate(temp, ProfileParser.profileOverrideHelper(profile: profile));
            await _commitProfile(
              temp,
              file,
              () => existing == null ? _profileDataSource.insert(profile) : _profileDataSource.edit(id, profile),
              cancelToken,
            );
            return unit;
          } finally {
            await _cleanup(temp);
          }
        }),
        (error, st) => error is ProfileFailure ? error : ProfileFailure.unexpected(error, st),
      );

  @override
  TaskEither<ProfileFailure, Unit> addLocal(String content, {UserOverride? userOverride}) => TaskEither.tryCatch(
    () => _operations.run('profiles', () async {
      final id = const Uuid().v4();
      final file = _profilePathResolver.file(id);
      final temp = _profilePathResolver.tempFile('$id-${const Uuid().v4()}');
      try {
        await temp.writeAsString(content);
        final profile =
            (await _profileParser
                    .addLocal(id: id, content: content, tempFilePath: temp.path, userOverride: userOverride)
                    .run())
                .match((e) => throw e, (p) => p);
        await _validate(temp, ProfileParser.profileOverrideHelper(profile: profile));
        await _commitProfile(temp, file, () => _profileDataSource.insert(profile), null);
        return unit;
      } finally {
        await _cleanup(temp);
      }
    }),
    (error, st) => error is ProfileFailure ? error : ProfileFailure.unexpected(error, st),
  );

  @override
  TaskEither<ProfileFailure, Unit> offlineUpdate(ProfileEntity profile, String nContent) => TaskEither.tryCatch(
    () => _operations.run('profiles', () async {
      final existing = (await _profileDataSource.getById(profile.id))?.toEntity();
      if (existing == null || existing.runtimeType != profile.runtimeType) throw const ProfileFailure.notFound();
      final file = _profilePathResolver.file(profile.id);
      final temp = _profilePathResolver.tempFile('${profile.id}-${const Uuid().v4()}');
      try {
        await temp.writeAsString(nContent);
        final entry = _profileParser
            .offlineUpdate(
              profile: existing.copyWith(userOverride: profile.userOverride),
              tempFilePath: temp.path,
            )
            .match((e) => throw e, (p) => p);
        await _validate(temp, ProfileParser.profileOverrideHelper(profile: entry));
        await _commitProfile(temp, file, () => _profileDataSource.edit(profile.id, entry), null);
        return unit;
      } finally {
        await _cleanup(temp);
      }
    }),
    (error, st) => error is ProfileFailure ? error : ProfileFailure.unexpected(error, st),
  );

  @override
  TaskEither<ProfileFailure, Unit> validateConfig(String path, String tempPath, String? profileOverride, bool debug) {
    // Validate override types without applying them to the active background service.
    // Profile overrides are applied only when this profile is actually connected.
    return TaskEither.fromEither(_configOptionRepo.fullOptionsOverrided(profileOverride))
        .mapLeft((failure) => ProfileFailure.invalidConfig(null, failure))
        .flatMap((_) => _singbox.validateConfigByPath(path, tempPath, debug).mapLeft(ProfileFailure.invalidConfig));
  }

  @override
  TaskEither<ProfileFailure, String> generateConfig(String id) => TaskEither.fromEither(
    Either.tryCatch(() => _profilePathResolver.file(id), ProfileFailure.unexpected),
  ).flatMap((configFile) => _singbox.generateFullConfigByPath(configFile.path).mapLeft(ProfileFailure.unexpected));

  @override
  TaskEither<ProfileFailure, String> getRawConfig(String id) {
    return TaskEither.fromEither(Either.tryCatch(() => _profilePathResolver.file(id), ProfileFailure.unexpected))
        .flatMap((configFile) => TaskEither.tryCatch(() => configFile.readAsString(), ProfileFailure.unexpected));
  }
}
