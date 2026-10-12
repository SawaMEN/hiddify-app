import importlib.util
import pathlib
import unittest
from unittest.mock import patch

file = pathlib.Path(__file__).with_name("fetch_stable_android_core.py")
spec = importlib.util.spec_from_file_location("stable_android_core", file)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class StableAndroidCoreTests(unittest.TestCase):
    def test_rejects_prerelease(self):
        with patch.object(module, "download", return_value=b'{"tag_name":"sawamen-abcdef012345-abcdef012345-panel8","prerelease":true,"draft":false,"published_at":"2026-10-13"}'):
            with self.assertRaisesRegex(ValueError, "not stable"):
                module.latest()

    def test_rejects_missing_android_asset(self):
        with patch.object(module, "download", return_value=b'{"tag_name":"sawamen-abcdef012345-abcdef012345-panel8","prerelease":false,"draft":false,"published_at":"2026-10-13","assets":[]}'):
            with self.assertRaisesRegex(ValueError, "lacks Android ARM64"):
                module.latest()

    def test_latest_uses_official_endpoint(self):
        self.assertEqual(module.API, "https://api.github.com/repos/SawaMEN/hiddify-core/releases/latest")

    def test_rejects_bad_checksum(self):
        import tempfile
        with tempfile.TemporaryDirectory() as folder:
            p = pathlib.Path(folder) / module.ASSET
            p.write_bytes(b"untrusted")
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                module.verify_sidecar(p, ("0"*64) + "  " + module.ASSET, module.ASSET)


if __name__ == "__main__":
    unittest.main()
