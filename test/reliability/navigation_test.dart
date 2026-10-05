import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/core/router/go_router/routing_config_notifier.dart';
import 'package:hiddify/core/router/go_router/helper/active_breakpoint_notifier.dart';

void main() {
  for(final mobile in [true,false]) {
    testWidgets('Navigation keeps named links and branch order (mobile=$mobile)',(tester) async {
      final container=ProviderContainer(overrides:[isMobileBreakpointProvider.overrideWith((ref)=>mobile)]);
      final routes=container.read(routingConfigNotifierProvider).routes;
      final shell=routes.first as StatefulShellRoute;
      final expected=mobile ? ['home','proxies','routingOptions','settings'] : ['home','profiles','proxies','routingOptions','settings','logs','about'];
      expect(shell.branches.map((b)=>(b.routes.first as GoRoute).name).toList(),expected);
      for(var i=0;i<expected.length;i++) {
        expect(getNameOfBranch(mobile,!mobile,i),expected[i]);expect(getIndexOfBranch(mobile,!mobile,expected[i]),i);
      }
      final router=GoRouter(routes:routes);
      expect(router.namedLocation('proxies'),'/home/proxies');
      expect(router.namedLocation('routingOptions'),'/settings/routing-options');
      expect(router.namedLocation('rule',pathParameters:{'orderId':'new'}),'/settings/routing-options/rule/new');
      expect(router.namedLocation('profiles'),mobile ? '/settings/profiles' : '/profiles');
      router.dispose();container.dispose();await tester.pump();
    });
  }
}
