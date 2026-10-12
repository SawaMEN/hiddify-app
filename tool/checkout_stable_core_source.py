#!/usr/bin/env python3
"""Checkout the selected stable release source solely for optional Go tests."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

from fetch_stable_android_core import latest, ANDROID_LIBS, ROOT

CORE_URL = "https://github.com/SawaMEN/hiddify-core.git"
FULL_SHA = re.compile(r"[0-9a-f]{40}")
TAG = re.compile(r"sawamen-([0-9a-f]{12})-([0-9a-f]{12})-panel[0-9]+")


def git(*args, directory=None):
    command = ["git"]
    if directory:
        command += ["-C", str(directory)]
    command += list(args)
    return subprocess.check_output(command, text=True).strip()


def identity(from_provenance):
    if from_provenance:
        p = json.loads((ANDROID_LIBS / "hiddify-core.provenance.json").read_text())
        if p.get("source") != "hiddify-core-stable-release":
            raise ValueError("Provenance must come from a verified stable release")
        tag = p["release_tag"]
        sha = p["core_commit"]
        submodules = {"hiddify-sing-box": p["sing_box_commit"],
                      "ray2sing": p["ray2sing_commit"]}
    else:
        release, _ = latest()
        tag = release["tag_name"]
        sha = release.get("target_commitish", "")
        submodules = None
    tag_match = TAG.fullmatch(tag)
    if not FULL_SHA.fullmatch(sha) or not tag_match or tag_match.group(1) != sha[:12]:
        raise ValueError("Release tag and core commit disagree")
    if submodules:
        for name, revision in submodules.items():
            if not FULL_SHA.fullmatch(revision):
                raise ValueError("Invalid source revision for " + name)
        if submodules["hiddify-sing-box"][:12] != tag_match.group(2):
            raise ValueError("Stable release Sing-box revision disagrees with tag")
    return tag, sha, submodules


def checkout(tag, sha, submodules, destination):
    if destination.exists():
        raise ValueError(f"Refusing to overwrite existing directory {destination}")
    destination.mkdir(parents=True)
    git("init", "-q", str(destination))
    git("remote", "add", "origin", CORE_URL, directory=destination)
    git("fetch", "--depth=1", "origin", sha, directory=destination)
    git("checkout", "-q", "--detach", "FETCH_HEAD", directory=destination)
    if git("rev-parse", "HEAD", directory=destination) != sha:
        raise ValueError("Source checkout differs from stable release revision")
    git("submodule", "update", "--init", "--recursive", "--depth=1", directory=destination)
    if submodules:
        for name, expected in submodules.items():
            if git("rev-parse", "HEAD", directory=destination / name) != expected:
                raise ValueError("Source checkout submodule mismatch: " + name)
    print(f"Optional Go test checkout: {tag} ({sha})")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--latest", action="store_true")
    group.add_argument("--provenance", action="store_true")
    args = parser.parse_args()
    tag, sha, submodules = identity(args.provenance)
    checkout(tag, sha, submodules, ROOT / "hiddify-core")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"Failed to prepare stable core source tests: {exc}", file=sys.stderr)
        sys.exit(1)
