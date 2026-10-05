import 'dart:async';

import 'package:hiddify/features/log/data/log_data_providers.dart';
import 'package:hiddify/features/log/model/log_entity.dart';
import 'package:hiddify/features/log/model/log_level.dart';
import 'package:hiddify/features/log/overview/logs_overview_state.dart';
import 'package:hiddify/hiddifycore/init_signal.dart';
import 'package:hiddify/utils/riverpod_utils.dart';
import 'package:hiddify/utils/utils.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:rxdart/rxdart.dart';

part 'logs_overview_notifier.g.dart';

@riverpod
class LogsOverviewNotifier extends _$LogsOverviewNotifier with AppLogger {
  StreamSubscription? _listener;
  int _generation = 0;
  bool _visible = true;
  Iterable<LogEntity> _logs = [];
  final _debouncer = CallbackDebouncer(const Duration(milliseconds: 200));
  LogLevel? _levelFilter;
  String _filter = '';

  @override
  LogsOverviewState build() {
    ref.disposeDelay(const Duration(seconds: 20));
    ref.watch(coreRestartSignalProvider);
    ref.watch(logRepositoryProvider);
    final generation = ++_generation;
    _visible = true;
    _detach();
    ref.onDispose(() {
      if (generation != _generation) return;
      _generation++;
      _detach();
      _debouncer.dispose();
    });
    ref.onCancel(() {
      _visible = false;
      _detach();
    });
    ref.onResume(() {
      _visible = true;
      if (!state.paused) _attach();
    });
    _attach();
    return LogsOverviewState(filter: _filter, levelFilter: _levelFilter);
  }

  void _detach() {
    final old = _listener;
    _listener = null;
    if (old != null)
      unawaited(
        old.cancel().catchError((Object e, StackTrace st) {
          loggy.warning('Unable to cancel log subscription', e, st);
        }),
      );
  }

  void _attach() {
    if (_listener != null || !_visible || !ref.mounted) return;
    final repository = ref.read(logRepositoryProvider).asData?.value;
    if (repository == null) return;
    final generation = _generation;
    _listener = repository
        .watchLogs()
        .throttleTime(const Duration(milliseconds: 250), leading: false, trailing: true)
        .listen(
          (event) {
            if (!ref.mounted || generation != _generation || !_visible || state.paused) return;
            event.fold((error) => state = state.copyWith(logs: AsyncError(error, StackTrace.current)), (logs) {
              _logs = logs.reversed;
              state = state.copyWith(logs: AsyncData(_computeLogs()));
            });
          },
          onError: (Object e, StackTrace st) {
            if (ref.mounted && generation == _generation) state = state.copyWith(logs: AsyncError(e, st));
          },
        );
  }

  List<LogEntity> _computeLogs() => _logs
      .where(
        (e) =>
            (_filter.isEmpty || e.message.contains(_filter)) &&
            (_levelFilter == null || e.level == null || e.level!.index >= _levelFilter!.index),
      )
      .toList();

  void pause() {
    // Broadcast pause buffers every snapshot. Cancel instead and replay the latest on resume.
    _detach();
    state = state.copyWith(paused: true);
  }

  void resume() {
    state = state.copyWith(paused: false);
    _attach();
  }

  Future<void> clear() async {
    final repository = ref.read(logRepositoryProvider).requireValue;
    final generation = _generation;
    final result = await repository.clearLogs().run();
    if (!ref.mounted || generation != _generation) return;
    result.match((e) => loggy.warning('Unable to clear logs', e), (_) {
      _logs = [];
      state = state.copyWith(logs: const AsyncData([]));
    });
  }

  void filterMessage(String? filter) {
    _filter = filter ?? '';
    final generation = _generation;
    _debouncer(() {
      if (ref.mounted && generation == _generation) {
        state = state.copyWith(filter: _filter, logs: AsyncData(_computeLogs()));
      }
    });
  }

  Future<void> filterLevel(LogLevel? level) async {
    _levelFilter = level;
    state = state.copyWith(levelFilter: level, logs: AsyncData(_computeLogs()));
  }
}
