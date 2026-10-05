import 'dart:io';

import 'package:hiddify/core/privacy/redactor.dart';

import 'package:dio/dio.dart';
import 'package:hiddify/core/model/failures.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

FutureOr<SentryEvent?> sentryBeforeSend(SentryEvent event, Hint hint) async {
  if (!canSendEvent(event.throwable)) return null;
  return event.copyWith(
    user: SentryUser(ipAddress: "0.0.0.0"),
    request: SentryRequest(),
    message: event.message == null ? null : SentryMessage(DiagnosticRedactor.text(event.message!.formatted)),
    throwable: event.throwable == null ? null : DiagnosticRedactor.text(event.throwable.toString()),
    exceptions: event.exceptions
        ?.map((e) => SentryException.fromJson(Map<String, dynamic>.from(DiagnosticRedactor.value(e.toJson()) as Map)))
        .toList(),
    threads: event.threads
        ?.map((e) => SentryThread.fromJson(Map<String, dynamic>.from(DiagnosticRedactor.value(e.toJson()) as Map)))
        .toList(),
    contexts: Contexts.fromJson(Map<String, dynamic>.from(DiagnosticRedactor.value(event.contexts.toJson()) as Map)),
    extra: Map<String, dynamic>.from(DiagnosticRedactor.value(event.extra ?? {}) as Map),
    tags: {
      for (final e in (DiagnosticRedactor.value(event.tags ?? <String, String>{}) as Map).entries)
        e.key.toString(): e.value.toString(),
    },
    transaction: event.transaction == null ? null : DiagnosticRedactor.text(event.transaction!),
    breadcrumbs: event.breadcrumbs
        ?.map(
          (b) => Breadcrumb(
            timestamp: b.timestamp,
            type: b.type,
            level: b.level,
            category: b.category,
            message: b.message == null ? null : DiagnosticRedactor.text(b.message!),
            data: Map<String, dynamic>.from(DiagnosticRedactor.value(b.data ?? {}) as Map),
          ),
        )
        .toList(),
  );
}

bool canSendEvent(dynamic throwable) {
  return switch (throwable) {
    UnexpectedFailure(:final error) => canSendEvent(error),
    DioException _ => false,
    SocketException _ => false,
    HttpException _ => false,
    HandshakeException _ => false,
    ExpectedFailure _ => false,
    ExpectedMeasuredFailure _ => false,
    _ => true,
  };
}

bool canLogEvent(dynamic throwable) => switch (throwable) {
  ExpectedMeasuredFailure _ => true,
  _ => canSendEvent(throwable),
};
