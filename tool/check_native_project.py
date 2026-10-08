"""Check the standalone Android source/build boundary without running tests."""
from pathlib import Path
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
from check_app_version import declared_version

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
    res = SOURCE / 'res'
    for path in res.rglob('*.xml'):
        ET.parse(path)
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
    # The source variable font defaults to 200. Android must receive actual 400/500/600/700
    # instances, not four declarations of that same default ExtraLight face.
    for suffix, weight in (('regular', 400), ('medium', 500), ('semibold', 600), ('bold', 700)):
        data = (res / 'font' / f'manrope_{suffix}.ttf').read_bytes()
        count = struct.unpack_from('>H', data, 4)[0]
        tables = {}
        for index in range(count):
            tag, _, offset, length = struct.unpack_from('>4sIII', data, 12 + index * 16)
            tables[tag] = (offset, length)
        if b'fvar' in tables or b'OS/2' not in tables:
            raise ValueError(f'Manrope {suffix} must be a static weight instance')
        actual = struct.unpack_from('>H', data, tables[b'OS/2'][0] + 4)[0]
        if actual != weight:
            raise ValueError(f'Manrope {suffix}: expected {weight}, got {actual}')
    manifest = ET.parse(SOURCE / 'AndroidManifest.xml').getroot()
    android = '{http://schemas.android.com/apk/res/android}'
    application = manifest.find('application')
    activity = next((node for node in application.findall('activity') if node.get(android + 'name') == '.MainActivity'), None)
    if activity is None or activity.find('intent-filter') is None:
        raise ValueError('Standalone MainActivity/launcher is missing')
    if any('flutter' in node.get(android + 'name', '').lower() for node in application.findall('meta-data')):
        raise ValueError('Flutter embedding metadata remains')
    version, build = declared_version()
    print(f'Standalone Kotlin project: no Dart/Flutter runtime; resources OK; version {version}+{build}')


if __name__ == '__main__':
    check()
