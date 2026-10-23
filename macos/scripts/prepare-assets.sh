#!/usr/bin/env bash
set -euo pipefail
MACOS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE="$MACOS/../assets/images/source/vetroff-launcher-mark.png"
ASSETS="$MACOS/VetrOFF/Assets.xcassets"
mkdir -p "$ASSETS/BrandMark.imageset" "$ASSETS/AppIcon.appiconset"
cp "$SOURCE" "$ASSETS/BrandMark.imageset/image.png"
for size in 16 32 128 256 512; do
    for scale in 1 2; do
        pixels=$((size * scale))
        sips -s format png --resampleHeightWidth "$pixels" "$pixels" "$SOURCE" \
            --out "$ASSETS/AppIcon.appiconset/icon_${size}_${scale}.png" >/dev/null
    done
done
