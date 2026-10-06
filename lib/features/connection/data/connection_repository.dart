import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:fpdart/fpdart.dart';
import 'package:hiddify/core/model/directories.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/core/router/dialog/dialog_notifier.dart';
import 'package:hiddify/core/utils/exception_handler.dart';
import 'package:hiddify/features/connection/model/connection_failure.dart';
import 'package:hiddify/features/connection/model/connection_status.dart';
import 'package:hiddify/features/profile/data/profile_path_resolver.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service.dart';
import 'package:hiddify/singbox/model/core_status.dart';
import 'package:hiddify/singbox/model/singbox_config_option.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:meta/meta.dart';

abstract interface class ConnectionRepository {
  SingboxConfigOption? get configOptionsSnapshot;
  Future<bool> profileRequiresReconnect(ProfileEntity profile);

  TaskEither<ConnectionFailure, Unit> setup();
  Stream<ConnectionStatus> watchConnectionStatus();
  TaskEither<ConnectionFailure, Unit> connect(ProfileEntity activeProfile, bool disableMemoryLimit);
  TaskEither<ConnectionFailure, Unit> disconnect();
  TaskEither<ConnectionFailure, Unit> reconnect(ProfileEntity activeProfile, bool disableMemoryLimit);
}

class ConnectionRepositoryImpl with ExceptionHandler, InfraLogger implements ConnectionRepository {
  ConnectionRepositoryImpl({
    required this.ref,
    required this.directories,
    required this.singbox,
    required this.configOptionRepository,
    required this.profilePathResolver,
  });

  final Ref ref;
  final Directories directories;
  final HiddifyCoreService singbox;
  final ConfigOptionRepository configOptionRepository;
  final ProfilePathResolver profilePathResolver;

  SingboxConfigOption? _configOptionsSnapshot;
  @override
  SingboxConfigOption? get configOptionsSnapshot => _configOptionsSnapshot;

  String? _runtimeSignature;
  String? _configuredSignature;
  static const _signaturePreference = 'connection_runtime_signature';
  Future<String> _signature(ProfileEntity profile) async {
    final digest = await sha256.bind(profilePathResolver.file(profile.id).openRead()).first;
    return sha256.convert(utf8.encode(jsonEncode([profile.id, profile.profileOverride(), digest.toString()]))).toString();
  }

  @override
  Future<bool> profileRequiresReconnect(ProfileEntity profile) async {
    final previous = _runtimeSignature ?? ref.read(sharedPreferencesProvider).value?.getString(_signaturePreference);
    return previous != await _signature(profile);
  }

  TaskEither<ConnectionFailure, Unit> _recordSuccessfulStart() => TaskEither(() async {
    // Retain the successful profile digest across UI process restarts. A routine
    // subscription refresh must not restart an unchanged, already running VPN.
    final signature = _configuredSignature;
    if (signature != null && ref.mounted && ref.read(Preferences.startedByUser)) {
      _runtimeSignature = signature;
      try {
        await ref.read(sharedPreferencesProvider).requireValue.setString(_signaturePreference, signature);
      } catch (error) {
        loggy.warning('Unable to save the running profile signature: $error');
      }
    }
    return right(unit);
  });

  bool _initialized = false;

  @override
  TaskEither<ConnectionFailure, Unit> setup() {
    if (_initialized) return TaskEither.of(unit);
    return exceptionHandler(() {
      loggy.debug('setting up singbox');
      return singbox
          .setup()
          .map((result) {
            _initialized = true;
            return result;
          })
          .mapLeft(UnexpectedConnectionFailure.new)
          .run();
    }, UnexpectedConnectionFailure.new);
  }

  @override
  Stream<ConnectionStatus> watchConnectionStatus() {
    return singbox.watchStatus().map(
      (event) => switch (event) {
        CoreStopped() => Disconnected(event.getCoreAlert()),
        CoreStarting() => const Connecting(),
        CoreStarted() => const Connected(),
        CoreStopping() => const Disconnecting(),
      },
    );
  }

