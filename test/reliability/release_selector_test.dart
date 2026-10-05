import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/model/environment.dart';
import 'package:hiddify/features/app_update/data/release_selector.dart';

Map<String, dynamic> release(
  String tag, {
  bool draft = false,
  bool dev = false,
  String asset = 'Hiddify-Android-arm64-v8a.apk',
}) => {
  'tag_name': tag,
  'draft': draft,
  'prerelease': dev,
  'published_at': '2026-10-05T00:00:00Z',
  'html_url': 'https://github.com/SawaMEN/hiddify-app/releases/tag/$tag',
  'assets': [
    {
      'name': asset,
      'browser_download_url':
          'https://github.com/SawaMEN/hiddify-app/releases/download/$tag/$asset',
    },
  ],
};
void main() {
  test('Selects highest stable compatible release, ignoring drafts and malformed tags', () {
    final result = ReleaseSelector.latest([
      release('v9.0.0', draft: true),
      release('v6.0.0.dev', dev: true),
      release('garbage'),
      release('v4.3.0', asset: 'source.tar.gz'),
      release('v4.1.0'),
      release('v4.2.0'),
    ], 'android');
    expect(result!.version, '4.2.0');
  });
  test(
    'Does not offer APK for another platform or unrelated download URLs',
    () {
      expect(ReleaseSelector.latest([release('v4.2.0')], 'linux'), isNull);
      final invalid = release('v4.2.0');
      (invalid['assets'] as List).first['browser_download_url'] =
          'https://example.org/app.apk';
      expect(ReleaseSelector.latest([invalid], 'android'), isNull);
    },
  );
  test('Same semantic version sorts by build number', () {
    expect(
      ReleaseSelector.latest([
        release('v4.2.0+40101'),
        release('v4.2.0+40102'),
      ], 'android')!.buildNumber,
      '40102',
    );
  });
  test(
    'Dev updates stay on the dev channel while allowing its prereleases',
    () {
      final releases = [release('v9.0.0'), release('v4.2.0.dev', dev: true)];
      expect(ReleaseSelector.latest(releases, 'android')!.version, '9.0.0');
      expect(
        ReleaseSelector.latest(releases, 'android', flavor: Environment.dev),
        isNull,
      );
      expect(
        ReleaseSelector.latest(
          releases,
          'android',
          flavor: Environment.dev,
          includePreReleases: true,
        )!.releaseTag,
        'v4.2.0.dev',
      );
    },
  );
}
