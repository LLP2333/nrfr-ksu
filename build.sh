#!/usr/bin/env bash
# Build nrfr.dex and package the KernelSU module zip into out/.
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
PLATFORM="$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)"
BUILD_TOOLS="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
VERSION="$(sed -n 's/^version=//p' module/module.prop)"
VERSION_CODE="$(sed -n 's/^versionCode=//p' module/module.prop)"
REPO="LLP2333/nrfr-ksu"

rm -rf out && mkdir -p out/classes
javac --release 17 -cp "$PLATFORM/android.jar" -d out/classes $(find src -name '*.java')
"$BUILD_TOOLS/d8" --release --min-api 26 --lib "$PLATFORM/android.jar" \
    --output out $(find out/classes -name '*.class')
cp out/classes.dex module/nrfr.dex

ZIP="out/nrfr-ksu-$VERSION.zip"
(cd module && zip -qr9 "../$ZIP" . -x '.*')
# KernelSU update metadata, uploaded as a release asset (module.prop updateJson points here)
cat > out/update.json <<JSON
{
  "version": "$VERSION",
  "versionCode": $VERSION_CODE,
  "zipUrl": "https://github.com/$REPO/releases/download/$VERSION/$(basename "$ZIP")",
  "changelog": "https://raw.githubusercontent.com/$REPO/main/CHANGELOG.md"
}
JSON
echo "built $ZIP and out/update.json"
