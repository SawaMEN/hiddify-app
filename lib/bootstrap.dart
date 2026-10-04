import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_displaymode/flutter_displaymode.dart';
import 'package:flutter_native_splash/flutter_native_splash.dart';
import 'package:hiddify/core/analytics/analytics_controller.dart';
import 'package:hiddify/core/app_info/app_info_provider.dart';
import 'package:hiddify/core/directories/directories_provider.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/logger/logger.dart';
import 'package:hiddify/core/logger/logger_controller.dart';
import 'package:hiddify/core/model/environment.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/core/preferences/preferences_migration.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/features/app/widget/app.dart';
import 'package:hiddify/features/app/widget/startup_app.dart';
import 'package:hiddify/features/chain/model/chain_enum.dart';
import 'package:hiddify/features/chain/notifier/chain_profile_notifier.dart';
import 'package:hiddify/features/log/data/log_data_providers.dart';
import 'package:hiddify/features/profile/data/profile_data_providers.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/features/proxy/active/active_proxy_notifier.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service_provider.dart';
import 'package:hiddify/riverpod_observer.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

Future<void> lazyBootstrap(WidgetsBinding widgetsBinding, Environment env) async {
  FlutterNativeSplash.preserve(widgetsBinding: widgetsBinding);
  LoggerController.preInit();
  FlutterError.onError = Logger.logFlutterError;
  WidgetsBinding.instance.platformDispatcher.onError = Logger.logPlatformDispatcherError;

  runApp(StartupApp(initialization: _initializeApp(env)));
  FlutterNativeSplash.remove();
}

Future<Widget> _initializeApp(Environment env) async {
  final stopWatch = Stopwatch()..start();
  final container = ProviderContainer(
    overrides: [environmentProvider.overrideWithValue(env)],
    observers: [RiverpodObserver()],
  );

  await _init("directories", () => container.read(appDirectoriesProvider.future));
  LoggerController.init(container.read(logPathResolverProvider).appFile().path);

  final appInfo = await _init("app info", () => container.read(appInfoProvider.future));
  await _init("preferences", () => container.read(sharedPreferencesProvider.future));

  final enableAnalytics = await _safeInit(
    "analytics preference",
    () => container.read(analyticsControllerProvider.future),
  );
  if (enableAnalytics == true) {
    await _safeInit(
      "analytics",
      () => container.read(analyticsControllerProvider.notifier).enableAnalytics(),
      timeout: 5000,
    );
  }

  await _init("preferences migration", () async {
    try {
      await PreferencesMigration(sharedPreferences: container.read(sharedPreferencesProvider).requireValue).migrate();
    } catch (e, stackTrace) {
      Logger.bootstrap.error("preferences migration failed", e, stackTrace);
      if (env == Environment.dev) rethrow;
      Logger.bootstrap.info("clearing preferences");
      await container.read(sharedPreferencesProvider).requireValue.clear();
    }
  });

  final debug = container.read(debugModeNotifierProvider) || kDebugMode;
  await _init("logs repository", () => container.read(logRepositoryProvider.future));
  await _init("logger controller", () => LoggerController.postInit(debug));

  Logger.bootstrap.info(appInfo.format());

  await _init("profile repository", () => container.read(profileRepositoryProvider.future));
  await _init("translations", () => container.read(translationsProvider.future));
  await _safeInit("active profile", () => container.read(activeProfileProvider.future), timeout: 1000);
  await _safeInit(
    "chain profile extra security",
    () => container.read(chainProfileNotifierProvider(ChainType.extraSecurity).future),
    timeout: 3000,
  );
  await _safeInit(
    "chain profile unblocker",
    () => container.read(chainProfileNotifierProvider(ChainType.unblocker).future),
    timeout: 3000,
  );
  await _safeInit("hiddify-core", () => container.read(hiddifyCoreServiceProvider).init());

  container.listen(activeProxyNotifierProvider, (previous, next) {});

  await _safeInit("android display mode", () async {
    await FlutterDisplayMode.setHighRefreshRate();
  });

  Logger.bootstrap.info("bootstrap took [${stopWatch.elapsedMilliseconds}ms]");
  stopWatch.stop();

  return UncontrolledProviderScope(
    container: container,
    child: SentryUserInteractionWidget(child: const App()),
  );
}

Future<T> _init<T>(String name, Future<T> Function() initializer, {int timeout = 15000}) async {
  final stopWatch = Stopwatch()..start();
  Logger.bootstrap.info("initializing [$name]");
  Future<T> func() => initializer().timeout(Duration(milliseconds: timeout));
  try {
    final result = await func();
    Logger.bootstrap.debug("[$name] initialized in ${stopWatch.elapsedMilliseconds}ms");
    return result;
  } catch (e, stackTrace) {
    Logger.bootstrap.error("[$name] error initializing", e, stackTrace);
    Error.throwWithStackTrace(StateError('Startup failed at [$name]: $e'), stackTrace);
  } finally {
    stopWatch.stop();
  }
}

Future<T?> _safeInit<T>(String name, Future<T> Function() initializer, {int timeout = 15000}) async {
  try {
    return await _init(name, initializer, timeout: timeout);
  } catch (_) {
    return null;
  }
}
