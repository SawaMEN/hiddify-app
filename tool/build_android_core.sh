#!/usr/bin/env bash
set -euo pipefail
# Use the same pinned, versioned core build and provenance as app/CI builds.
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
git -C "$root" submodule update --init --recursive
if [ -n "${HIDDIFY_ANDROID_NDK:-}" ]; then
  export ANDROID_NDK_HOME="$HIDDIFY_ANDROID_NDK"
fi
exec make -C "$root" android-libs
