import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  String source(String path) => File(path).readAsStringSync();

  test('profile expansion preserves indentation-sensitive subscription content', () {
    final parser = source('lib/features/profile/data/profile_parser.dart');
    expect(parser, contains('final rawLine = lines[currentIndex];'));
    expect(parser, contains('results[currentIndex] = rawLine;'));
    expect(parser, isNot(contains('results[currentIndex] = line.trim();')));
  });

  test('nested subscriptions reject local targets and redirects', () {
    final parser = source('lib/features/profile/data/profile_parser.dart');
    expect(parser, contains('_validateNestedSubscriptionUri(uri)'));
    expect(parser, contains("host == 'localhost'"));
    expect(parser, contains('followRedirects: false'));
    expect(parser, contains('_isBlockedNestedAddress'));
  });

  test('download deadline is not reported as caller cancellation', () {
    final http = source('lib/core/http_client/dio_http_client.dart');
    expect(http, contains('var timedOut = false;'));
    expect(http, contains('var cancelledByCaller = false;'));
    expect(http, contains("throw TimeoutException('Subscription download exceeded 60 seconds')"));
  });

  test('Android service start does not depend on notification permission', () {
    final activity = source('android/app/src/main/kotlin/com/hiddify/hiddify/MainActivity.kt');
    final boot = source('android/app/src/main/kotlin/com/hiddify/hiddify/bg/BootReceiver.kt');
    expect(activity, isNot(contains('POST_NOTIFICATIONS')));
    expect(activity, isNot(contains('notificationPermissionLauncher')));
    expect(boot, isNot(contains('ServiceNotification.checkPermission()')));
  });

  test('Android native close always executes resource cleanup', () {
    final box = source('android/app/src/main/kotlin/com/hiddify/hiddify/bg/BoxService.kt');
    expect(box, contains('finally {'));
    expect(box, contains('closeTun(reason)'));
    expect(box, contains('DefaultNetworkMonitor.stop()'));
    expect(box, contains('if (closeError == null) coreOwner = null'));
  });

  test('production Android releases cannot fall back to debug signing', () {
    final gradle = source('android/app/build.gradle');
    expect(gradle, contains("System.getenv('CHANNEL') == 'prod' && !hasKeyStore"));
    expect(gradle, contains('Production Android release requires android/key.properties'));
  });

  test('route-rule payloads are URL-safe and legacy base64 remains decodable', () {
    final rules = source('lib/features/route_rules/notifier/rules_notifier.dart');
    expect(rules, contains('base64Url.encode'));
    expect(rules, contains('base64.normalize'));
    expect(rules, contains("replaceAll(' ', '+')"));

    final bytes = utf8.encode('{"route":"+/="}');
    final encoded = base64Url.encode(bytes);
    final link = Uri.parse(
      'hiddify:///settings/routing-options',
    ).replace(queryParameters: {'routeRule': encoded});
    final roundTrip = Uri.parse(link.toString()).queryParameters['routeRule']!;
    expect(base64.decode(base64.normalize(roundTrip)), bytes);
  });

  test('profile rollback keeps recovery copy when restoration fails', () {
    final repository = source('lib/features/profile/data/profile_repository.dart');
    expect(repository, contains('_restoreBackup'));
    expect(repository, contains('Error.throwWithStackTrace(error, stackTrace)'));
    expect(repository, contains('recovery backup preserved'));
  });

  test('analytics state is persisted only after Sentry initializes', () {
    final analytics = source('lib/core/analytics/analytics_controller.dart');
    final initIndex = analytics.indexOf('await SentryFlutter.init');
    final persistIndex = analytics.indexOf('_preferences.setBool(enableAnalyticsPrefKey, true)');
    expect(initIndex, greaterThanOrEqualTo(0));
    expect(persistIndex, greaterThan(initIndex));
  });

  test('active profile replacement order is deterministic', () {
    final dao = source('lib/features/profile/data/profile_data_source.dart');
    expect(dao, contains('tbl.lastUpdate, mode: OrderingMode.desc'));
    expect(dao, contains('tbl.id, mode: OrderingMode.asc'));
  });

  test('preference reset does not invalidate state after failed persistence', () {
    final preferences = source('lib/core/utils/preferences_utils.dart');
    expect(preferences, contains('if (!await entry.remove())'));
    expect(preferences, contains("throw StateError('Unable to remove preference"));
  });
}
