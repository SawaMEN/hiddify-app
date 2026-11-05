"""Release graph regression tests: Android dev publication must not await macOS."""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github" / "workflows"


def job(document: str, name: str) -> str:
    match = re.search(
        rf"(?ms)^  {re.escape(name)}:\n(.*?)(?=^  [a-z][A-Za-z0-9_-]*:\n|\Z)",
        document,
    )
    if match is None:
        raise AssertionError(f"Workflow job missing: {name}")
    return match.group(1)


class DevReleaseWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build = (WORKFLOWS / "build.yml").read_text()
        cls.ci = (WORKFLOWS / "ci.yml").read_text()
        cls.mac = (WORKFLOWS / "macos-dev.yml").read_text()

    def test_android_dev_release_does_not_wait_for_macos(self):
        android = job(self.build, "upload-android-dev")
        self.assertIn("- build-android-arm64", android)
        self.assertIn("- analyze", android)
        self.assertIn("- test", android)
        self.assertNotIn("build-macos", android)
        self.assertNotIn("macos-dmg", android)
        self.assertNotIn("VetrOFF-macOS-arm64.dmg", android)
        self.assertIn("inputs.channel == 'dev'", android)
        self.assertIn("inputs.tag-name == 'dev-latest'", android)
        self.assertIn("gh", self.mac)

    def test_stable_and_draft_release_require_both_platforms(self):
        for name in ("upload-release", "update-draft"):
            release = job(self.build, name)
            self.assertIn("- build-android-arm64", release)
            self.assertIn("- build-macos", release)
            self.assertIn("needs.build-macos.result == 'success'", release)
            self.assertIn("VetrOFF-macOS-arm64.dmg", release)
        self.assertIn(
            "!(inputs.channel == 'dev' && inputs.tag-name == 'dev-latest')",
            job(self.build, "build-macos"),
        )

    def test_macos_dev_is_independent_and_does_not_overwrite_apk(self):
        self.assertIn('branches: [main]', self.mac)
        self.assertIn('"macos/**"', self.mac)
        self.assertIn("workflow_dispatch:", self.mac)
        publisher = job(self.mac, "publish-macos-dev")
        self.assertIn("needs: build-macos", publisher)
        self.assertIn("gh release upload dev-latest", publisher)
        self.assertIn("--clobber", publisher)
        self.assertNotIn("android-arm64-v8a", publisher)
        self.assertNotIn("git tag", publisher)
        self.assertNotIn("release edit", publisher)

    def test_only_main_updates_rolling_apk_and_old_runs_cancel(self):
        self.assertIn("cancel-in-progress: true", self.ci)
        self.assertIn("github.ref_name == 'main'", self.ci)
        self.assertNotIn("github.ref_name == 'dev'", self.ci)


if __name__ == "__main__":
    unittest.main()
