#!/usr/bin/env bash
# Downloads the Xray gomobile AAR (2dust/AndroidLibXrayLite) into app/libs/libv2ray.aar.
# Set XRAY_AAR_VERSION to a release tag (e.g. 25.10.15) to pin; default is the latest release.
set -euo pipefail
cd "$(dirname "$0")/.."

mkdir -p app/libs
if [ -n "${XRAY_AAR_VERSION:-}" ]; then
  URL="https://github.com/2dust/AndroidLibXrayLite/releases/download/${XRAY_AAR_VERSION}/libv2ray.aar"
else
  URL="https://github.com/2dust/AndroidLibXrayLite/releases/latest/download/libv2ray.aar"
fi

echo "Downloading $URL"
curl -fL --retry 4 --retry-delay 3 -o app/libs/libv2ray.aar "$URL"

# sanity check: an AAR is a zip containing classes.jar and jni/ libs
unzip -l app/libs/libv2ray.aar | grep -q "classes.jar" || { echo "Downloaded file is not a valid AAR"; exit 1; }
unzip -l app/libs/libv2ray.aar | grep -E "jni/.*\.so" || echo "WARNING: no native libs found in AAR"
ls -l app/libs/libv2ray.aar
