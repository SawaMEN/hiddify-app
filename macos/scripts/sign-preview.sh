#!/usr/bin/env bash
set -euo pipefail

# A local integrity signature is required for reliable ARM64 launches. It is
# NOT a Developer ID signature and cannot authorize Network Extension/Keychain
# groups. Never attach those restricted entitlements to an ad-hoc preview.
APP="${1:?Usage: sign-preview.sh /path/VetrOFF.app}"
TUN="$APP/Contents/PlugIns/PacketTunnel.appex"
[[ "$(uname -s)" == Darwin ]]
test -x "$APP/Contents/MacOS/VetrOFF"
test -x "$TUN/Contents/MacOS/PacketTunnel"
for bundle in "$TUN" "$APP"; do
    if codesign -dv "$bundle" 2>&1 | grep -q '^Authority='; then
        echo "Refusing to replace an identity-signed bundle with an ad-hoc signature: $bundle" >&2
        exit 1
    fi
done
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
printf '%s\n' '<?xml version="1.0" encoding="UTF-8"?><plist version="1.0"><dict/></plist>' > "$TEMP/empty.plist"
for bundle in "$TUN" "$APP"; do
    codesign --force --sign - --timestamp=none --entitlements "$TEMP/empty.plist" "$bundle"
    codesign --verify --strict --verbose=2 "$bundle"
done
codesign --verify --deep --strict --verbose=2 "$APP"
