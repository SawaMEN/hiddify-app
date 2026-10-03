import 'dart:async';

import 'package:hooks_riverpod/hooks_riverpod.dart';

export 'package:hooks_riverpod/legacy.dart' show StateProvider;

extension RefLifeCycle on Ref {
  void disposeDelay(Duration duration) {
    final link = keepAlive();
    Timer? timer;

    onCancel(() {
      timer?.cancel();
      timer = Timer(duration, link.close);
    });

    onDispose(() {
      timer?.cancel();
    });

    onResume(() {
      timer?.cancel();
    });
  }
}
