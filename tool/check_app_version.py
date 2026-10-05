"""Keep release tags, Android metadata and pubspec's app version consistent."""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def declared_version(path=ROOT / "pubspec.yaml"):
    match = re.search(r"^version:\s*(\d+\.\d+\.\d+)\+(\d+)\s*$", path.read_text(), re.M)
    if not match:
        raise ValueError("pubspec.yaml must declare version+buildNumber")
    return match.group(1), match.group(2)


def validate_tag(tag, channel, version, build):
    if tag in {"draft", "dev-latest"} and channel == "dev":
        return
    match = re.fullmatch(r"v?(\d+\.\d+\.\d+)(?:\+(\d+))?(?:\.(dev|prod))?", tag)
    if not match or match.group(1) != version:
        raise ValueError("Release tag version must match pubspec.yaml")
    if match.group(2) is not None and match.group(2) != build:
        raise ValueError("Release tag build number must match pubspec.yaml")
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
