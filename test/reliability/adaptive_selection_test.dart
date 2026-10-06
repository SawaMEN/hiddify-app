import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:fpdart/fpdart.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/features/profile/model/profile_entity.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/features/proxy/data/proxy_data_providers.dart';
import 'package:hiddify/features/proxy/data/proxy_repository.dart';
import 'package:hiddify/features/proxy/model/proxy_failure.dart';
import 'package:hiddify/features/proxy/overview/proxies_overview_notifier.dart';
import 'package:hiddify/features/proxy/selection/smart_selection.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore.pb.dart';

class _Profile extends ActiveProfile {
  @override
  Stream<ProfileEntity?> build() =>
      Stream.value(ProfileEntity.local(id: 'selection', active: true, name: 'test', lastUpdate: DateTime(2026)));
}

class _Repository implements ProxyRepository {
  final selected = <String>[];
  @override
  TaskEither<ProxyFailure, Unit> selectProxy(String groupTag, String outboundTag) => TaskEither(() async {
    selected.add(outboundTag);
    return right(unit);
  });
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('Adaptive selection activates and exits sticky groups with either smart preference', (tester) async {
    for (final keepSmart in [false, true]) {
      SharedPreferences.setMockInitialValues({'adaptive_network': true, 'smart_server_selection': keepSmart});
      final preferences = await SharedPreferences.getInstance();
      final events = StreamController<OutboundGroup?>.broadcast();
      final repository = _Repository();
      final container = ProviderContainer(
        overrides: [
          sharedPreferencesProvider.overrideWith((_) async => preferences),
          activeProfileProvider.overrideWith(_Profile.new),
          proxyRepositoryProvider.overrideWithValue(repository),
          proxyGroupStreamProvider.overrideWith((_) => events.stream),
        ],
      );
      await container.read(sharedPreferencesProvider.future).timeout(const Duration(seconds: 5));
      final subscription = container.listen(smartSelectionProvider, (_, __) {});
      await tester.pump();
      await tester.pump();
      final group = OutboundGroup(
        tag: 'select',
        selected: 'verified',
        items: [
          OutboundInfo(tag: 'lowest', isGroup: true, groupSelectedTag: ''),
          OutboundInfo(tag: 'unknown', urlTestDelay: 0),
          OutboundInfo(tag: 'verified', urlTestDelay: 200),
        ],
      );
      events.add(group);
      await tester.pump();
      await tester.pump();
      expect(repository.selected, ['lowest']);
      events.add(group.deepCopy()..selected = 'lowest');
      await tester.pump();
      await container.read(Preferences.adaptiveNetwork.notifier).update(false);
      await tester.pump();
      if (keepSmart) {
        expect(repository.selected, ['lowest']);
        await container.read(Preferences.smartServerSelection.notifier).update(false);
        await tester.pump();
      }
      expect(repository.selected, ['lowest', 'verified']);
      subscription.close();
      container.dispose();
      unawaited(events.close());
      await tester.pump();
    }
  });
}
