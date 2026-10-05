import 'dart:async';
import 'dart:convert';

import 'package:hiddify/core/router/go_router/go_router_notifier.dart';
import 'package:hiddify/features/profile/import/import_summary.dart';
import 'package:hiddify/features/profile/import/import_preview.dart';

import 'package:dio/dio.dart';
import 'package:fpdart/fpdart.dart';
import 'package:hiddify/core/haptic/haptic_service.dart';
import 'package:hiddify/core/http_client/http_client_provider.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/model/failures.dart';
import 'package:hiddify/core/notification/in_app_notification_controller.dart';
import 'package:hiddify/core/router/dialog/dialog_notifier.dart';
import 'package:hiddify/features/profile/add/model/free_profiles_model.dart';
import 'package:hiddify/features/profile/data/profile_data_providers.dart';
import 'package:hiddify/features/profile/data/profile_repository.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';
import 'package:hiddify/features/profile/model/profile_failure.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';
import 'package:hiddify/utils/riverpod_utils.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'profile_notifier.g.dart';

@riverpod
class AddProfileNotifier extends _$AddProfileNotifier with AppLogger {
  @override
  AsyncValue<Unit?> build() {
    ref.disposeDelay(const Duration(minutes: 1));
    ref.onDispose(() {
      loggy.debug("disposing");
      _cancelToken?.cancel();
    });
    listenSelf((previous, next) {
      final t = ref.read(translationsProvider).requireValue;
      final notification = ref.read(inAppNotificationControllerProvider);
      switch (next) {
        case AsyncData(value: final _?):
          notification.showSuccessToast(t.pages.profiles.msg.save.success);
        case AsyncError(:final error):
          if (error case ProfileInvalidUrlFailure()) {
            notification.showErrorToast(t.pages.profiles.msg.invalidUrl);
          } else if (error case ProfileCancelByUserFailure()) {
            return;
          } else {
            ref
                .read(dialogNotifierProvider.notifier)
                .showCustomAlertFromErr(t.presentError(error, action: t.pages.profiles.msg.add.failure));
          }
        case _:
          break;
      }
    });
    ref.onDispose(() {
      if (!(_cancelToken?.isCancelled ?? true)) _cancelToken?.cancel();
    });
    return const AsyncData(null);
  }

  ProfileRepository get _profilesRepo => ref.read(profileRepositoryProvider).requireValue;
  CancelToken? _cancelToken;
  Future<bool> _preview(ImportSummary summary, bool insecure) async {
    final context = rootNavKey.currentContext;
    if (!ref.mounted || context == null) return false;
    return showImportPreview(context, ref.read(translationsProvider).requireValue, summary, insecure);
  }

  Future<void> addClipboard(String rawInput) async {
    if (state.isLoading) return;
    state = const AsyncLoading();
    final result = await AsyncValue.guard(() async {
      // final activeProfile = await ref.read(activeProfileProvider.future);
      // final markAsActive = activeProfile == null || ref.read(Preferences.markNewProfileActive);
      final TaskEither<ProfileFailure, Unit> task;
      if (LinkParser.parse(rawInput) case (final rs)?) {
        loggy.debug("adding remote profile");
        task = _profilesRepo.upsertRemote(
          rs.url,
          userOverride: rs.name.isNotEmpty ? UserOverride(name: rs.name) : null,
          cancelToken: _cancelToken = CancelToken(),
          preview: _preview,
        );
      } else {
        loggy.debug("adding profile, content");
        task = _profilesRepo.addLocal(safeDecodeBase64(rawInput), preview: _preview);
      }
      return await task
          .match(
            (err) {
              loggy.warning("failed to add profile", err);
              throw err;
            },
            (_) {
              loggy.info("successfully added profile");
              return unit;
            },
          )
          .run();
    });
    if (ref.mounted) state = result;
  }

  Future<void> addManual({required String url, required UserOverride userOverride}) async {
    if (state.isLoading) return;
    state = const AsyncLoading();
    final result = await AsyncValue.guard(() async {
      final task = _profilesRepo.upsertRemote(
        url,
        userOverride: userOverride,
        cancelToken: _cancelToken = CancelToken(),
        preview: _preview,
      );
      return await task
          .match(
            (err) {
              loggy.warning("failed to add profile", err);
              throw err;
            },
            (r) {
              loggy.info("successfully added profile, mark as active? [true]");
              return r;
            },
          )
          .run();
    });
    if (ref.mounted) state = result;
  }
}

@riverpod
class UpdateProfileNotifier extends _$UpdateProfileNotifier with AppLogger {
  @override
  AsyncValue<Unit?> build(String id) {
    ref.disposeDelay(const Duration(minutes: 1));
    listenSelf((previous, next) {
      final t = ref.read(translationsProvider).requireValue;
      final notification = ref.read(inAppNotificationControllerProvider);
      switch (next) {
        case AsyncData(value: final _?):
          notification.showSuccessToast(t.pages.profiles.msg.update.success);
        case AsyncError(:final error):
          ref
              .read(dialogNotifierProvider.notifier)
              .showCustomAlertFromErr(t.presentError(error, action: t.pages.profiles.msg.update.failure));
        case _:
          break;
      }
    });
    return const AsyncData(null);
  }

  ProfileRepository get _profilesRepo => ref.read(profileRepositoryProvider).requireValue;

  Future<void> updateProfile(RemoteProfileEntity profile) async {
    if (state.isLoading) return;
    state = const AsyncLoading();
    final repository = _profilesRepo;
    await ref.read(hapticServiceProvider.notifier).lightImpact();
    if (!ref.mounted) return;
    final result = await AsyncValue.guard(() async {
      return await repository
          .upsertRemote(profile.url)
          .match(
            (err) {
              loggy.warning("failed to update profile", err);
              throw err;
            },
            (_) async {
              loggy.info('successfully updated profile');
              if (!ref.mounted) return unit;

              return unit;
            },
          )
          .run();
    });
    if (ref.mounted) state = result;
  }
}

@riverpod
class FreeSwitchNotifier extends _$FreeSwitchNotifier {
  @override
  bool build() {
    return false;
  }

  Future<void> onChange(bool value) async => state = value;
}

@riverpod
class AddProfilePageNotifier extends _$AddProfilePageNotifier {
  @override
  AddProfilePages build() => AddProfilePages.options;

  void goOptions() => state = AddProfilePages.options;
  void goManual() => state = AddProfilePages.manual;
}

enum AddProfilePages { options, manual }

@riverpod
class FreeProfilesNotifier extends _$FreeProfilesNotifier {
  @override
  Future<List<FreeProfile>> build() async {
    final httpClient = ref.watch(httpClientProvider);
    final res = await httpClient.get(
      'https://raw.githubusercontent.com/hiddify/hiddify-app/refs/heads/main/test.configs/free_configs',
    );
    if (res.statusCode == 200) {
      return FreeProfilesModel.fromJson(jsonDecode(res.data.toString()) as Map<String, dynamic>).profiles;
    }
    return <FreeProfile>[];
  }
}

@riverpod
Future<List<FreeProfile>> freeProfilesFilteredByRegion(Ref ref) async {
  final freeProfiles = await ref.watch(freeProfilesNotifierProvider.future);
  if (!ref.mounted) return [];
  final region = ref.watch(ConfigOptions.region);
  return freeProfiles.where((e) => e.region.contains(region.name) || e.region.isEmpty).toList();
}
