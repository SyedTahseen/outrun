#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
[ -n "$NDK" ] && [ -x "$NDK/ndk-build" ] || { echo "Set ANDROID_NDK_HOME to an NDK install"; exit 1; }

WORK="build-native"
rm -rf "$WORK"
mkdir -p "$WORK/jni"

git clone --recursive https://github.com/heiher/hev-socks5-tunnel.git "$WORK/jni/hev-socks5-tunnel"
if [ -n "${HEV_REF:-}" ]; then
  git -C "$WORK/jni/hev-socks5-tunnel" checkout "$HEV_REF"
  git -C "$WORK/jni/hev-socks5-tunnel" submodule update --init --recursive
fi

sed -i 's|^#define PKGNAME .*|#define PKGNAME app/xvpn/vpn|; s|^#define CLSNAME .*|#define CLSNAME Tun2Socks|' \
  "$WORK/jni/hev-socks5-tunnel/src/hev-jni.c"
grep -n "define PKGNAME\|define CLSNAME" "$WORK/jni/hev-socks5-tunnel/src/hev-jni.c"

cat > "$WORK/jni/Android.mk" <<'EOF'
TOP_PATH := $(call my-dir)
include $(TOP_PATH)/hev-socks5-tunnel/Android.mk
EOF

cat > "$WORK/jni/Application.mk" <<'EOF'
APP_OPTIM := release
APP_PLATFORM := android-26
APP_ABI := armeabi-v7a arm64-v8a x86_64
APP_CFLAGS := -O3 -DPKGNAME=app/xvpn/vpn -DCLSNAME=Tun2Socks
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
EOF

"$NDK/ndk-build" \
  NDK_PROJECT_PATH="$PWD/$WORK" \
  APP_BUILD_SCRIPT="$PWD/$WORK/jni/Android.mk" \
  NDK_APPLICATION_MK="$PWD/$WORK/jni/Application.mk" \
  -j"$(nproc)"

for abi in armeabi-v7a arm64-v8a x86_64; do
  so="$WORK/libs/$abi/libhev-socks5-tunnel.so"
  [ -f "$so" ] || { echo "Missing $so"; exit 1; }
  strings "$so" | grep -qx "app/xvpn/vpn/Tun2Socks" || { echo "ERROR: $so is not bound to app/xvpn/vpn/Tun2Socks"; exit 1; }
  mkdir -p "app/src/main/jniLibs/$abi"
  cp "$so" "app/src/main/jniLibs/$abi/"
done
echo "Native libs ready:"
ls -l app/src/main/jniLibs/*/
