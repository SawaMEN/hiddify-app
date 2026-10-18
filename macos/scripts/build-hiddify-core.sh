#!/usr/bin/env bash
set -euo pipefail

# Build the user's forked Go engine for native macOS in-process linking.
# This runs only on macOS and DOES NOT alter or build the Android client.
CORE_DIR="${VETROFF_CORE_DIR:?Set VETROFF_CORE_DIR to a checkout of SawaMEN/hiddify-core}"
OUT_DIR="${VETROFF_CORE_OUT:-$(pwd)/macos/build/Core}"
CORE_EXPECTED_SHA="${VETROFF_CORE_SHA:-ddb5a020fc54ea22c0d2a2fd699839270745d664}"

if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "This build must run on macOS with Xcode." >&2
    exit 1
fi
if [[ ! -f "${CORE_DIR}/Makefile" ]] || [[ ! -f "${CORE_DIR}/go.mod" ]]; then
    echo "VETROFF_CORE_DIR is not a hiddify-core source checkout." >&2
    exit 1
fi
CORE_DIR="$(cd "${CORE_DIR}" && pwd)"
mkdir -p "${OUT_DIR}"
OUT_DIR="$(cd "${OUT_DIR}" && pwd)"

actual_sha="$(git -C "${CORE_DIR}" rev-parse HEAD)"
if [[ "${actual_sha}" != "${CORE_EXPECTED_SHA}" ]]; then
    echo "Refusing unpinned core revision: got ${actual_sha}; expected ${CORE_EXPECTED_SHA}" >&2
    exit 1
fi

GO_TAGS="$(sed -n 's/^TAGS=//p' "${CORE_DIR}/Makefile" | head -n 1)"
MAC_TAGS="$(sed -n 's/^MACOS_ADD_TAGS=//p' "${CORE_DIR}/Makefile" | head -n 1)"
if [[ -z "${GO_TAGS}" ]] || [[ -z "${MAC_TAGS}" ]]; then
    echo "Core Makefile build tags not found." >&2
    exit 1
fi

cd "${CORE_DIR}"
make lib_install
GOMOBILE="$(go env GOPATH)/bin/gomobile"
if [[ ! -x "${GOMOBILE}" ]]; then
    echo "gomobile was not installed by make lib_install." >&2
    exit 1
fi

# This produces a direct gomobile ObjC/Swift library. No separate daemon,
# localhost proxy, gRPC transport or Flutter runtime is introduced.
"${GOMOBILE}" bind -v \
    -target=macos/arm64 \
    -libname=HiddifyCore \
    -tags="${GO_TAGS},${MAC_TAGS}" \
    -trimpath \
    -ldflags="-s -w" \
    -macosversion=14.0 \
    -o "${OUT_DIR}/HiddifyCore.xcframework" \
    github.com/sagernet/sing-box/experimental/libbox \
    ./platform/mobile

test -d "${OUT_DIR}/HiddifyCore.xcframework"
echo "Built: ${OUT_DIR}/HiddifyCore.xcframework"
