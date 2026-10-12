#!/usr/bin/env bash
set -euo pipefail
# Fetch and verify latest stable Android kernel release.
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -n "${HIDDIFY_ANDROID_NDK:-}" ]; then
  export ANDROID_NDK_HOME="$HIDDIFY_ANDROID_NDK"
fi
exec make -C "$root" android-libs
