
import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/core/model/environment.dart';
import 'package:hiddify/core/model/optional_range.dart';
import 'package:hiddify/core/utils/preferences_utils.dart';
import 'package:hiddify/core/utils/profile_metadata.dart';
import 'package:hiddify/features/app_update/data/app_update_repository.dart';
import 'package:hiddify/utils/link_parsers.dart';
import 'package:hiddify/utils/validators.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('URLs support IPv6 and localhost but reject malformed authority and spaces', () {
    for (final url in [
      'https://[2001:db8::1]:65535/a',
      'http://localhost:65001',
      'https://example.com/a%20b',
      'https://127.0.0.1',
    ]) {
      expect(isUrl(url), isTrue, reason: url);
    }
    for (final url in [
      'https://999.1.1.1',
      'https://example.com:65536',
      'https://example.com:0',
      'https://exa mple.com',
      'https://example.com/a b',
      'https://-invalid.example',
      'https://a..b',
    ]) {
      expect(isUrl(url), isFalse, reason: url);
    }
  });

  test('Rule validators accept valid boundaries and reject malformed values', () {
    expect(isPort('65535'), isTrue);
    expect(isPortOrPortRange('65001:65535'), isTrue);
    expect(isPortOrPortRange('80:20'), isFalse);
    expect(isIpCidr('1x2x3x4/24'), isFalse);
    expect(isIpCidr('2001:db8::/128'), isTrue);
    expect(isIpCidr('2001:db8::/129'), isFalse);
    expect(isIpCidr('192.0.2.0/32'), isTrue);
    expect(isIpCidr('192.0.2.0/33'), isFalse);
    expect(isDomain('-bad.example'), isFalse);
    expect(isDomain('good-.example'), isFalse);
    expect(isDomainSuffix('.com'), isTrue);
    expect(isDomainSuffix('com'), isTrue);
    expect(isProcessName('/usr/bin/app'), isFalse);
    expect(isProcessName(r'C:\app.exe'), isFalse);
    expect(isProcessName('Программа.exe'), isTrue);
  });

  test('Empty and malformed nested deep links cannot be imported', () {
    for (final link in ['hiddify://import?url=', 'v2ray://import?url=', 'clash://import?url=not-a-url']) {
      expect(LinkParser.parse(link), isNull);
    }
    const source = 'https://example.com/sub?token=a&name=b';
    final link = Uri(scheme: 'hiddify', host: 'import', queryParameters: {'url': source});
    expect(LinkParser.parse(link.toString())?.url, source);
  });

  test('Ranges reject reversed and negative values', () {
    expect(OptionalRange.tryParse('20-10'), isNull);
    expect(OptionalRange.tryParse('-1'), isNull);
    expect(OptionalRange.parse('10-20').max, 20);
    expect(OptionalRange.parse('', allowEmpty: true).min, isNull);
  });

  test('Profile updates remain observable at second precision', () {
    final old = DateTime.utc(2026, 10, 5, 10);
    expect(nextProfileUpdateTime(old, old.add(const Duration(milliseconds: 100))), old.add(const Duration(seconds: 1)));
    final later = old.add(const Duration(seconds: 10));
    expect(nextProfileUpdateTime(old, later), later);
  });

  test('Content-Disposition handles plain, quoted and UTF-8 filename', () {
    expect(parseContentDispositionFilename('attachment; filename=test.yaml'), 'test.yaml');
    expect(parseContentDispositionFilename('attachment; filename="My profile.yaml"'), 'My profile.yaml');
    expect(
      parseContentDispositionFilename("attachment; filename=fallback; filename*=UTF-8''%D0%A2%D0%B5%D1%81%D1%82.yaml"),
      'Тест.yaml',
    );
    expect(parseContentDispositionFilename('attachment'), isNull);
  });

  test('Nullable preferences clear stored values and lists round-trip without loss', () async {
    SharedPreferences.setMockInitialValues({'nullable': 'old', 'list': ''});
    final prefs = await SharedPreferences.getInstance();
    final nullable = PreferencesEntry<String?, dynamic>(preferences: prefs, key: 'nullable', defaultValue: null);
    expect(await nullable.write(null), isTrue);
    expect(prefs.containsKey('nullable'), isFalse);
    final list = PreferencesEntry<List<String>, dynamic>(preferences: prefs, key: 'list', defaultValue: []);
    expect(list.read(), isEmpty);
    expect(await list.write(['a;b', '', 'second']), isTrue);
    expect(list.read(), ['a;b', '', 'second']);
    final validated = PreferencesEntry<int, dynamic>(
      preferences: prefs,
      key: 'port',
      defaultValue: 80,
      validator: (v) => v > 0 && v <= 65535,
    );
    expect(() => validated.parseRaw(0), throwsFormatException);
    expect(prefs.containsKey('port'), isFalse);
  });

  Map<String, dynamic> release(String tag, {bool prerelease = false, bool draft = false}) => {
    'tag_name': tag,
    'prerelease': prerelease,
    'draft': draft,
    'published_at': '2026-10-01T00:00:00Z',
    'html_url': 'https://example.com/$tag',
  };

  test('Update selection filters flavor, drafts and malformed releases', () {
    final data = [
      release('v9.0.0.dev'),
      release('draft', draft: true),
      release('broken'),
      release('v2.0.0'),
      release('v3.0.0', prerelease: true),
      release('v1.0.0'),
    ];
    expect(selectLatestCompatibleRelease(data).version, '2.0.0');
    expect(selectLatestCompatibleRelease(data, includePreReleases: true).version, '3.0.0');
    expect(selectLatestCompatibleRelease(data, flavor: Environment.dev).version, '9.0.0');
    expect(() => selectLatestCompatibleRelease([release('broken')]), throwsFormatException);
  });

}
