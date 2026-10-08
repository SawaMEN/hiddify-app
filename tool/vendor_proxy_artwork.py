"""Convert the pinned Dart flags and provider artwork to offline Android vectors.

Usage: python tool/vendor_proxy_artwork.py /path/to/flutter_circle_flags /path/to/simple-icons /path/to/fluentui-system-icons
The inputs must be checked out at the commits below. No network/runtime dependency is added.
"""
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
FLAGS_COMMIT = '19d83cba60de91143491a441b5076583bf1681a8'
FLUENT_COMMIT = 'cd332ec1f90cda2fad16f86754f8da52f1e9ab30'
ICONS_COMMIT = '98820a4dc8c363ca72fa2c0d294ea4a0a9bba75d'
ANDROID = 'http://schemas.android.com/apk/res/android'
ET.register_namespace('android', ANDROID)
DRAWABLE = ROOT / 'android/app/src/main/res/drawable'


def attr(**values):
    return {f'{{{ANDROID}}}{k}': str(v) for k, v in values.items()}


def shape(node):
    tag = node.tag.split('}')[-1]
    a = node.attrib
    if tag == 'path':
        return a['d']
    if tag in ('circle', 'ellipse'):
        x, y = float(a['cx']), float(a['cy'])
        rx = float(a['r'] if tag == 'circle' else a['rx'])
        ry = float(a['r'] if tag == 'circle' else a['ry'])
        return f'M{x-rx},{y}a{rx},{ry} 0 1,0 {2*rx},0a{rx},{ry} 0 1,0 {-2*rx},0z'
    if tag == 'rect':
        x, y = float(a.get('x', 0)), float(a.get('y', 0))
        w, h = float(a['width']), float(a['height'])
        rx = min(float(a.get('rx', a.get('ry', 0))), w / 2)
        ry = min(float(a.get('ry', a.get('rx', 0))), h / 2)
        if not rx or not ry:
            return f'M{x},{y}h{w}v{h}h{-w}z'
        return (f'M{x+rx},{y}h{w-2*rx}a{rx},{ry} 0 0,1 {rx},{ry}v{h-2*ry}'
                f'a{rx},{ry} 0 0,1 {-rx},{ry}h{-w+2*rx}a{rx},{ry} 0 0,1 {-rx},{-ry}'
                f'v{-h+2*ry}a{rx},{ry} 0 0,1 {rx},{-ry}z')
    raise ValueError(f'Unsupported SVG shape: {tag}')


def color(value):
    if value == 'none':
        return '#00000000'
    value = {'white': '#ffffff', 'black': '#000000'}.get(value, value)
    if re.fullmatch(r'#[0-9a-fA-F]{3}', value):
        value = '#' + ''.join(c * 2 for c in value[1:])
    if not re.fullmatch(r'#[0-9a-fA-F]{6}', value):
        raise ValueError(f'Unsupported SVG color: {value}')
    return value


