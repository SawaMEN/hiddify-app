import 'dart:async';

import 'package:dio/dio.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:hiddify/core/http_client/http_client_provider.dart';
import 'package:hiddify/features/connection/notifier/connection_notifier.dart';
import 'package:hiddify/features/proxy/active/active_proxy_notifier.dart';
import 'package:hiddify/features/settings/data/config_option_repository.dart';

enum InternetHealth { unchecked, checking, available, unavailable }

final appForegroundProvider = NotifierProvider<AppForegroundNotifier, bool>(
  AppForegroundNotifier.new,
);

class AppForegroundNotifier extends Notifier<bool> {
  @override
  bool build() => true;
  void set(bool value) => state = value;
}

final connectionHealthProvider =
    NotifierProvider<ConnectionHealthNotifier, InternetHealth>(
      ConnectionHealthNotifier.new,
    );

class ConnectionHealthNotifier extends Notifier<InternetHealth> {
  Timer? _timer;
  Future<void>? _pending;
  CancelToken? _token;
  int _generation = 0, _failures = 0;
  @override
  InternetHealth build() {
    ref.onDispose(_stop);
    ref.listen(serviceRunningProvider, (_, __) => _restart());
    ref.listen(appForegroundProvider, (_, __) => _restart());
    ref.listen(
      activeProxyNotifierProvider.select((s) => s.value?.tag),
      (_, __) => _restart(),
    );
    Future.microtask(() {
      if (ref.mounted) _restart();
    });
    return InternetHealth.unchecked;
  }

  void _stop() {
    _generation++;
    _timer?.cancel();
    _token?.cancel();
  }

  void _restart() {
    _stop();
    _failures = 0;
    state = InternetHealth.unchecked;
    if (ref.read(serviceRunningProvider) && ref.read(appForegroundProvider)) {
      _timer = Timer(const Duration(seconds: 3), () => unawaited(check()));
    }
  }

  Future<void> check() {
    if (_pending != null) return _pending!;
    final future = _check();
    _pending = future;
    return future.whenComplete(() {
      if (identical(_pending, future)) _pending = null;
    });
  }

  Future<void> _check() async {
    if (!ref.mounted) return;
    if (_token != null && !_token!.isCancelled ||
        !ref.read(serviceRunningProvider) ||
        !ref.read(appForegroundProvider))
      return;
    final generation = _generation;
    final token = _token = CancelToken();
    state = InternetHealth.checking;
    final deadline = Timer(
      const Duration(seconds: 12),
      () => token.cancel('Probe timeout'),
    );
    var healthy = false;
    try {
      // Proxy-only: direct fallback would incorrectly mark a broken tunnel healthy.
      final response = await ref
          .read(httpClientProvider)
          .get<dynamic>(
            ref.read(ConfigOptions.connectionTestUrl),
            cancelToken: token,
            proxyOnly: true,
          );
      healthy = validProbeStatus(
        ref.read(ConfigOptions.connectionTestUrl),
        response.statusCode,
      );
    } catch (_) {
    } finally {
      deadline.cancel();
      if (identical(_token, token)) _token = null;
    }
    if (!ref.mounted || generation != _generation) return;
    state = healthy ? InternetHealth.available : InternetHealth.unavailable;
    _failures = healthy ? 0 : _failures + 1;
    if (_failures >= 3 &&
        (ref.read(activeProxyNotifierProvider).value?.urlTestDelay ?? 0) >=
            65000) {
      unawaited(
        ref
            .read(connectionNotifierProvider.notifier)
            .recoverUnhealthyConnection(),
      );
    }
    _timer = Timer(const Duration(seconds: 30), () => unawaited(check()));
  }
}

/// A captive portal HTML page must not satisfy a generate_204 endpoint.
bool validProbeStatus(String url, int? status) {
  final path = Uri.tryParse(url)?.path ?? '';
  final expectsNoContent =
      path.contains('generate_204') || path.contains('generate204');
  return status == 204 || status == 200 && !expectsNoContent;
}
