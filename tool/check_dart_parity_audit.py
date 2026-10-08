#!/usr/bin/env python3
"""Validate the 200-item source audit and generated wording; this does not render Android."""
import collections
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def check():
    audit = json.loads((ROOT / 'docs/KOTLIN_DART_200_DIFFS.json').read_text())
    rows = audit['differences']
    assert len(rows) == 200
    assert [r['id'] for r in rows] == [f'D{i:03}' for i in range(1, 201)]
    assert len({(r['group'], r['expected']) for r in rows}) == 200
    original = set(subprocess.check_output(['git', 'ls-tree', '-r', '--name-only', audit['dartReference']], cwd=ROOT, text=True).splitlines())
    for row in rows:
        assert row['dart'] in original, row
        if row['status'] == 'retired':
            assert row.get('retirementReason'), row
            assert not (ROOT / row['kotlin']).exists(), row
            assert row['verification'] == 'unused resource removed; history retained', row
        else:
            assert (ROOT / row['kotlin']).is_file(), row
            assert row['status'] == 'implemented'
            assert row['verification'] == 'source; device pending'
    content = (ROOT / 'android/app/src/main/kotlin/com/hiddify/hiddify/nativeui/NativeWifiGuideContent.kt').read_text()
    literals = {json.loads(m.group()) for m in re.finditer(r'"(?:\\.|[^"\\])*"', content)}
    for row in rows[:74]:
        assert row['expected'] in literals, row['id']
    app = (ROOT / 'android/app/src/main/kotlin/com/hiddify/hiddify/nativeui/NativeApp.kt').read_text()
    constants = set(re.findall(r'private const val (PAGE_[A-Z_]+)', app))
    assert set(re.findall(r'\bPAGE_[A-Z_]+\b', app)) <= constants
    print('Audit: 200 unique differences; references, target files, history, original guide text and route constants OK')
    print(dict(collections.Counter(row['group'] for row in rows)))

if __name__ == '__main__': check()
