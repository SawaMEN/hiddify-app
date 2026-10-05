import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/http_client/dio_http_client.dart';
import 'package:hiddify/core/http_client/subscription_cache.dart';

void main() {
  late HttpServer server;
  late Directory directory;
  late DioHttpClient client;
  setUp(() async {
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    directory = await Directory.systemTemp.createTemp('subscription-test-');
    client = DioHttpClient(timeout: const Duration(seconds: 1), userAgent: 'test', debug: false);
  });
  tearDown(() async {
    client.dispose();
    await server.close(force: true);
    await directory.delete(recursive: true);
  });
  test('A conditional 304 restores current body and refreshed quota headers', () async {
    var calls = 0;
    server.listen((request) async {
      calls++;
      if (calls == 1) {
        request.response.headers.set('etag', '"test"');
        request.response.headers.set('subscription-userinfo', 'upload=1;download=2');
        request.response.write('profile-content');
      } else {
        expect(request.headers.value('if-none-match'), '"test"');
        request.response.statusCode = 304;
        request.response.headers.set('subscription-userinfo', 'upload=2;download=3');
      }
      await request.response.close();
    });
    final url = 'http://127.0.0.1:${server.port}/profile';
    await SubscriptionCache.download(client, url, '${directory.path}/first');
    final second = await SubscriptionCache.download(client, url, '${directory.path}/second');
    expect(await File('${directory.path}/second').readAsString(), 'profile-content');
    expect(second.statusCode, 200);
    expect(second.headers.value('subscription-userinfo'), 'upload=2;download=3');
    expect(calls, 2);
  });
  test('A missing cached representation causes a fresh fetch', () async {
    var calls = 0;
    server.listen((request) async {
      calls++;
      request.response.statusCode = calls == 1 ? 304 : 200;
      if (calls > 1) request.response.write('fresh');
      await request.response.close();
    });
    await SubscriptionCache.download(client, 'http://127.0.0.1:${server.port}/profile', '${directory.path}/body');
    expect(await File('${directory.path}/body').readAsString(), 'fresh');
    expect(calls, 2);
  });
  test('Download size limit cancels oversized responses', () async {
    server.listen((r) async {
      r.response.write('123456789');
      await r.response.close();
    });
    await expectLater(
      client.download('http://127.0.0.1:${server.port}/profile', '${directory.path}/body', maxBytes: 4),
      throwsFormatException,
    );
  });
  test('A proxy-only probe never falls back to the direct target', () async {
    var reached = false;
    server.listen((r) async {
      reached = true;
      r.response.write('ok');
      await r.response.close();
    });
    client.setProxyPort(1);
    await expectLater(
      client.get<String>('http://127.0.0.1:${server.port}', proxyOnly: true),
      throwsA(isA<DioException>()),
    );
    expect(reached, false);
  });
}
