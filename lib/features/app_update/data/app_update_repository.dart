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
  });
}

class AppUpdateRepositoryImpl with ExceptionHandler, InfraLogger implements AppUpdateRepository {
  AppUpdateRepositoryImpl({required this.httpClient});

  final DioHttpClient httpClient;

  @override
  TaskEither<AppUpdateFailure, RemoteVersionEntity> getLatestVersion({
    bool includePreReleases = false,
    Release release = Release.general,
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
      );
      if (latest == null) return left(const AppUpdateFailure());
      return right(latest);
    }, AppUpdateFailure.new);
  }
}
