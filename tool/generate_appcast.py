"""Publish only this fork's stable ARM64 update feed and asset checksums."""
import argparse
import hashlib
from pathlib import Path
import re
import xml.etree.ElementTree as ET


def generate(directory, repository, tag, channel):
    if repository != "SawaMEN/hiddify-app":
        raise ValueError("Unexpected update repository")
    apks = sorted(directory.glob("*.apk"))
    if not apks:
        raise ValueError("No APK assets")
    sums = []
    for apk in apks:
        with apk.open("rb") as stream:
            sums.append(f"{hashlib.file_digest(stream, 'sha256').hexdigest()}  {apk.name}\n")
    (directory / "SHA256SUMS").write_text("".join(sums))
    if channel != "prod":
        return
    match = re.fullmatch(r"v?(\d+\.\d+\.\d+)(?:\+(\d+))?(?:\.prod)?", tag)
    if not match:
        raise ValueError("Stable release requires a semantic version tag")
    ns = "http://www.andymatuschak.org/xml-namespaces/sparkle"
    ET.register_namespace("sparkle", ns)
    rss = ET.Element("rss", {"version": "2.0"})
    feed = ET.SubElement(rss, "channel")
    ET.SubElement(feed, "title").text = "Hiddify SawaMEN updates"
    for apk in apks:
        if "arm64" not in apk.name.lower() or "dev" in apk.name.lower():
            continue
        item = ET.SubElement(feed, "item")
        ET.SubElement(item, "title").text = match[1]
        ET.SubElement(item, f"{{{ns}}}minimumSystemVersion").text = "7.0.0"
        ET.SubElement(item, "link").text = f"https://github.com/{repository}/releases/tag/{tag}"
        ET.SubElement(item, "enclosure", {
            "url": f"https://github.com/{repository}/releases/download/{tag}/{apk.name}",
            "length": str(apk.stat().st_size), "type": "application/vnd.android.package-archive",
            f"{{{ns}}}version": match[1], f"{{{ns}}}shortVersionString": match[1], f"{{{ns}}}os": "android",
        })
    if not feed.findall("item"):
        raise ValueError("No stable ARM64 APK")
    ET.ElementTree(rss).write(directory / "appcast.xml", encoding="utf-8", xml_declaration=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--channel", required=True)
    args = parser.parse_args()
    generate(args.directory, args.repository, args.tag, args.channel)
