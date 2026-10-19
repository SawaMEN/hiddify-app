#!/usr/bin/env bash
set -euo pipefail

# Protected signing job only. Credentials never appear in repository files or logs.
: "${VETROFF_TEAM_ID:?Missing Developer Team ID}"
: "${MACOS_CERT_P12_B64:?Missing Developer ID .p12}"
: "${MACOS_CERT_P12_PASSWORD:?Missing certificate password}"
: "${MACOS_APP_PROFILE_B64:?Missing app Developer ID profile}"
: "${MACOS_TUN_PROFILE_B64:?Missing PacketTunnel Developer ID profile}"
: "${RUNNER_TEMP:?Requires a disposable macOS CI runner}"

if [[ ! "$VETROFF_TEAM_ID" =~ ^[A-Z0-9]{10}$ ]]; then
  echo "Invalid Developer Team ID" >&2; exit 1
fi

TEMP="$(mktemp -d "$RUNNER_TEMP/vetroff-sign.XXXXXX")"
KC="$TEMP/signing.keychain-db"
KC_PASSWORD="$(openssl rand -hex 32)"
cleanup() {
    security list-keychains -d user -s "$HOME/Library/Keychains/login.keychain-db" || true
    security delete-keychain "$KC" >/dev/null 2>&1 || true
    rm -rf "$TEMP"
}
trap cleanup EXIT

printf '%s' "$MACOS_CERT_P12_B64" | base64 -D > "$TEMP/cert.p12" 2>/dev/null ||
  { echo "Cannot decode Developer ID signing certificate" >&2; exit 1; }
security create-keychain -p "$KC_PASSWORD" "$KC"
security set-keychain-settings -lut 1800 "$KC"
security unlock-keychain -p "$KC_PASSWORD" "$KC"
security import "$TEMP/cert.p12" -k "$KC" -P "$MACOS_CERT_P12_PASSWORD" \
  -T /usr/bin/codesign -T /usr/bin/security >/dev/null
security set-key-partition-list -S apple-tool:,apple:,codesign: -s \
  -k "$KC_PASSWORD" "$KC" >/dev/null
security list-keychains -d user -s "$KC" "$HOME/Library/Keychains/login.keychain-db"

if ! security find-identity -v -p codesigning "$KC" |
  grep -F "Developer ID Application:" |
  grep -F "($VETROFF_TEAM_ID)" >/dev/null; then
    echo "No valid Developer ID Application identity for requested team" >&2
    exit 1
fi

mkdir -p "$HOME/Library/MobileDevice/Provisioning Profiles"
validate_profile() {
    local encoded="$1" bundle="$2" name="$3"
    local raw="$TEMP/$name.mobileprovision" plist="$TEMP/$name.plist"
    printf '%s' "$encoded" | base64 -D > "$raw" 2>/dev/null ||
      { echo "Cannot decode $name provisioning profile" >&2; exit 1; }
    security cms -D -i "$raw" > "$plist"
    local uuid
    uuid="$(python3 - "$plist" "$VETROFF_TEAM_ID" "$bundle" <<'PY'
import datetime
import plistlib
import re
import sys
from pathlib import Path
profile = plistlib.loads(Path(sys.argv[1]).read_bytes())
team, bundle = sys.argv[2:]
if team not in profile.get("TeamIdentifier", []):
    raise SystemExit("Developer ID profile team mismatch")
if profile.get("ExpirationDate") <= datetime.datetime.utcnow():
    raise SystemExit("Developer ID provisioning profile expired")
ent = profile.get("Entitlements", {})
app_id = ent.get("com.apple.application-identifier", ent.get("application-identifier", ""))
if app_id != team + "." + bundle:
    raise SystemExit("Developer ID profile application identifier mismatch")
if "packet-tunnel-provider" not in ent.get("com.apple.developer.networking.networkextension", []):
    raise SystemExit("Missing provisioned packet-tunnel capability")
if team + ".app.vetroff.client.macos.shared" not in ent.get("keychain-access-groups", []):
    raise SystemExit("Missing provisioned shared Keychain entitlement")
uuid = profile.get("UUID", "")
if not re.fullmatch(r"[0-9A-Fa-f-]{36}", uuid):
    raise SystemExit("Malformed provisioning UUID")
print(uuid)
PY
)"
    install -m 600 "$raw" "$HOME/Library/MobileDevice/Provisioning Profiles/$uuid.mobileprovision"
    printf '%s' "$uuid"
}

export VETROFF_APP_PROFILE_UUID
export VETROFF_TUN_PROFILE_UUID
VETROFF_APP_PROFILE_UUID="$(validate_profile "$MACOS_APP_PROFILE_B64" \
    app.vetroff.client.macos app)"
VETROFF_TUN_PROFILE_UUID="$(validate_profile "$MACOS_TUN_PROFILE_B64" \
    app.vetroff.client.macos.PacketTunnel tunnel)"

echo "Validated separate application and Packet Tunnel Developer ID profiles."
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
bash macos/scripts/generate-xcode-project.sh

DERIVED="$RUNNER_TEMP/VetrOFFSignedDerived"
xcodebuild -project macos/VetrOFF.xcodeproj -scheme VetrOFF \
  -configuration Release -destination 'generic/platform=macOS' \
  -derivedDataPath "$DERIVED" \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO \
  "DEVELOPMENT_TEAM=$VETROFF_TEAM_ID" \
  "CODE_SIGN_STYLE=Manual" \
  "CODE_SIGN_IDENTITY=Developer ID Application" \
  "CODE_SIGNING_ALLOWED=YES" \
  "OTHER_CODE_SIGN_FLAGS=--options runtime --timestamp" build

APP="$DERIVED/Build/Products/Release/VetrOFF.app"
test -d "$APP/Contents/PlugIns/PacketTunnel.appex"
bash macos/scripts/verify-signed-app.sh "$APP" "$VETROFF_TEAM_ID"
mkdir -p dist
bash macos/scripts/package-dmg.sh "$APP" "dist/VetrOFF-macOS-arm64.dmg"
test "$(find dist -maxdepth 1 -type f | wc -l | tr -d ' ')" = 1
echo "One signed DMG created; NOT notarized or released by this script."
