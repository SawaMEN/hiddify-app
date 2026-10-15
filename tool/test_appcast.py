import tempfile
import hashlib
import json
from unittest.mock import patch
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from generate_appcast import generate
from check_app_version import declared_version

class AppcastTests(unittest.TestCase):
    def test_stable_is_own_arm64_feed(self):
        with tempfile.TemporaryDirectory() as d:
            directory=Path(d)
            (directory/'vetroff-Android-arm64-v8a.apk').write_bytes(b'test')
            tag = 'v' + declared_version()[0]
            generate(directory,'SawaMEN/hiddify-app',tag,'prod')
            enclosure=ET.parse(directory/'appcast.xml').find('.//enclosure')
            self.assertIn(f'/SawaMEN/hiddify-app/releases/download/{tag}/',enclosure.attrib['url'])
            minimum = ET.parse(directory/'appcast.xml').find('.//{http://www.andymatuschak.org/xml-namespaces/sparkle}minimumSystemVersion')
            self.assertEqual(minimum.text, '10.0.0')
            self.assertEqual(enclosure.attrib['length'],'4')
            self.assertTrue((directory/'SHA256SUMS').exists())
    def test_update_manifest_identifies_rolling_dev_and_binds_apk_checksum(self):
        with tempfile.TemporaryDirectory() as d, patch.dict('os.environ', {'GITHUB_SHA': 'a' * 40}):
            directory = Path(d)
            name = 'vetroff-Android-arm64-v8a-dev.apk'
            (directory/name).write_bytes(b'apk contents')
            generate(directory,'SawaMEN/hiddify-app','dev-latest','dev')
            metadata = json.loads((directory/'update.json').read_text())
            self.assertEqual(metadata['commit'], 'a' * 40)
            self.assertEqual(metadata['channel'], 'dev')
            self.assertEqual(metadata['buildNumber'], int(declared_version()[1]))
            self.assertEqual(metadata['assets'], [{'name': name, 'size': 12,
                'sha256': hashlib.sha256(b'apk contents').hexdigest()}])

    def test_dev_cannot_publish_stable_feed(self):
        with tempfile.TemporaryDirectory() as d:
            directory=Path(d)
            (directory/'vetroff-Android-arm64-v8a-dev.apk').write_bytes(b'test')
            generate(directory,'SawaMEN/hiddify-app','dev-latest','dev')
            self.assertFalse((directory/'appcast.xml').exists())
            with self.assertRaises(ValueError):generate(directory,'SawaMEN/hiddify-app','dev-latest','prod')
if __name__=='__main__':unittest.main()
