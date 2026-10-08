"""Check Android resource references and reachability without an APK build.

Includes every resource qualifier and follows XML references transitively. Kotlin's
explicit flag map is scanned like any other source; platform resources are excluded.
"""
from collections import defaultdict
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'android/app/src'
RES = SOURCE / 'main/res'
XML_REF = re.compile(r'@(?:\+)?([a-zA-Z_]+)/([\w.]+)')
CODE_REF = re.compile(r'(?<!android\.)\bR\.(\w+)\.(\w+)')


def resource_graph(source=SOURCE, res=RES):
    definitions = defaultdict(list)
    for path in res.rglob('*'):
        if not path.is_file():
            continue
        kind = path.parent.name.split('-')[0]
        if kind == 'values':
            for node in ET.parse(path).getroot():
                name = node.get('name')
                if name is None:
                    continue
                resource_type = node.get('type', node.tag)
                if resource_type in ('string-array', 'integer-array'):
                    resource_type = 'array'
                definitions[(resource_type, name)].append((path, ET.tostring(node, encoding='unicode')))
        else:
            definitions[(kind, path.stem)].append((path, path.read_text() if path.suffix == '.xml' else ''))
    references = set()
    for path in source.rglob('*.kt'):
        references.update(CODE_REF.findall(path.read_text()))
    for path in source.rglob('AndroidManifest.xml'):
        references.update(XML_REF.findall(path.read_text()))
    missing = references - definitions.keys()
    reachable = set(references)
    pending = list(references)
    while pending:
        key = pending.pop()
        for path, text in definitions.get(key, []):
            for reference in XML_REF.findall(text):
                if reference not in definitions:
                    missing.add(reference)
                if reference not in reachable:
                    reachable.add(reference)
                    pending.append(reference)
    return definitions, missing, definitions.keys() - reachable


def check():
    definitions, missing, unused = resource_graph()
    if missing:
        raise ValueError('Missing resources: ' + ', '.join(f'{kind}/{name}' for kind, name in sorted(missing)))
    if unused:
        raise ValueError('Unreferenced resources: ' + ', '.join(f'{kind}/{name}' for kind, name in sorted(unused)))
    print(f'Android resource graph: {len(definitions)} reachable resource names; references OK')


if __name__ == '__main__':
    check()
