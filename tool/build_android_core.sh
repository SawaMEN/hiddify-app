#!/usr/bin/env bash
set -euo pipefail

# Build the exact core/kernel revisions in the git index, without mutable release assets.
git submodule update --init hiddify-core
git -C hiddify-core config submodule.ray2sing.url https://github.com/hiddify/ray2sing.git
git -C hiddify-core submodule update --init --recursive

ndk="${ANDROID_NDK_HOME:-${ANDROID_HOME:?Android SDK is required}/ndk/30.0.16248370}"
for attempt in $(seq 1 120); do
  if [ -f "$ndk/source.properties" ]; then break; fi
  if [ "$attempt" -eq 120 ]; then echo "Android NDK not available: $ndk" >&2; exit 1; fi
  sleep 1
done
export ANDROID_NDK_HOME="$ndk"
export GOTOOLCHAIN=auto
export PATH="$(go env GOPATH)/bin:$PATH"

(
  cd hiddify-core
  # Keep the feature set of the pinned core Makefile. Only ARM64 is packaged by this app.
  tags="$(python3 -c 'from pathlib import Path; print(next(l.split("=",1)[1] for l in Path("Makefile").read_text().splitlines() if l.startswith("TAGS=")))')"
  mobile_version="$(go list -m -f '{{.Version}}' github.com/sagernet/gomobile)"
  go install "github.com/sagernet/gomobile/cmd/gomobile@$mobile_version"
  go install "github.com/sagernet/gomobile/cmd/gobind@$mobile_version"
  mkdir -p bin
  CGO_LDFLAGS='-O2 -g -s -w -Wl,-z,max-page-size=16384' gomobile bind \
    -androidapi=24 -javapkg=com.hiddify.core -libname=hiddify-core \
    -tags="$tags" -trimpath -ldflags='-w -s -checklinkname=0 -buildid=' \
    -target=android/arm64 -o bin/hiddify-core.aar \
    github.com/sagernet/sing-box/experimental/libbox ./platform/mobile
)
mkdir -p android/app/libs
test -s hiddify-core/bin/hiddify-core.aar
cp hiddify-core/bin/hiddify-core.aar android/app/libs/hiddify-core.aar
sha256sum android/app/libs/hiddify-core.aar
