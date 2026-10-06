import 'package:flutter_test/flutter_test.dart';
import 'package:hiddify/utils/uri_utils.dart';
import 'package:url_launcher_platform_interface/url_launcher_platform_interface.dart';

class _FailingLauncher extends UrlLauncherPlatform {
  @override
  Null get linkDelegate => null;

  @override
  Future<bool> canLaunch(String url) async => true;

  @override
  Future<bool> launchUrl(String url, LaunchOptions options) =>
      Future<bool>.error(StateError('native launcher failed asynchronously'));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('An asynchronous native launch failure returns false', () async {
    final original = UrlLauncherPlatform.instance;
    UrlLauncherPlatform.instance = _FailingLauncher();
    addTearDown(() => UrlLauncherPlatform.instance = original);
    expect(await UriUtils.tryLaunch(Uri.parse('https://example.com')), isFalse);
  });
}
