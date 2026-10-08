"""Check the standalone Android source/build boundary without running tests."""
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
from check_app_version import declared_version
from check_native_resources import check as check_resources

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'android/app/src/main'


def check():
    paths = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], cwd=ROOT).decode().split('\0')
    dart = [name for name in paths if name.endswith('.dart') and (ROOT / name).is_file()]
    if dart:
        raise ValueError(f'Dart sources remain: {dart[:5]}')
    for path in SOURCE.rglob('*'):
        if not path.is_file():
            continue
        if path.suffix == '.java':
            raise ValueError(f'Application Java source must be Kotlin: {path.relative_to(ROOT)}')
        if path.suffix == '.kt' and re.search(r'^import io\.flutter\.', path.read_text(), re.M):
            raise ValueError(f'Flutter runtime dependency: {path.relative_to(ROOT)}')
    for path in [ROOT / 'android/settings.gradle', ROOT / 'android/app/build.gradle']:
        if 'dev.flutter.' in path.read_text():
            raise ValueError(f'Flutter build plugin: {path.relative_to(ROOT)}')
    validator = (SOURCE / 'kotlin/com/hiddify/hiddify/nativecore/NativeProfileValidator.kt').read_text()
    if 'import com.hiddify.core.mobile.Mobile' not in validator:
        raise ValueError('Profile validator must use the gomobile -javapkg=com.hiddify.core namespace')
    res = SOURCE / 'res'
    for path in res.rglob('*.xml'):
        ET.parse(path)
    # Material SVG viewport guides must never become painted paths. Compose Icon
    # tints every opaque pixel, so one such path turns the whole icon into a square.
    android_attr = '{http://schemas.android.com/apk/res/android}'
    for path in (res / 'drawable').glob('*.xml'):
        if path.name.startswith(('native_flag_', 'ic_')):
            continue  # Flags and launcher/cover artwork have intentional backgrounds.
        vector = ET.parse(path).getroot()
        if vector.tag != 'vector':
            continue
        width = vector.get(android_attr + 'viewportWidth')
        height = vector.get(android_attr + 'viewportHeight')
        guide = f'M00h{width}v{height}'
        rectangle = re.escape(guide) + r'(?:H0(?:V0)?|h-' + re.escape(str(width)) + r')z'
        for node in vector.iter('path'):
            data = re.sub(r'-?\d+(?:\.\d+)?', lambda m: f'{float(m[0]):g}',
                          node.get(android_attr + 'pathData', ''))
            data = re.sub(r'[\s,]', '', data)
            if re.fullmatch(rectangle + '(?:' + rectangle.replace('M00', 'm00') + ')*', data):
                fill = node.get(android_attr + 'fillColor', '#00000000')
                if fill != '#00000000' and node.get(android_attr + 'fillAlpha') != '0':
                    raise ValueError(f'Opaque SVG viewport guide hides icon: {path.name}')
    keys = set()
    for path in (res / 'values').glob('*.xml'):
        keys.update(node.attrib['name'] for node in ET.parse(path).getroot() if node.tag == 'string')
    for directory in ['values', 'values-ru']:
        names = [node.attrib['name'] for node in ET.parse(res / directory / 'strings_native.xml').getroot()]
        if len(names) != len(set(names)):
            raise ValueError(f'Duplicate string: {directory}')
        if directory == 'values':
            english = set(names)
        elif english != set(names):
            raise ValueError('English/Russian string keys differ')
    for path in SOURCE.rglob('*.kt'):
        missing = set(re.findall(r'R\.string\.(native_\w+)', path.read_text())) - keys
        if missing:
            raise ValueError(f'Missing strings in {path.name}: {sorted(missing)}')
        for kind in ('drawable', 'font'):
            available = {item.stem for directory in res.glob(kind + '*') for item in directory.iterdir() if item.is_file()}
            missing = set(re.findall(r'R\.' + kind + r'\.(\w+)', path.read_text())) - available
            if missing:
                raise ValueError(f'Missing {kind} in {path.name}: {sorted(missing)}')
    manifest = ET.parse(SOURCE / 'AndroidManifest.xml').getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    application = manifest.find('application')
    activity = next((node for node in application.findall('activity') if node.get(android + 'name') == '.MainActivity'), None)
    if activity is None or activity.find('intent-filter') is None:
        raise ValueError('Standalone MainActivity/launcher is missing')
    if any('flutter' in node.get(android + 'name', '').lower() for node in application.findall('meta-data')):
        raise ValueError('Flutter embedding metadata remains')
    check_resources()
    version, build = declared_version()
    print(f'Standalone Kotlin project: no Dart/Flutter runtime; resources OK; version {version}+{build}')


if __name__ == '__main__':
    check()
