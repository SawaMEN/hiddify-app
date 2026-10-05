import 'dart:async';
import 'dart:convert';

import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hiddify/core/preferences/preferences_provider.dart';
import 'package:hiddify/features/profile/notifier/active_profile_notifier.dart';
import 'package:hiddify/features/proxy/overview/proxies_overview_notifier.dart';
import 'package:hiddify/features/proxy/data/proxy_data_providers.dart';
import 'package:hiddify/features/proxy/selection/server_ranker.dart';
import 'package:hiddify/features/connection/health/connection_health.dart';
import 'package:hiddify/hiddifycore/generated/v2/hcore/hcore.pb.dart';

final smartSelectionProvider = NotifierProvider<SmartSelectionNotifier, String?>(SmartSelectionNotifier.new);

class SmartSelectionNotifier extends Notifier<String?> {
  ServerRanker _ranker = ServerRanker();
  String? _profile;
  Timer? _save;
  bool _selecting = false;
  int _generation = 0;
  @override
  String? build() {
    ref.onDispose(() {
      _generation++;
      _save?.cancel();
    });
    ref.listen(activeProfileProvider.select((s) => s.value?.id), (_, next) {
      _load(next);
      state = null;
    });
    ref.listen(Preferences.smartServerSelection, (_, __) {
      _generation++;
      state = null;
    });
    ref.listen(proxiesOverviewNotifierProvider, (_, next) {
      final group = next.value;
      if (group != null) _observe(group);
    });
    _load(ref.read(activeProfileProvider).value?.id);
    return null;
  }

  void _load(String? id) {
    _generation++;
    _save?.cancel();
    _profile = id;
    _ranker = ServerRanker();
    if (id == null) return;
    try {
      final data =
          jsonDecode(ref.read(sharedPreferencesProvider).requireValue.getString('server_health_$id') ?? '{}') as Map;
      final now = DateTime.now().millisecondsSinceEpoch;
      for (final entry in data.entries.take(128)) {
        final sample = ServerSample.fromJson(Map<String, dynamic>.from(entry.value as Map));
        if (now - sample.updated >= 0 && now - sample.updated < 86400000)
          _ranker.samples[entry.key.toString()] = sample;
      }
    } catch (_) {}
  }

  void _observe(OutboundGroup group) {
    if (_profile == null || !ref.read(Preferences.smartServerSelection) || !ref.read(appForegroundProvider)) return;
    final now = DateTime.now().millisecondsSinceEpoch;
    final eligible = <String>{};
    for (final item in group.items.where((i) => !i.isGroup).take(128)) {
      eligible.add(item.tag);
      final timestamp = item.urlTestTime.seconds.toInt() * 1000 + item.urlTestTime.nanos ~/ 1000000;
      if (timestamp <= 0 || timestamp > now + 1000) continue;
      (_ranker.samples[item.tag] ??= ServerSample()).observe(item.urlTestDelay, timestamp);
    }
    _ranker.samples.removeWhere((k, v) => !eligible.contains(k));
    if (_save == null || !_save!.isActive) {
      final id = _profile;
      _save = Timer(const Duration(seconds: 10), () {
        if (!ref.mounted || id != _profile) return;
        final prefs = ref.read(sharedPreferencesProvider).requireValue;
        final profiles = prefs.getStringList('server_health_profiles') ?? <String>[];
        profiles.remove(id);
        profiles.add(id!);
        for (final old in profiles.take((profiles.length - 16).clamp(0, profiles.length)).toList()) {
          profiles.remove(old);
          unawaited(prefs.remove('server_health_$old').catchError((Object e) => false));
        }
        unawaited(prefs.setStringList('server_health_profiles', profiles).catchError((Object e) => false));
        unawaited(
          prefs
              .setString(
                'server_health_$id',
                jsonEncode({for (final e in _ranker.samples.entries) e.key: e.value.toJson()}),
              )
              .catchError((Object e) => false),
        );
      });
    }
    final recommended = _ranker.recommend(group.selected, eligible, now);
    if (recommended == null || _selecting) return;
    _selecting = true;
    final generation = _generation;
    unawaited(
      serializedProxySelection(() async {
        try {
          if (!ref.mounted ||
              generation != _generation ||
              !ref.read(Preferences.smartServerSelection) ||
              !ref.read(appForegroundProvider))
            return;
          final result = await ref.read(proxyRepositoryProvider).selectProxy(group.tag, recommended).run();
          if (!ref.mounted || generation != _generation) return;
          result.match((_) {}, (_) {
            _ranker.switched(DateTime.now().millisecondsSinceEpoch);
            state = recommended;
          });
        } finally {
          _selecting = false;
        }
      }),
    );
  }
}
