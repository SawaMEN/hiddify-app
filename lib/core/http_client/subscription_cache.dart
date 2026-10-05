import 'dart:convert';
import 'dart:io';

import 'package:crypto/crypto.dart';
import 'package:dio/dio.dart';
import 'package:hiddify/core/http_client/dio_http_client.dart';

/// Stores only the current HTTP representation, never subscription versions.
abstract class SubscriptionCache {
  static Future<Response> download(
    DioHttpClient client,
    String url,
    String path, {
    String? userAgent,
    CancelToken? cancelToken,
  }) async {
    final directory = Directory('${File(path).parent.path}/http-cache');
    final key = sha256.convert(utf8.encode('$url\n${userAgent ?? client.userAgent}')).toString();
    final body = File('${directory.path}/$key.body');
    final metaFile = File('${directory.path}/$key.json');
    var meta = <String, dynamic>{};
    try {
      if (await body.exists() && await body.length() <= 8 * 1024 * 1024) {
        final decoded = jsonDecode(await metaFile.readAsString());
        if (decoded is Map<String, dynamic> && decoded['headers'] is Map) meta = decoded;
      }
    } catch (_) {
      /* Cache corruption must not prevent a fresh download. */
    }
    final response = await client.download(
      url,
      path,
      userAgent: userAgent,
      cancelToken: cancelToken,
      acceptNotModified: true,
      headers: {
        if (meta['etag'] is String) 'If-None-Match': meta['etag'],
        if (meta['modified'] is String) 'If-Modified-Since': meta['modified'],
      },
    );
    if (response.statusCode == 304) {
      if (meta.isEmpty || !await body.exists()) {
        return downloadFresh(client, url, path, userAgent, cancelToken);
      }
      if (cancelToken?.isCancelled ?? false) throw cancelToken!.cancelError!;
      await body.copy(path);
      final headers = <String, List<String>>{};
      for (final e in (meta['headers'] as Map).entries) {
        if (e.value is List) headers[e.key.toString()] = (e.value as List).map((v) => v.toString()).toList();
      }
      // A 304 can update headers such as traffic quotas without replacing the body.
      headers.addAll(response.headers.map);
      return Response(requestOptions: response.requestOptions, statusCode: 200, headers: Headers.fromMap(headers));
    }
    try {
      await directory.create(recursive: true);
      final temp = File('${body.path}.tmp');
      await File(path).copy(temp.path);
      await temp.rename(body.path);
      await metaFile.writeAsString(
        jsonEncode({
          'etag': response.headers.value('etag'),
          'modified': response.headers.value('last-modified'),
          'headers': response.headers.map,
        }),
      );
      final bodies = await directory.list().where((e) => e is File && e.path.endsWith('.body')).cast<File>().toList();
      final dated = await Future.wait(bodies.map((f) async => (f, (await f.stat()).modified)));
      dated.sort((a, b) => b.$2.compareTo(a.$2));
      for (final old in dated.skip(32)) {
        await old.$1.delete();
        final m = File(old.$1.path.replaceFirst(RegExp(r'\.body$'), '.json'));
        if (await m.exists()) await m.delete();
      }
    } catch (_) {
      /* Optional cache writes never invalidate the live response. */
    }
    return response;
  }

  static Future<Response> downloadFresh(
    DioHttpClient client,
    String url,
    String path,
    String? userAgent,
    CancelToken? token,
  ) => client.download(url, path, userAgent: userAgent, cancelToken: token);
}
