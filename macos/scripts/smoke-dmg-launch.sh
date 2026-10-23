#!/usr/bin/env bash
set -euo pipefail

DMG="${1:?Usage: smoke-dmg-launch.sh /path/VetrOFF-macOS-arm64.dmg}"
[[ "$(uname -s)" == Darwin ]]
TEMP="$(mktemp -d)"
APP_PID=""
MOUNTED=false
cleanup() {
    if [[ -n "$APP_PID" ]]; then
        kill "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
    if [[ "$MOUNTED" == true ]]; then hdiutil detach -quiet "$TEMP/mount" || true; fi
    rm -rf "$TEMP"
}
trap cleanup EXIT
mkdir -p "$TEMP/mount" "$TEMP/installed"
hdiutil attach -quiet -readonly -nobrowse -mountpoint "$TEMP/mount" "$DMG"
MOUNTED=true
# Verify exactly the bytes a user installs, after the image has been mounted.
ditto "$TEMP/mount/VetrOFF.app" "$TEMP/installed/VetrOFF.app"
hdiutil detach -quiet "$TEMP/mount"
MOUNTED=false
APP="$TEMP/installed/VetrOFF.app"
codesign --verify --deep --strict --verbose=2 "$APP"
# Launch the bundle executable without relying on a pre-existing app instance.
# This checks dyld, code signatures and SwiftUI initialization; Gatekeeper and
# a working VPN still require Developer ID/notarization and physical Mac tests.
"$APP/Contents/MacOS/VetrOFF" --launch-smoke-test --profile-smoke-test > "$TEMP/launch.log" 2>&1 &
APP_PID=$!
for attempt in $(seq 1 30); do
    if ! kill -0 "$APP_PID" 2>/dev/null; then
        cat "$TEMP/launch.log" >&2
        echo "VetrOFF exited before its main window opened." >&2
        exit 1
    fi
    if grep -q '^VETROFF_MAIN_WINDOW_READY$' "$TEMP/launch.log" &&
       grep -q '^VETROFF_PROFILE_STORE_READY$' "$TEMP/launch.log"; then
        sleep 3
        if ! kill -0 "$APP_PID" 2>/dev/null; then
            cat "$TEMP/launch.log" >&2
            echo "VetrOFF crashed just after opening its window." >&2
            exit 1
        fi
        if [[ -n "${VETROFF_SCREENSHOT_DIR:-}" ]]; then
            mkdir -p "$VETROFF_SCREENSHOT_DIR"
            screencapture -x "$VETROFF_SCREENSHOT_DIR/macos-home.png" || true
        fi
        echo "Installed DMG verified: window + private Keychain import/read/select/rename/delete."
        exit 0
    fi
    sleep 1
done
cat "$TEMP/launch.log" >&2
echo "VetrOFF did not show its main window within 30 seconds." >&2
exit 1