def convert(source, destination, monochrome=False):
    svg = ET.parse(source).getroot()
    _, _, w, h = svg.attrib['viewBox'].split()
    vector = ET.Element('vector', attr(width='48dp', height='48dp', viewportWidth=w, viewportHeight=h))
    definitions = {e.attrib['id']: e for e in svg.iter() if 'id' in e.attrib}

    def visit(node, parent, inherited='black'):
        tag = node.tag.split('}')[-1]
        if tag in ('defs', 'mask', 'clipPath', 'title'):
            return
        fill = node.attrib.get('fill', inherited)
        if tag in ('svg', 'g'):
            group = ET.SubElement(parent, 'group')
            transform = node.attrib.get('transform')
            if 'style' in node.attrib:
                # The Dart Iranian lion flag uses CSS translation, not a transform attribute.
                match = re.search(r'transform:\s*translate\(([^)]+)\)', node.attrib['style'])
                if not match:
                    raise ValueError('Unsupported SVG style')
                transform = 'translate(' + match[1].replace('px', '') + ')'
            if transform:
                match = re.fullmatch(r'translate\(([^)]+)\)', transform)
                if not match:
                    raise ValueError(f'Unsupported SVG transform: {transform}')
                values = re.split(r'[,\s]+', match[1].strip())
                group.attrib.update(attr(translateX=values[0], translateY=values[1] if len(values) > 1 else '0'))
            for key in ('clip-path', 'mask'):
                if key in node.attrib:
                    name = node.attrib[key].removeprefix('url(#').removesuffix(')')
                    definition = definitions[name]
                    # All masks in the pinned input are opaque white shapes (hard clips).
                    if key == 'mask' and any(color(e.attrib.get('fill', 'black')) != '#ffffff' for e in definition):
                        raise ValueError('Non-opaque mask requires a different conversion')
                    ET.SubElement(group, 'clip-path', attr(pathData=' '.join(shape(e) for e in definition)))
            for child in node:
                visit(child, group, fill)
        else:
            attributes = attr(pathData=shape(node), fillColor='#ffffff' if monochrome else color(fill))
            if node.attrib.get('fill-rule') == 'evenodd':
                attributes.update(attr(fillType='evenOdd'))
            ET.SubElement(parent, 'path', attributes)

    visit(svg, vector)
    ET.indent(vector, space='    ')
    destination.write_text(ET.tostring(vector, encoding='unicode') + '\n')


def main():
    flags, icons, fluent = map(Path, sys.argv[1:])
    for path, expected in ((flags, FLAGS_COMMIT), (icons, ICONS_COMMIT), (fluent, FLUENT_COMMIT)):
        actual = subprocess.check_output(['git', '-C', str(path), 'rev-parse', 'HEAD'], text=True).strip()
        if actual != expected:
            raise ValueError(f'{path} must be at {expected}, got {actual}')
    entries = []
    for source in sorted((flags / 'assets/svg').glob('*.svg')):
        name = 'native_flag_' + source.stem.replace('-', '_')
        convert(source, DRAWABLE / (name + '.xml'))
        entries.append(f'    "{source.stem}" to R.drawable.{name},')
    target = ROOT / 'android/app/src/main/kotlin/com/hiddify/hiddify/nativeui/NativeFlagDrawables.kt'
    target.write_text('// Generated by tool/vendor_proxy_artwork.py; do not edit.\n'
                      'package com.hiddify.hiddify.nativeui\n\nimport com.hiddify.hiddify.R\n\n'
                      'internal val NativeFlagDrawables = mapOf(\n' + '\n'.join(entries) + '\n)\n')
    for name in ['cloudflare', 'hetzner', 'ovh', 'fastly', 'digitalocean', 'alibabacloud', 'googlecloud', 'satellite']:
        convert(icons / 'icons' / (name + '.svg'), DRAWABLE / ('native_provider_' + name + '.xml'), monochrome=True)
    for folder, name, target in [('Question Circle', 'question_circle_20_regular', 'question_circle'), ('WiFi 1', 'wifi_1_24_regular', 'wifi_signal')]:
        convert(fluent / 'assets' / folder / 'SVG' / ('ic_fluent_' + name + '.svg'),
                DRAWABLE / ('native_' + target + '.xml'), monochrome=True)
    licenses = ROOT / 'android/app/src/main/assets/licenses'
    licenses.mkdir(parents=True, exist_ok=True)
    (licenses / 'circle-flags-MIT.txt').write_text((flags / 'LICENSE').read_text())
    (licenses / 'simple-icons-CC0.txt').write_text((icons / 'LICENSE.md').read_text())
    (licenses / 'fluent-system-icons-MIT.txt').write_text((fluent / 'LICENSE').read_text())
    print(f'Converted {len(entries)} Dart country/region flags and 8 provider logos')


if __name__ == '__main__':
    main()
