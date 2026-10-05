import 'package:hiddify/core/model/environment.dart';
import 'package:hiddify/features/app_update/data/github_release_parser.dart';
import 'package:hiddify/features/app_update/model/remote_version_entity.dart';
import 'package:version/version.dart';

abstract class ReleaseSelector {
  static RemoteVersionEntity? latest(
    List<dynamic> raw,
    String platform, {
    bool includePreReleases = false,
    Environment flavor = Environment.prod,
  }) {
    final candidates = <RemoteVersionEntity>[];
    for (final release in raw) {
      if (release is! Map<String, dynamic> ||
          release['draft'] == true ||
          release['prerelease'] == true && !includePreReleases)
        continue;
      try {
        final parsed = GithubReleaseParser.parse(release);
        Version.parse(parsed.version);
        if (parsed.flavor != flavor) continue;
        final assets = release['assets'];
        if (assets is! List ||
            !assets.any((a) => a is Map && compatibleAsset(a, platform)))
          continue;
        final url = Uri.tryParse(parsed.url);
        if (url?.scheme != 'https' ||
            url?.host != 'github.com' ||
            !url!.path.startsWith('/SawaMEN/hiddify-app/releases/'))
          continue;
        candidates.add(parsed);
      } catch (_) {
        /* Ignore malformed or incompatible releases. */
      }
    }
    candidates.sort((a, b) {
      final version = Version.parse(b.version)
          .compareTo(Version.parse(a.version));
      return version != 0
          ? version
          : (int.tryParse(b.buildNumber) ?? 0).compareTo(
              int.tryParse(a.buildNumber) ?? 0,
            );
    });
    return candidates.isEmpty ? null : candidates.first;
  }

  static bool compatibleAsset(Map asset, String platform) {
    final name = (asset['name'] ?? '').toString().toLowerCase();
    final url = Uri.tryParse((asset['browser_download_url'] ?? '').toString());
    if (url?.scheme != 'https' ||
        url?.host != 'github.com' ||
        !url!.path.startsWith('/SawaMEN/hiddify-app/releases/download/'))
      return false;
    return switch (platform) {
      'android' => name.endsWith('.apk') && name.contains('arm64'),
      'linux' =>
        name.endsWith('.appimage') ||
            name.endsWith('.deb') ||
            name.endsWith('.rpm'),
      'windows' => name.endsWith('.exe') || name.endsWith('.msix'),
      'macos' => name.endsWith('.dmg') || name.endsWith('.pkg'),
      _ => false,
    };
  }
}
