import 'dart:async';

import 'package:dartx/dartx.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/notification/in_app_notification_controller.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/features/profile/data/profile_data_providers.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';
import 'package:hiddify/utils/custom_loggers.dart';
import 'package:meta/meta.dart';
import 'package:neat_periodic_task/neat_periodic_task.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'profiles_update_notifier.g.dart';

typedef ProfileUpdateStatus = ({String name, bool success});

@Riverpod(keepAlive: true)
class ForegroundProfilesUpdateNotifier extends _$ForegroundProfilesUpdateNotifier with AppLogger {
  static const prefKey = "profiles_update_check";
  static const interval = Duration(minutes: 15);

  @override
  Stream<ProfileUpdateStatus?> build() {
    var cycleCount = 0;
    final generation = ++_generation;
    final scheduler = NeatPeriodicTaskScheduler(
      name: 'profiles update worker',
      interval: interval,
      timeout: const Duration(minutes: 5),
      task: () async {
        loggy.debug("cycle [${cycleCount++}]");
        await updateProfiles();
      },
    );

    _scheduler = scheduler;
    ref.onDispose(() {
      if (generation == _generation) _scheduler = null;
      unawaited(
        scheduler.stop().catchError((Object error, StackTrace stackTrace) {
          loggy.error("error stopping profile update worker", error, stackTrace);
        }),
      );
    });

    if (ref.watch(Preferences.introCompleted)) {
      loggy.debug("intro done, starting");
      _scheduler?.start();
    } else {
      loggy.debug("intro in process, skipping");
    }
    return const Stream.empty();
  }

  NeatPeriodicTaskScheduler? _scheduler;
  int _generation = 0;
  bool _forceNextRun = false;

  Future<void> trigger() async {
    loggy.debug("triggering update");
    _forceNextRun = true;
    await _scheduler?.trigger();
  }

  @visibleForTesting
  Future<void> updateProfiles() async {
    var force = false;
    if (_forceNextRun) {
      force = true;
      _forceNextRun = false;
    }

    final generation = _generation;
    bool isCurrent() => ref.mounted && generation == _generation;
    final preferences = await ref.read(sharedPreferencesProvider.future);
    if (!isCurrent()) return;
    final previousRun = DateTime.tryParse(preferences.getString(prefKey) ?? "");

    if (!force && previousRun != null && previousRun.add(interval) > DateTime.now()) {
      loggy.debug("too soon! previous run: [$previousRun]");
      return;
    }
    loggy.debug("${force ? "[FORCED] " : ""}running, previous run: [$previousRun]");

    final repository = await ref.read(profileRepositoryProvider.future);
    if (!isCurrent()) return;
    final remoteProfiles = await repository
        .watchAll()
        .map(
          (event) => event.getOrElse((f) {
            loggy.error("error getting profiles");
            throw f;
          }).whereType<RemoteProfileEntity>(),
        )
        .first;

    await for (final profile in Stream.fromIterable(remoteProfiles)) {
      if (!isCurrent()) return;
      final updateInterval = profile.options?.updateInterval;
      if (force || updateInterval != null && updateInterval <= DateTime.now().difference(profile.lastUpdate)) {
        final t = await ref.read(translationsProvider.future);
        if (!isCurrent()) return;
        await repository
            .upsertRemote(profile.url)
            .mapLeft((l) {
              if (!isCurrent()) return;
              loggy.debug("error updating profile [${profile.id}]", l);
              ref
                  .read(inAppNotificationControllerProvider)
                  .showErrorToast(t.pages.profiles.msg.update.failureNamed(name: profile.name));
              state = AsyncData((name: profile.name, success: false));
            })
            .map((_) {
              if (!isCurrent()) return;
              loggy.debug("profile [${profile.id}] updated successfully");
              ref
                  .read(inAppNotificationControllerProvider)
                  .showSuccessToast(t.pages.profiles.msg.update.successNamed(name: profile.name));
              state = AsyncData((name: profile.name, success: true));
            })
            .run();
      } else {
        loggy.debug(
          "skipping profile [${profile.id}] update. last successful update: [${profile.lastUpdate}] - interval: [${profile.options?.updateInterval}]",
        );
      }
    }
    if (isCurrent()) {
      await preferences.setString(prefKey, DateTime.now().toIso8601String());
    }
  }
}
