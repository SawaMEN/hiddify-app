import 'dart:async';

import 'package:hiddify/features/connection/health/connection_health.dart';
import 'package:hiddify/features/proxy/selection/smart_selection.dart';

import 'package:accessibility_tools/accessibility_tools.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/foundation.dart';
import 'package:material_ui/material_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_hooks/flutter_hooks.dart';
import 'package:hiddify/core/localization/locale_extensions.dart';
import 'package:hiddify/core/localization/locale_preferences.dart';
import 'package:hiddify/core/localization/translations.dart';
import 'package:hiddify/core/model/constants.dart';
import 'package:hiddify/core/router/go_router/go_router_notifier.dart';
import 'package:hiddify/core/router/go_router/helper/active_breakpoint_notifier.dart';
import 'package:hiddify/core/theme/app_theme.dart';
import 'package:hiddify/core/theme/visual_effects.dart';
import 'package:hiddify/core/widget/cyber_background.dart';
import 'package:hiddify/core/theme/theme_preferences.dart';
import 'package:hiddify/features/app_update/notifier/app_update_notifier.dart';
import 'package:hiddify/features/connection/widget/connection_wrapper.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/connection/model/connection_status.dart';
import 'package:hiddify/features/per_app_proxy/overview/per_app_proxy_service_notifier.dart';
import 'package:hiddify/features/profile/notifier/profiles_update_notifier.dart';
import 'package:hiddify/features/shortcut/shortcut_wrapper.dart';
import 'package:hiddify/hiddifycore/hiddify_core_service_provider.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:toastification/toastification.dart';
import 'package:upgrader/upgrader.dart';

bool _debugAccessibility = false;
bool isOnPauseCalled = false;
bool _coreNeedsResume = false;

class App extends HookConsumerWidget with WidgetsBindingObserver, PresLogger {
  const App({super.key});

  void onPause(WidgetRef ref) {
    isOnPauseCalled = true;
    ref.read(appForegroundProvider.notifier).set(false);
    final connection = ref.read(connectionNotifierProvider).value;
    // Permission activities can pause Flutter while the service is starting.
    if (connection is Connecting || connection is Disconnecting) return;
    _coreNeedsResume = true;
    unawaited(
      ref.read(hiddifyCoreServiceProvider).closeFront().catchError((Object error, StackTrace stackTrace) {
        loggy.warning("Unable to suspend core listeners", error, stackTrace);
      }),
    );
  }

  void onResume(WidgetRef ref) {
    ref.read(appForegroundProvider.notifier).set(true);
    if (_coreNeedsResume) {
      _coreNeedsResume = false;
      unawaited(
        ref.read(hiddifyCoreServiceProvider).init().catchError((Object error, StackTrace stackTrace) {
          loggy.warning("Unable to restore core listeners", error, stackTrace);
        }),
      );
    }

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!ref.context.mounted) return;
      if (isOnPauseCalled) ref.invalidate(perAppProxyServiceProvider);
      isOnPauseCalled = false;
    });
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    setupStateListener(ref);
    ref.watch(connectionHealthProvider);
    ref.watch(smartSelectionProvider);
    final router = ref.watch(goRouterNotiferProvider);
    final locale = ref.watch(localePreferencesProvider);
    final themeMode = ref.watch(themePreferencesProvider);
    final theme = useMemoized(() => AppTheme(themeMode, locale.preferredFontFamily), [
      themeMode,
      locale.preferredFontFamily,
    ]);
    final upgrader = ref.watch(upgraderProvider);
    final activeBreakpoint = Breakpoint(context).activeBreakpoint;

    ref.listen(foregroundProfilesUpdateNotifierProvider, (_, _) {});
    ref.listen(perAppProxyServiceProvider, (_, _) {});

    useEffect(() {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!context.mounted) return;
        ref.read(activeBreakpointNotifierProvider.notifier).update(activeBreakpoint);
      });
      return null;
    }, [activeBreakpoint]);

    return ShortcutWrapper(
      ToastificationWrapper(
        child: ConnectionWrapper(
          DynamicColorBuilder(
            builder: (ColorScheme? lightColorScheme, ColorScheme? darkColorScheme) {
              return MaterialApp.router(
                routerConfig: router,
                locale: locale.flutterLocale,
                supportedLocales: AppLocaleUtils.supportedLocales,
                localizationsDelegates: GlobalMaterialLocalizations.delegates,
                debugShowCheckedModeBanner: false,
                themeMode: themeMode.flutterThemeMode,
                theme: theme.lightTheme(lightColorScheme),
                darkTheme: theme.darkTheme(darkColorScheme),
                title: Constants.appName,
                builder: (context, child) {
                  final theme = Theme.of(context);
                  Widget appChild = UpgradeAlert(
                    upgrader: upgrader,
                    navigatorKey: router.routerDelegate.navigatorKey,
                    child: child ?? const SizedBox(),
                  );
                  if (kDebugMode && _debugAccessibility) {
                    appChild = AccessibilityTools(checkFontOverflows: true, child: appChild);
                  }
                  return AnnotatedRegion<SystemUiOverlayStyle>(
                    value: SystemUiOverlayStyle(
                      statusBarColor: Colors.transparent,
                      statusBarIconBrightness: theme.brightness == Brightness.dark ? Brightness.light : Brightness.dark,
                      systemNavigationBarColor: theme.canvasColor,
                      systemNavigationBarIconBrightness: theme.brightness == Brightness.dark
                          ? Brightness.light
                          : Brightness.dark,
                    ),
                    child: VisualEffectsHost(
                      // Third-party widgets still use Flutter's legacy Material types.
                      child: TickerMode(
                        enabled: ref.watch(appForegroundProvider),
                        child: MaterialUiCompatibilityBridge(child: CyberBackground(child: appChild)),
                      ),
                    ),
                  );
                },
              );
            },
          ),
        ),
      ),
    );
  }

  void setupStateListener(WidgetRef ref) {
    final appLifecycleState = useAppLifecycleState();

    useEffect(() {
      loggy.info("current app state");
      loggy.info(appLifecycleState);
      if (appLifecycleState == AppLifecycleState.paused) {
        onPause(ref);
      } else if (appLifecycleState == AppLifecycleState.resumed) {
        onResume(ref);
      }
      return null;
    }, [appLifecycleState]);
  }
}
