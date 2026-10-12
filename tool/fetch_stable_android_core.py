#!/usr/bin/env python3
"""Install Android ARM64 SDK from SawaMEN/hiddify-core /releases/latest.

Never consume source-branch commits, drafts, prereleases, or generic Linux assets.
Verify archive and member SHA-256 and matching version before replacing binaries.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tarfile
import tempfile
import time
from urllib.request import Request, urlopen
from zipfile import ZipFile

REPO = "https://github.com/SawaMEN/hiddify-core"
API = "https://api.github.com/repos/SawaMEN/hiddify-core/releases/latest"
ASSET = "hiddify-core-android-arm64.tar.gz"
FILES = {"hiddify-core.aar", "libhiddify-root.so", "target.json", "SHA256SUMS"}
ROOT = Path(__file__).resolve().parents[1]
ANDROID_LIBS = ROOT / "android/app/libs"
ROOT_COMPANION = ROOT / "android/app/src/main/jniLibs/arm64-v8a/libhiddify-root.so"


def download(url, path=None):
    headers = {"User-Agent": "VetrOFF-Android-stable-core"}
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token and url.startswith("https://api.github.com/"):
        headers["Authorization"] = "Bearer " + token
    for attempt in range(4):
        try:
            with urlopen(Request(url, headers=headers), timeout=100) as source:
                if path is None:
                    return source.read()
                with path.open("wb") as dest:
                    shutil.copyfileobj(source, dest, length=1048576)
                return
        except Exception:
            if attempt == 3:
                raise
            time.sleep(2 * (attempt + 1))


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1024 * 1024), b""):
            h.update(b)
    return h.hexdigest()


def verify_sidecar(path, checksum, name):
    words = checksum.strip().split()
    if len(words) != 2 or not re.fullmatch(r"[0-9a-fA-F]{64}", words[0]):
        raise ValueError("Invalid SHA-256 metadata for " + name)
    if words[1].lstrip("*") != name or digest(path) != words[0].lower():
        raise ValueError("SHA-256 mismatch: " + name)


def latest():
    release = json.loads(download(API))
    if release.get("draft") or release.get("prerelease") or not release.get("published_at"):
        raise ValueError("The latest kernel release is not stable")
    tag = release.get("tag_name", "")
    if not re.fullmatch(r"sawamen-[0-9a-f]{12}-[0-9a-f]{12}-panel[0-9]+", tag):
        raise ValueError("Unexpected stable kernel release tag: " + tag)
    assets = {a["name"]: a for a in release.get("assets", [])}
    for name in (ASSET, ASSET + ".sha256"):
        asset = assets.get(name)
        if not asset:
            raise ValueError("Stable kernel release lacks Android ARM64 asset: " + name)
        url = asset["browser_download_url"]
        if url != f"{REPO}/releases/download/{tag}/{name}":
            raise ValueError("Untrusted Android release asset URL")
    return release, assets


def validate_archive(archive, destination, tag):
    with tarfile.open(archive, "r:gz") as tar:
        entries = tar.getmembers()
        if {member.name for member in entries} != FILES or len(entries) != len(FILES):
            raise ValueError("Unexpected Android release package contents")
        for member in entries:
            if not member.isfile() or member.size <= 0 or member.size > 400 * 1024 * 1024:
                raise ValueError("Unsafe Android archive member: " + member.name)
            inp = tar.extractfile(member)
            if inp is None:
                raise ValueError("Missing Android archive member: " + member.name)
            with inp, (destination / member.name).open("wb") as out:
                shutil.copyfileobj(inp, out, length=1024 * 1024)

    lines = (destination / "SHA256SUMS").read_text().splitlines()
    if len(lines) != 2:
        raise ValueError("Android asset must have checksums for both binaries")
    listed = {}
    for line in lines:
        match = re.fullmatch(r"([0-9a-fA-F]{64})  (hiddify-core\.aar|libhiddify-root\.so)", line)
        if not match or match.group(2) in listed:
            raise ValueError("Invalid or duplicate checksum entry")
        listed[match.group(2)] = match.group(1).lower()
    if set(listed) != {"hiddify-core.aar", "libhiddify-root.so"}:
        raise ValueError("Incomplete Android checksums")
    for name, sha in listed.items():
        if digest(destination / name) != sha:
            raise ValueError("Android binary checksum mismatch: " + name)

    manifest = json.loads((destination / "target.json").read_text())
    target = manifest.get("target", {})
    if (target.get("repository") != REPO or target.get("version") != tag
            or not re.fullmatch(r"[0-9a-f]{40}", target.get("commit", ""))):
        raise ValueError("Android release source identity mismatch")
    if manifest.get("android_build") != {"platform": "android", "arch": "arm64"}:
        raise ValueError("Android release has incorrect platform/architecture")
    with (destination / "libhiddify-root.so").open("rb") as f:
        elf = f.read(20)
    if len(elf) != 20 or elf[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<HH", elf, 16) != (3, 183):
        raise ValueError("Root companion must be a 64-bit ARM64 PIE ELF")
    with ZipFile(destination / "hiddify-core.aar") as aar:
        if aar.testzip() is not None:
            raise ValueError("Damaged Android AAR")
        if not any(x.startswith("jni/arm64-v8a/") and x.endswith(".so") for x in aar.namelist()):
            raise ValueError("AAR does not contain ARM64 JNI library")
    return target


def install():
    release, assets = latest()
    # Use a temporary directory, never write unverified bytes to application paths.
    with tempfile.TemporaryDirectory(prefix="hiddify-android-core-") as directory:
        folder = Path(directory)
        archive = folder / ASSET
        checksum = folder / (ASSET + ".sha256")
        download(assets[ASSET]["browser_download_url"], archive)
        download(assets[ASSET + ".sha256"]["browser_download_url"], checksum)
        verify_sidecar(archive, checksum.read_text(), ASSET)
        target = validate_archive(archive, folder, release["tag_name"])
        ANDROID_LIBS.mkdir(parents=True, exist_ok=True)
        ROOT_COMPANION.parent.mkdir(parents=True, exist_ok=True)
        src_aar = folder / "hiddify-core.aar"
        src_root = folder / "libhiddify-root.so"
        provenance = {
            "release_tag": release["tag_name"],
            "release_url": release["html_url"],
            "core_commit": target["commit"],
            "sing_box_commit": target["submodules"]["hiddify-sing-box"],
            "ray2sing_commit": target["submodules"]["ray2sing"],
            "aar_sha256": digest(src_aar),
            "root_companion_sha256": digest(src_root),
            "source": "hiddify-core-stable-release",
            "application_commit": subprocess.check_output(
                ["git", "-C", str(ROOT), "rev-parse", "HEAD"], text=True
            ).strip(),
        }
        os.replace(src_aar, ANDROID_LIBS / "hiddify-core.aar")
        os.replace(src_root, ROOT_COMPANION)
        (ANDROID_LIBS / "hiddify-core.provenance.json").write_text(
            json.dumps(provenance, indent=2) + "\n"
        )
    print("Installed verified stable Android core", release["tag_name"])


if __name__ == "__main__":
    try:
        install()
    except Exception as e:
        print("Stable Android core download failed:", e, file=sys.stderr)
        sys.exit(1)
