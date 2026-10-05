import 'package:fpdart/fpdart.dart';
import 'package:hiddify/core/model/directories.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
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
              (_) => applyConfigOption(activeProfile).flatMap(
                (_) => singbox.start(
                  profilePathResolver.file(activeProfile.id).path,
                  activeProfile.name,
                  disableMemoryLimit,
                ),
              ),
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
        () => applyConfigOption(activeProfile)
            .flatMap(
              (_) => singbox
                  .restart(profilePathResolver.file(activeProfile.id).path, activeProfile.name, disableMemoryLimit)
                  .mapLeft(UnexpectedConnectionFailure.new),
            )
            .run(),
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
          return unit;
        }, (error, stackTrace) => error is ConnectionFailure ? error : ConnectionFailure.unexpected(error, stackTrace)),
      );
}
