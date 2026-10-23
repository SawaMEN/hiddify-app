"""Keep the uploaded APK/DMG pair; never prune a partial or failed release."""
import json
import os
import subprocess
import sys
from urllib.parse import quote


def obsolete_assets(release, expected):
    assets = release["assets"]
    if len(expected) != 2 or {name.rsplit('.', 1)[-1] for name in expected} != {'apk', 'dmg'}:
        raise ValueError("Expected exactly one APK and one DMG")
    for name in expected:
        matches = [asset for asset in assets if asset['name'] == name]
        if len(matches) != 1 or matches[0].get('state') != 'uploaded' or matches[0].get('size', 0) <= 0:
            raise ValueError(f"Release is incomplete: {name}; existing assets were kept")
    return [asset['id'] for asset in assets if asset['name'] not in expected]


def main():
    tag, *expected = sys.argv[1:]
    repo = os.environ['GITHUB_REPOSITORY']
    endpoint = f"repos/{repo}/releases/tags/{quote(tag, safe='')}"
    release = json.loads(subprocess.check_output(['gh', 'api', endpoint], text=True))
    for asset_id in obsolete_assets(release, expected):
        subprocess.run(['gh', 'api', '--method', 'DELETE',
                        f'repos/{repo}/releases/assets/{asset_id}'], check=True)
    # Check the server result too: fail CI if another publisher added extras.
    final = json.loads(subprocess.check_output(['gh', 'api', endpoint], text=True))
    if obsolete_assets(final, expected):
        raise RuntimeError('Unexpected release assets remain')
    print('Release contains exactly one APK and one DMG.')


if __name__ == '__main__':
    main()
