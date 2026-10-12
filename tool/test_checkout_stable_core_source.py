import json
import pathlib
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from checkout_stable_core_source import identity


class StableSourceTests(unittest.TestCase):
    def test_latest_release_selects_matching_source(self):
        release = {
            "tag_name": "sawamen-123456abcdef-abcdefabcdef-panel8",
            "target_commitish": "123456abcdef" + "1" * 28,
        }
        with patch("checkout_stable_core_source.latest", return_value=(release, {})):
            tag, sha, submodules = identity(False)
        self.assertEqual(tag, release["tag_name"])
        self.assertEqual(sha, release["target_commitish"])
        self.assertIsNone(submodules)

    def test_rejects_stale_source_revision(self):
        release = {
            "tag_name": "sawamen-999999999999-abcdefabcdef-panel8",
            "target_commitish": "123456abcdef" + "1" * 28,
        }
        with patch("checkout_stable_core_source.latest", return_value=(release, {})):
            with self.assertRaisesRegex(ValueError, "disagree"):
                identity(False)

    def test_provenance_checks_sing_box_revision(self):
        provenance = {
            "source": "hiddify-core-stable-release",
            "release_tag": "sawamen-123456abcdef-abcdefabcdef-panel8",
            "core_commit": "123456abcdef" + "1" * 28,
            "sing_box_commit": "a" * 40,
            "ray2sing_commit": "b" * 40,
        }
        with patch("checkout_stable_core_source.ANDROID_LIBS") as folder:
            folder.__truediv__.return_value.read_text.return_value = json.dumps(provenance)
            with self.assertRaisesRegex(ValueError, "Sing-box revision disagrees"):
                identity(True)


if __name__ == "__main__":
    unittest.main()
