import tempfile
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
from generate_appcast import generate

class AppcastTests(unittest.TestCase):
    def test_stable_is_own_arm64_feed(self):
        with tempfile.TemporaryDirectory() as d:
            directory=Path(d)
            (directory/'Hiddify-Android-arm64-v8a.apk').write_bytes(b'test')
            generate(directory,'SawaMEN/hiddify-app','v4.2.0','prod')
            enclosure=ET.parse(directory/'appcast.xml').find('.//enclosure')
            self.assertIn('/SawaMEN/hiddify-app/releases/download/v4.2.0/',enclosure.attrib['url'])
            self.assertEqual(enclosure.attrib['length'],'4')
            self.assertTrue((directory/'SHA256SUMS').exists())
    def test_dev_cannot_publish_stable_feed(self):
        with tempfile.TemporaryDirectory() as d:
            directory=Path(d)
            (directory/'Hiddify-Android-arm64-v8a-dev.apk').write_bytes(b'test')
            generate(directory,'SawaMEN/hiddify-app','dev-latest','dev')
            self.assertFalse((directory/'appcast.xml').exists())
            with self.assertRaises(ValueError):generate(directory,'SawaMEN/hiddify-app','dev-latest','prod')
if __name__=='__main__':unittest.main()