  @override
  TaskEither<ConnectionFailure, Unit> connect(ProfileEntity activeProfile, bool disableMemoryLimit) {
    return TaskEither(
      () => singbox.runExclusive(
        () => setup()
            .flatMap(
              (_) => applyConfigOption(activeProfile).flatMap((_) {
                // Applying/generating a profile can take long enough for the user to cancel.
                // Re-check intent immediately before crossing into the native start path so an
                // old queued connect cannot revive a VPN after Stop was already requested.
                if (!ref.read(Preferences.startedByUser)) {
                  loggy.debug('connect cancelled before core start');
                  return TaskEither.of(unit);
                }
                return singbox
                    .start(profilePathResolver.file(activeProfile.id).path, activeProfile.name, disableMemoryLimit)
                    .flatMap((_) => _recordSuccessfulStart());
              }),
            )
            .run(),
      ),
    );
  }

  @override
  TaskEither<ConnectionFailure, Unit> disconnect() => singbox.stop().mapLeft(UnexpectedConnectionFailure.new);

  @override
  TaskEither<ConnectionFailure, Unit> reconnect(ProfileEntity activeProfile, bool disableMemoryLimit) {
    return TaskEither(
      () => singbox.runExclusive(
        () => applyConfigOption(activeProfile).flatMap((_) {
          if (!ref.read(Preferences.startedByUser)) {
            loggy.debug('reconnect cancelled before core restart');
            return TaskEither.of(unit);
          }
          return singbox
              .restart(profilePathResolver.file(activeProfile.id).path, activeProfile.name, disableMemoryLimit)
              .mapLeft<ConnectionFailure>(UnexpectedConnectionFailure.new)
              .flatMap((_) => _recordSuccessfulStart());
        }).run(),
      ),
    );
  }

  @visibleForTesting
  TaskEither<ConnectionFailure, Unit> applyConfigOption(
    ProfileEntity prof,
  ) => TaskEither.fromEither(configOptionRepository.fullOptionsOverrided(prof.profileOverride()))
      .mapLeft((failure) => ConnectionFailure.invalidConfigOption(null, failure))
      .flatMap(
        (overridedOptions) => TaskEither.tryCatch(() async {
          if (!overridedOptions.chainStatus.isOff()) {
            final isWarpLicenseAgreed = ref.read(Preferences.warpConsentGiven) == true;
            final isWarpEnabled =
                (overridedOptions.chainStatus.isUnblocker() && overridedOptions.unblocker.mode.isWarp()) ||
                (overridedOptions.chainStatus.isExtraSecurity() && overridedOptions.extraSecurity.mode.isWarp());
            if (!isWarpLicenseAgreed && isWarpEnabled) {
              final isAgreed = await ref.read(dialogNotifierProvider.notifier).showWarpLicense();
              if (isAgreed == true) {
                await ref.read(Preferences.warpConsentGiven.notifier).update(true);
              } else {
                throw const MissingWarpLicense();
              }
            }

            final isPsiphonLicenseAgreed = ref.read(Preferences.psiphonConsentGiven) == true;
            final isPsiphonEnabled =
                (overridedOptions.chainStatus.isUnblocker() && overridedOptions.unblocker.mode.isPsiphon()) ||
                (overridedOptions.chainStatus.isExtraSecurity() && overridedOptions.extraSecurity.mode.isPsiphon());
            if (!isPsiphonLicenseAgreed && isPsiphonEnabled) {
              final isAgreed = await ref.read(dialogNotifierProvider.notifier).showPsiphonLicense();
              if (isAgreed == true) {
                await ref.read(Preferences.psiphonConsentGiven.notifier).update(true);
              } else {
                throw const MissingPsiphonLicense();
              }
            }
          }

          final result = await singbox.changeOptions(overridedOptions).run();
          result.match((error) => throw ConnectionFailure.invalidConfig(error), (_) {});
          _configOptionsSnapshot = overridedOptions;
          _configuredSignature = await _signature(prof);
          return unit;
        }, (error, stackTrace) => error is ConnectionFailure ? error : ConnectionFailure.unexpected(error, stackTrace)),
      );
}
