import 'package:version/version.dart';
import 'package:fpdart/fpdart.dart';

import 'dart:io';

import 'package:hiddify/features/app_update/data/release_selector.dart';
import 'package:hiddify/core/http_client/dio_http_client.dart';
import 'package:hiddify/core/model/constants.dart';
import 'package:hiddify/core/model/environment.dart';
import 'package:hiddify/core/utils/exception_handler.dart';
import 'package:hiddify/features/app_update/model/app_update_failure.dart';
import 'package:hiddify/features/app_update/model/remote_version_entity.dart';
import 'package:hiddify/utils/utils.dart';

abstract interface class AppUpdateRepository {
  TaskEither<AppUpdateFailure, RemoteVersionEntity> getLatestVersion({
    bool includePreReleases = false,
    Release release = Release.general,
    Environment flavor = Environment.prod,
  });
}

class AppUpdateRepositoryImpl with ExceptionHandler, InfraLogger implements AppUpdateRepository {
  AppUpdateRepositoryImpl({required this.httpClient});

  final DioHttpClient httpClient;

  @override
  TaskEither<AppUpdateFailure, RemoteVersionEntity> getLatestVersion({
    bool includePreReleases = false,
    Release release = Release.general,
    Environment flavor = Environment.prod,
  }) {
    return exceptionHandler(() async {
      if (!release.allowCustomUpdateChecker) {
        throw Exception("custom update checkers are not supported");
      }
      final response = await httpClient.get<List>(Constants.githubReleasesApiUrl);
      if (response.statusCode != 200 || response.data == null) {
        loggy.warning("failed to fetch latest version info");
        return left(const AppUpdateFailure());
      }

      final latest = ReleaseSelector.latest(
        response.data!,
        Platform.operatingSystem,
        includePreReleases: includePreReleases,
        flavor: flavor,
      );
      if (latest == null) return left(const AppUpdateFailure());
      return right(latest);
    }, AppUpdateFailure.new);
  }
}

/// Ignore draft, incompatible and malformed releases without hiding valid updates.
RemoteVersionEntity selectLatestCompatibleRelease(
  List<dynamic> data, {
  Environment flavor = Environment.prod,
  bool includePreReleases = false,
}) {
  final releases = <RemoteVersionEntity>[];
  for (final item in data) {
    if (item is! Map<String, dynamic> || item['draft'] == true) continue;
    try {
      final release = GithubReleaseParser.parse(item);
      Version.parse(release.version);
      if (release.flavor == flavor && (includePreReleases || !release.preRelease)) releases.add(release);
    } catch (_) {
      continue;
    }
  }
  if (releases.isEmpty) throw const FormatException('No compatible releases');
  releases.sort((a, b) {
    final version = Version.parse(b.version).compareTo(Version.parse(a.version));
    if (version != 0) return version;
    final build = (int.tryParse(b.buildNumber) ?? 0).compareTo(int.tryParse(a.buildNumber) ?? 0);
    return build != 0 ? build : b.publishedAt.compareTo(a.publishedAt);
  });
  return releases.first;
}
