import 'package:flutter/foundation.dart';
import 'package:hiddify/core/analytics/analytics_filter.dart';
import 'package:hiddify/core/analytics/analytics_logger.dart';

import 'package:hiddify/core/logger/logger_controller.dart';
import 'package:hiddify/core/model/environment.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/utils/custom_loggers.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:sentry_flutter/sentry_flutter.dart';
import 'package:shared_preferences/shared_preferences.dart';

part 'analytics_controller.g.dart';

const String enableAnalyticsPrefKey = "enable_analytics";

bool _testCrashReport = false;

@Riverpod(keepAlive: true)
class AnalyticsController extends _$AnalyticsController with AppLogger {
  @override
  Future<bool> build() async {
    return _preferences.getBool(enableAnalyticsPrefKey) ?? false;
  }

  SharedPreferences get _preferences => ref.read(sharedPreferencesProvider).requireValue;

  Future<void> enableAnalytics() async {
    if (state case AsyncData(value: final enabled)) {
      loggy.debug("enabling analytics");
      state = const AsyncLoading();
      final sentryLogger = SentryLoggyIntegration();
      try {
        // final env = ref.read(environmentProvider);
        // final appInfo = await ref.read(appInfoProvider.future);
        final dsn = !kDebugMode || _testCrashReport ? Environment.sentryDSN : "";

        await SentryFlutter.init((options) {
          options.dsn = dsn;
          // options.environment = env.name;
          // options.dist = appInfo.release.name;
          options.debug = kDebugMode;
          options.enableNativeCrashHandling = true;
          options.enableNdkScopeSync = true;
          // options.autoAppStart = false;
          // options.attachScreenshot = true;
          options.serverName = "";
          options.attachThreads = true;
          options.tracesSampleRate = 0;
          options.sendDefaultPii = false;
          options.enableUserInteractionTracing = false;
          options.addIntegration(sentryLogger);
          options.beforeSend = sentryBeforeSend;
        });

        // Do not publish/persist the enabled state until Sentry is fully initialized. Remove a
        // stale printer first in case a previous initialization was interrupted.
        LoggerController.instance.removePrinter("analytics");
        LoggerController.instance.addPrinter("analytics", sentryLogger);
        if (!enabled && !await _preferences.setBool(enableAnalyticsPrefKey, true)) {
          throw StateError('Unable to persist analytics preference');
        }
        state = const AsyncData(true);
      } catch (error, stackTrace) {
        LoggerController.instance.removePrinter("analytics");
        await Sentry.close();
        state = AsyncError(error, stackTrace);
      }
    }
  }

  Future<void> disableAnalytics() async {
    if (state case AsyncData()) {
      loggy.debug("disabling analytics");
      state = const AsyncLoading();
      try {
        if (!await _preferences.setBool(enableAnalyticsPrefKey, false)) {
          throw StateError('Unable to persist analytics preference');
        }
        await Sentry.close();
        LoggerController.instance.removePrinter("analytics");
        state = const AsyncData(false);
      } catch (error, stackTrace) {
        // Privacy preference is written before closing the SDK, so a failed close cannot
        // accidentally re-enable analytics after restart.
        LoggerController.instance.removePrinter("analytics");
        state = AsyncError(error, stackTrace);
      }
    }
  }
}
