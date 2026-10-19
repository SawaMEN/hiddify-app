#!/usr/bin/env bash
set -euo pipefail

# Build and validate a locally signed, Debug-only Network Extension on an
# Apple Silicon Mac. No credentials or private keys are uploaded to CI.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEAM_ID="${APPLE_TEAM_ID:?Set APPLE_TEAM_ID to your Apple Developer Team ID}"
CORE="$ROOT/macos/Core/HiddifyCore.xcframework"
DERIVED="${VETROFF_DERIVED_DATA:-$ROOT/macos/build/SignedDeviceTest}"

if [[ "$(uname -s)" != "Darwin" ]] || [[ "$(uname -m)" != "arm64" ]]; then
    echo "A physical Apple Silicon Mac is required." >&2
    exit 1
fi
if ! command -v xcodegen >/dev/null; then
    echo "Install XcodeGen: brew install xcodegen" >&2
    exit 1
fi
if [[ ! -f "$CORE/macos-arm64/HiddifyCore.framework/HiddifyCore" ]]; then
    echo "Missing pinned HiddifyCore.xcframework. Follow macos/ON_DEVICE_TESTING.md." >&2
    exit 1
fi
if [[ ! "$TEAM_ID" =~ ^[A-Z0-9]{10}$ ]]; then
    echo "APPLE_TEAM_ID must be a 10-character Apple Developer Team ID." >&2
    exit 1
fi

cd "$ROOT"
bash macos/scripts/generate-xcode-project.sh

args=()
if [[ "${VETROFF_ALLOW_PROVISIONING_UPDATES:-0}" == "1" ]]; then
    args+=(-allowProvisioningUpdates)
fi

xcodebuild -project macos/VetrOFF.xcodeproj \
    -scheme VetrOFF -configuration Debug \
    -derivedDataPath "$DERIVED" \
    -destination "generic/platform=macOS" \
    ARCHS=arm64 ONLY_ACTIVE_ARCH=NO \
    "DEVELOPMENT_TEAM=$TEAM_ID" \
    CODE_SIGN_STYLE=Automatic CODE_SIGNING_ALLOWED=YES \
    "${args[@]}" build

APP="$DERIVED/Build/Products/Debug/VetrOFF.app"
EXT="$APP/Contents/PlugIns/PacketTunnel.appex"
test -d "$EXT"

for bundle in "$EXT" "$APP"; do
    codesign --verify --deep --strict --verbose=2 "$bundle"
done

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT
codesign -d --entitlements :- "$APP" > "$tmpdir/app.plist" 2>/dev/null
codesign -d --entitlements :- "$EXT" > "$tmpdir/tunnel.plist" 2>/dev/null

python3 - "$tmpdir/app.plist" "$tmpdir/tunnel.plist" "$TEAM_ID" <<'PY'
import plistlib
import sys

group_suffix = ".app.vetroff.client.macos.shared"
team_id = sys.argv[3]
groups = []
for name, filename in zip(("VetrOFF.app", "PacketTunnel.appex"), sys.argv[1:3]):
    with open(filename, "rb") as file:
        ent = plistlib.load(file)
    types = ent.get("com.apple.developer.networking.networkextension", [])
    if "packet-tunnel-provider" not in types:
        raise SystemExit(f"{name}: Network Extension entitlement missing")
    access_groups = ent.get("keychain-access-groups", [])
    valid = [g for g in access_groups
             if g.startswith(team_id + ".") and g.endswith(group_suffix)]
    if len(valid) != 1:
        raise SystemExit(f"{name}: shared Keychain entitlement missing/misprovisioned")
    groups.append(valid[0])
if groups[0] != groups[1]:
    raise SystemExit("App and PacketTunnel have different Keychain groups")
print("Both code signatures have the packet-tunnel entitlement and matching Keychain access group.")
PY

# Produce exactly one signed test DMG after signature/entitlement preflight.
# Do not publish it as production: it is not Developer ID notarized, and
# successful packaging does not verify that any packets were forwarded.
SIGNED_DMG="${VETROFF_SIGNED_DMG_OUTPUT:-$ROOT/macos/build/VetrOFF-macOS-arm64-signed-debug.dmg}"
bash "$ROOT/macos/scripts/package-dmg.sh" "$APP" "$SIGNED_DMG"

echo "Signed test application: $APP"
echo "Signed Debug installer: $SIGNED_DMG"
echo "Run it on this Mac and perform macos/ON_DEVICE_TESTING.md."
echo "This check does NOT verify real TCP/UDP packets, DNS, IPv6 or Apple notarization."
