import 'dart:async';

import 'package:hiddify/hiddifycore/hiddify_core_service.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'hiddify_core_service_provider.g.dart';

// The native core has one lifecycle per app, shared by connection/recovery and
// the UI. Declaring scoped dependencies here incorrectly makes this global
// owner scoped and rejects reads from the connection coordinator in debug mode.
@Riverpod(keepAlive: true)
HiddifyCoreService hiddifyCoreService(Ref ref) {
  final service = HiddifyCoreService(ref);
  ref.onDispose(
    () => unawaited(
      service.dispose().catchError((Object error, StackTrace stackTrace) {
        service.loggy.warning("Unable to dispose core channels", error, stackTrace);
      }),
    ),
  );
  return service;
}
