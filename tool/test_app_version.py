import unittest

from check_app_version import declared_version, validate_tag


class VersionTests(unittest.TestCase):
    def test_current_release_and_rolling_dev_are_valid(self):
        version, build = declared_version()
        for tag in [f"v{version}", f"v{version}+{build}.prod"]:
            validate_tag(tag, "prod", version, build)
        validate_tag("dev-latest", "dev", version, build)

    def test_wrong_version_build_or_channel_is_rejected(self):
        for tag in ["v4.1.2", "v4.2.0+40102", "v4.2.0.dev", "draft"]:
            with self.assertRaises(ValueError):
                validate_tag(tag, "prod", "4.2.0", "40200")


if __name__ == "__main__":
    unittest.main()
