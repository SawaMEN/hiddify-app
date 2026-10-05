"""Record the exact source revisions and checksum of the locally built AAR."""

import hashlib
import json
from pathlib import Path
import subprocess
import sys


def revision(path: Path) -> str:
    return subprocess.check_output(
        ["git", "-C", str(path), "rev-parse", "HEAD"], text=True
    ).strip()


def dirty(path: Path) -> bool:
    return bool(subprocess.check_output(
        ["git", "-C", str(path), "status", "--porcelain", "--untracked-files=no"], text=True
    ).strip())


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    aar = Path(sys.argv[1]).resolve()
    core = root / "hiddify-core"
    with aar.open("rb") as source:
        checksum = hashlib.file_digest(source, "sha256").hexdigest()
    data = {
        "application_commit": revision(root),
        "core_commit": revision(core),
        "sing_box_commit": revision(core / "hiddify-sing-box"),
        "ray2sing_commit": revision(core / "ray2sing"),
        "source_dirty": {
            "application": dirty(root),
            "core": dirty(core),
            "sing_box": dirty(core / "hiddify-sing-box"),
        },
        "go": subprocess.check_output(["go", "version"], text=True).strip(),
        "aar_sha256": checksum,
    }
    root_binary = root / "android/app/src/main/jniLibs/arm64-v8a/libhiddify-root.so"
    with root_binary.open("rb") as source:
        data["root_companion_sha256"] = hashlib.file_digest(source, "sha256").hexdigest()
    output = aar.with_suffix(".provenance.json")
    output.write_text(json.dumps(data, indent=2) + "\n")
    print(f"Core provenance: {output}")


if __name__ == "__main__":
    main()
