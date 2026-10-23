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

# Production Developer ID signing uses different provisioning profiles for
# the host app and PacketTunnel.appex. Set both UUIDs only in the protected
# release environment; the normal unsigned CI and Debug builds are unchanged.
import os
import re
team = os.environ.get("VETROFF_TEAM_ID", "")
app_uuid = os.environ.get("VETROFF_APP_PROFILE_UUID", "")
tun_uuid = os.environ.get("VETROFF_TUN_PROFILE_UUID", "")
if any((team, app_uuid, tun_uuid)):
    if not (re.fullmatch(r"[A-Z0-9]{10}", team) and
            all(re.fullmatch(r"[0-9A-Fa-f-]{36}", x) for x in (app_uuid, tun_uuid))):
        raise SystemExit("Invalid/missing team and separate provisioning profile UUIDs")
    newline = chr(10)
    source = source.replace(
        "    CODE_SIGN_STYLE: Automatic" + newline,
        "    CODE_SIGN_STYLE: Manual" + newline,
        1,
    )
    for bid, profile_uuid in (
        ("app.vetroff.client.macos", app_uuid),
        ("app.vetroff.client.macos.PacketTunnel", tun_uuid),
    ):
        anchor = "        PRODUCT_BUNDLE_IDENTIFIER: " + bid + newline
        if source.count(anchor) != 1:
            raise SystemExit("Unexpected project.yml signing target layout")
        source = source.replace(
            anchor,
            anchor + "        DEVELOPMENT_TEAM: " + team + newline +
            "        PROVISIONING_PROFILE_SPECIFIER: " + profile_uuid + newline +
            "        CODE_SIGN_STYLE: Manual" + newline +
            '        CODE_SIGN_IDENTITY: "Developer ID Application"' + newline,
            1,
        )

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
# Set only when the framework is actually linked into PacketTunnel.
# Runtime checks still require both VPN and shared-Keychain entitlements;
# an ad-hoc preview cannot start a tunnel in either configuration.
app_marker = '        ENABLE_HARDENED_RUNTIME: YES\n        GENERATE_INFOPLIST_FILE: NO\n'
if source.count(app_marker) != 1:
    raise SystemExit("Unexpected macOS app build settings")
source = source.replace(
    app_marker,
    '        SWIFT_ACTIVE_COMPILATION_CONDITIONS: "$(inherited) VETROFF_NATIVE_CORE"\n' + app_marker,
    1,
)
pathlib.Path(sys.argv[2]).write_text(source, encoding="utf-8")
PY
trap 'rm -f "$GENERATED"' EXIT
xcodegen generate --spec "$GENERATED" --project "$MACOS"
