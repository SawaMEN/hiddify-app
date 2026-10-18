#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MACOS="$ROOT/macos"
SPEC="$MACOS/project.yml"
GENERATED="$MACOS/project-with-core.generated.yml"
CORE="$MACOS/Core/HiddifyCore.xcframework"

if [[ ! -d "$CORE" ]]; then
    echo "Generating native UI-only project (Go core not installed)."
    xcodegen generate --spec "$SPEC" --project "$MACOS"
    exit 0
fi

echo "Generating native project with static HiddifyCore inside PacketTunnel only."
python3 - "$SPEC" "$GENERATED" <<'PY'
import pathlib
import sys

source = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
needle = "  PacketTunnel:\n    type: app-extension\n    platform: macOS\n"
if source.count(needle) != 1:
    raise SystemExit("Unexpected project.yml PacketTunnel layout")
replacement = needle + """    dependencies:
      - framework: Core/HiddifyCore.xcframework
        embed: false
"""
source = source.replace(needle, replacement, 1)

# Keep all native system libraries in the extension link only; the app
# does not run an additional Go runtime or load Chromium into its UI process.
marker = "        APPLICATION_EXTENSION_API_ONLY: YES\n"
if source.count(marker) != 1:
    raise SystemExit("Unexpected project.yml extension build settings")
source = source.replace(
    marker,
    marker + '        OTHER_LDFLAGS: "$(inherited) -framework AppKit -framework CoreText -framework UniformTypeIdentifiers -framework SystemConfiguration -framework Network -framework CoreServices -framework ApplicationServices -framework Carbon -framework Security -lresolv -lbsm"\n',
    1,
)
pathlib.Path(sys.argv[2]).write_text(source, encoding="utf-8")
PY
trap 'rm -f "$GENERATED"' EXIT
xcodegen generate --spec "$GENERATED" --project "$MACOS"
