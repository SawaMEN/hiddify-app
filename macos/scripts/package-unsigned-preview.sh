#!/usr/bin/env bash
set -euo pipefail

APP="${VETROFF_BUILD_APP:?Set VETROFF_BUILD_APP to the built VetrOFF.app directory}"
OUT="${VETROFF_PREVIEW_OUT:?Set VETROFF_PREVIEW_OUT to output directory}"

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "macOS packaging requires Apple's ditto and lipo utilities." >&2
    exit 1
fi

EXT="$APP/Contents/PlugIns/PacketTunnel.appex"
APP_BIN="$APP/Contents/MacOS/VetrOFF"
EXT_BIN="$EXT/Contents/MacOS/PacketTunnel"

# Fail hard if the UI-only project was accidentally generated or
# the bundled Network Extension wasn't built.
test -s "$APP_BIN"
test -s "$EXT_BIN"
for BIN in "$APP_BIN" "$EXT_BIN"; do
    xcrun lipo -archs "$BIN" | grep -qw arm64 || {
        echo "Missing ARM64 binary: $BIN" >&2
        exit 1
    }
done

# Do not accidentally ship the app if somebody changed this job to
# generate a signed build without reviewing release signing.
if codesign --verify "$APP" >/dev/null 2>&1; then
    echo "Expected an unsigned Developer Preview. Refusing to package a signed build." >&2
    exit 1
fi

mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
ZIP="$OUT/VetrOFF-macOS-arm64-unsigned-preview.zip"
ditto -c -k --keepParent --sequesterRsrc "$APP" "$ZIP"
test -s "$ZIP"
(
  cd "$OUT"
  shasum -a 256 "VetrOFF-macOS-arm64-unsigned-preview.zip" \
    > "VetrOFF-macOS-arm64-unsigned-preview.sha256"
)

cat > "$OUT/VetrOFF-macOS-README.txt" <<EOF
VetrOFF for macOS 14+ (Apple Silicon / arm64)
Developer Preview — UNSIGNED and NOT NOTARIZED

Source commit: ${GITHUB_SHA:-unknown}
Core pin: 37ed16781062332d2269e57d2bf1a85d6a4e2717
Archive: VetrOFF-macOS-arm64-unsigned-preview.zip

This build includes SwiftUI VetrOFF.app and the embedded Go-backed
PacketTunnel.appex. GitHub Actions can compile the bundle, but this
is NOT an installable, production-ready VPN release.

The unsigned app may be blocked by Gatekeeper. Apple's VPN extension
requires appropriate Developer ID / provisioning and packet-tunnel
entitlements. Do not disable macOS security protections to run an
untrusted build. Do not use this preview to protect sensitive traffic.

A working VPN must first pass signed-device TCP, UDP, DNS, IPv6,
network-transition, and privacy-leak acceptance testing.

Instructions:
https://github.com/SawaMEN/hiddify-app/blob/main/macos/ON_DEVICE_TESTING.md
EOF
ls -lh "$OUT"
