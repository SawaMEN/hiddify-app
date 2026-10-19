#!/usr/bin/env bash
set -euo pipefail

# Verify BOTH signed binaries before notarization or publishing.
# Never echo signing certificates, account credentials or profile payloads.
APP="${1:?Usage: verify-signed-app.sh /path/VetrOFF.app TEAM_ID}"
TEAM="${2:?Apple Developer Team ID required}"
if [[ ! "$TEAM" =~ ^[A-Z0-9]{10}$ ]]; then
    echo "Invalid Apple Developer Team ID" >&2; exit 1
fi
TUN="$APP/Contents/PlugIns/PacketTunnel.appex"
test -d "$TUN"
for bundle in "$APP" "$TUN"; do
    codesign --verify --deep --strict --verbose=2 "$bundle"
    codesign -dv --verbose=4 "$bundle" 2>&1 |
        grep -Fx "TeamIdentifier=$TEAM" >/dev/null || {
            echo "Signing team mismatch: $bundle" >&2; exit 1;
        }
    codesign -dv --verbose=4 "$bundle" 2>&1 |
        grep -E 'flags=.*\(runtime\)' >/dev/null || {
            echo "Missing hardened runtime: $bundle" >&2; exit 1;
        }
    bin="$bundle/Contents/MacOS/$(basename "$bundle" .app)"
    if [[ "$bundle" == "$TUN" ]]; then
        bin="$bundle/Contents/MacOS/PacketTunnel"
    fi
    xcrun lipo -archs "$bin" | grep -qw arm64
done

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
codesign -d --entitlements :- "$APP" > "$tmp/app.plist" 2>/dev/null
codesign -d --entitlements :- "$TUN" > "$tmp/tunnel.plist" 2>/dev/null

python3 - "$tmp/app.plist" "$tmp/tunnel.plist" "$TEAM" <<'PY'
import plistlib
import sys
team = sys.argv[3]
expected = ("app.vetroff.client.macos", "app.vetroff.client.macos.PacketTunnel")
groups = []
for binary, filename in zip(expected, sys.argv[1:3]):
    with open(filename, "rb") as file:
        ent = plistlib.load(file)
    if "packet-tunnel-provider" not in ent.get(
        "com.apple.developer.networking.networkextension", []
    ):
        raise SystemExit(binary + ": missing packet-tunnel entitlement")
    if ent.get("com.apple.developer.team-identifier", team) != team:
        raise SystemExit(binary + ": team entitlement mismatch")
    access = ent.get("keychain-access-groups", [])
    group = team + ".app.vetroff.client.macos.shared"
    if group not in access:
        raise SystemExit(binary + ": shared Keychain group not provisioned")
    if ent.get("com.apple.security.app-sandbox") is not True:
        raise SystemExit(binary + ": app sandbox missing")
    groups.append(group)
if groups[0] != groups[1]:
    raise SystemExit("Host and extension do not share Keychain group")
print("Code signature and Network Extension/Keychain entitlements verified.")
PY
