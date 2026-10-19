#!/usr/bin/env bash
set -euo pipefail

# Package an already built native macOS ARM64 app as exactly one drag-to-install
# DMG. Do not claim that an unsigned CI app has usable VPN entitlements.
APP="${1:?Usage: package-dmg.sh /path/VetrOFF.app /path/VetrOFF-macOS-arm64.dmg}"
OUTPUT="${2:?Missing output DMG path}"

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "DMG packaging requires macOS / hdiutil." >&2
    exit 1
fi
if [[ ! -d "$APP/Contents/PlugIns/PacketTunnel.appex" ]]; then
    echo "Refusing to package a UI-only app without PacketTunnel.appex." >&2
    exit 1
fi

APP_BIN="$APP/Contents/MacOS/VetrOFF"
TUN_BIN="$APP/Contents/PlugIns/PacketTunnel.appex/Contents/MacOS/PacketTunnel"
for binary in "$APP_BIN" "$TUN_BIN"; do
    if [[ ! -f "$binary" ]]; then
        echo "Missing expected macOS executable: $binary" >&2
        exit 1
    fi
    if ! xcrun lipo -archs "$binary" | grep -qw arm64; then
        echo "Executable is not Apple Silicon ARM64: $binary" >&2
        exit 1
    fi
done

if [[ "$(plutil -extract CFBundleIdentifier raw -o - "$APP/Contents/Info.plist")" != "app.vetroff.client.macos" ]]; then
    echo "Unexpected VetrOFF application identifier." >&2
    exit 1
fi
if [[ "$(plutil -extract CFBundleIdentifier raw -o - "$APP/Contents/PlugIns/PacketTunnel.appex/Contents/Info.plist")" != "app.vetroff.client.macos.PacketTunnel" ]]; then
    echo "Unexpected PacketTunnel application identifier." >&2
    exit 1
fi

mkdir -p "$(dirname "$OUTPUT")"
OUTPUT="$(cd "$(dirname "$OUTPUT")" && pwd)/$(basename "$OUTPUT")"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
ditto "$APP" "$STAGE/VetrOFF.app"
ln -s /Applications "$STAGE/Applications"

# The drag-and-drop disk image contains one app and an Applications shortcut.
# Never embed XCFramework archives, sources, APKs, or developer secrets.
hdiutil create -quiet -volname "VetrOFF macOS" -srcfolder "$STAGE" \
    -format UDZO -fs HFS+ -ov "$OUTPUT"
hdiutil verify -quiet "$OUTPUT"
test -s "$OUTPUT"
echo "Verified macOS DMG: $OUTPUT"
ls -lh "$OUTPUT"
