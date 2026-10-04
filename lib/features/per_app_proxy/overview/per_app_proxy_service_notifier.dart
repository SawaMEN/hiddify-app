import 'dart:async';

import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/notification/in_app_notification_controller.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/features/per_app_proxy/data/selected_data_provider.dart';
import 'package:hiddify/features/per_app_proxy/model/per_app_proxy_mode.dart';
import 'package:hiddify/features/per_app_proxy/overview/per_app_proxy_notifier.dart';
import 'package:hiddify/utils/custom_loggers.dart';
import 'package:installed_apps/index.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'per_app_proxy_service_notifier.g.dart';

@riverpod
class PerAppProxyService extends _$PerAppProxyService with AppLogger {
  int _generation = 0;
  bool _updating = false;

  @override
  Future<void> build() async {
    final generation = ++_generation;
    final dataSource = ref.read(appProxyDataSourceProvider);
    StreamSubscription? include;
    StreamSubscription? exclude;
    Timer? timer;
    bool current() => ref.mounted && generation == _generation;
    // Register before the first await, and retain only this build's resources.
    ref.onDispose(() {
      if (generation == _generation) _generation++;
      unawaited(include?.cancel());
      unawaited(exclude?.cancel());
      timer?.cancel();
    });
    final phonePkgs = (await InstalledApps.getInstalledApps(false)).map((e) => e.packageName).toSet();
    if (!current()) return;
    void failed(Object e, StackTrace st) => loggy.warning('Per-app routing stream failed', e, st);
    include = dataSource.watchActivePackages(phonePkgs: phonePkgs, mode: AppProxyMode.include).listen((pkgs) {
      if (current()) unawaited(ref.read(Preferences.includeApps.notifier).update(pkgs));
    }, onError: failed);
    exclude = dataSource.watchActivePackages(phonePkgs: phonePkgs, mode: AppProxyMode.exclude).listen((pkgs) {
      if (current()) unawaited(ref.read(Preferences.excludeApps.notifier).update(pkgs));
    }, onError: failed);
    timer = Timer.periodic(const Duration(days: 1), (_) => unawaited(_autoSelectionUpdate(generation)));
    await _autoSelectionUpdate(generation);
  }

  Future<void> _autoSelectionUpdate(int generation) async {
    if (_updating || !ref.mounted || generation != _generation) return;
    _updating = true;
    try {
      final autoRegion = ref.read(Preferences.autoAppsSelectionRegion);
      if (autoRegion == null) return;
      final mode = ref.read(Preferences.perAppProxyMode).toAppProxy();
      final lastUpdate = ref.read(Preferences.autoAppsSelectionLastUpdate);
      final days = ref.read(Preferences.autoAppsSelectionUpdateInterval).round().clamp(1, 3650).toInt();
      if (mode != null && (lastUpdate == null || DateTime.now().difference(lastUpdate) > Duration(days: days))) {
        // Hold this notifier alive until its side effect has finished.
        final subscription = ref.listen(perAppProxyProvider(mode), (_, _) {});
        try {
          final result = await ref.read(perAppProxyProvider(mode).notifier).applyAutoSelection();
          if (!ref.mounted || generation != _generation) return;
          if (result) {
            final t = ref.read(translationsProvider).requireValue;
            ref
                .read(inAppNotificationControllerProvider)
                .showSuccessToast(t.pages.settings.routing.generalOptions.perAppProxy.autoSelection.toast.success);
          }
        } finally {
          subscription.close();
        }
      }
    } catch (e, st) {
      loggy.warning('Automatic app selection failed', e, st);
    } finally {
      _updating = false;
    }
  }
}
