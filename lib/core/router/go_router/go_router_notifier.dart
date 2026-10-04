import 'package:material_ui/material_ui.dart';
import 'package:go_router/go_router.dart';
import 'package:hiddify/core/router/go_router/refresh_listenable.dart';
import 'package:hiddify/core/router/go_router/routing_config_notifier.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'go_router_notifier.g.dart';

// if 'stateful shell route' navigators not registered, this navigator key can be used for showing dialog or bottom sheet...
final rootNavKey = GlobalKey<NavigatorState>(debugLabel: 'rootNav');

@Riverpod(keepAlive: true)
class GoRouterNotifer extends _$GoRouterNotifer {
  @override
  GoRouter build() {
    final rConfig = ValueNotifier<RoutingConfig>(ref.read(routingConfigNotifierProvider));
    ref.listen(routingConfigNotifierProvider, (_, next) => rConfig.value = next);
    final refreshListenable = RefreshListenable(ref);
    final router = GoRouter.routingConfig(
      initialLocation: '/home',
      navigatorKey: rootNavKey,
      routingConfig: rConfig,
      refreshListenable: refreshListenable,
      errorBuilder: (context, state) {
        return Scaffold(
          body: Center(
            child: TextButton(onPressed: () => context.go('/home'), child: const Text('Return to home')),
          ),
        );
      },
    );
    ref.onDispose(() {
      router.dispose();
      refreshListenable.dispose();
      rConfig.dispose();
    });
    return router;
  }
}
