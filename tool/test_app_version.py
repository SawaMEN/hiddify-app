import unittest

from check_app_version import declared_version, validate_tag


class VersionTests(unittest.TestCase):
    def test_current_release_and_rolling_dev_are_valid(self):
        version, build = declared_version()
        for tag in [f"v{version}", f"v{version}+{build}.prod"]:
            validate_tag(tag, "prod", version, build)
        validate_tag("dev-latest", "dev", version, build)

    def test_wrong_version_build_or_channel_is_rejected(self):
        for tag in ["v4.1.2", "v1.0.1+40102", "v1.0.1.dev", "draft"]:
            with self.assertRaises(ValueError):
                validate_tag(tag, "prod", "1.0.1", "40201")


if __name__ == "__main__":
    unittest.main()
