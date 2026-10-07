"""Keep release tags, Android metadata and the native app version consistent."""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def declared_version(path=ROOT / "version.properties"):
    source = path.read_text()
    name = re.search(r"^versionName=(\d+\.\d+\.\d+)\s*$", source, re.M)
    code = re.search(r"^versionCode=([1-9]\d*)\s*$", source, re.M)
    if not name or not code:
        raise ValueError("version.properties must declare versionName and positive versionCode")
    return name.group(1), code.group(1)


def apk_minimum_sdk(metadata):
    # AAPT2 renamed sdkVersion to minSdkVersion; accept both tool generations.
    match = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'\s*$", metadata, re.M)
    if not match:
        raise ValueError("APK minimum SDK is missing from AAPT badging")
    return int(match.group(1))


def validate_tag(tag, channel, version, build):
    if tag in {"draft", "dev-latest"} and channel == "dev":
        return
    match = re.fullmatch(r"v?(\d+\.\d+\.\d+)(?:\+(\d+))?(?:\.(dev|prod))?", tag)
    if not match or match.group(1) != version:
        raise ValueError("Release tag version must match version.properties")
    if match.group(2) is not None and match.group(2) != build:
        raise ValueError("Release tag build number must match version.properties")
    if match.group(3) is not None and match.group(3) != channel:
        raise ValueError("Release tag channel does not match the build")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--channel", choices=["dev", "prod"], required=True)
    args = parser.parse_args()
    version, build = declared_version()
    validate_tag(args.tag, args.channel, version, build)
    print(f"VetrOFF Client {version}+{build}: release metadata verified")
