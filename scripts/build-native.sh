#!/usr/bin/env bash
# Builds hev-socks5-tunnel (tun2socks) as libhev-socks5-tunnel.so for every ABI, with its JNI
# registration bound to app.xvpn.vpn.Tun2Socks, and copies the results to app/src/main/jniLibs/.
# Needs: git, and the Android NDK (ANDROID_NDK_HOME or ANDROID_NDK_ROOT).
# Set HEV_REF to a tag/commit to pin; default is the upstream default branch.
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

# Hardcode the JNI class in the source too, so it can't depend on -D flags being propagated
sed -i 's|^#define PKGNAME .*|#define PKGNAME app/xvpn/vpn|; s|^#define CLSNAME .*|#define CLSNAME Tun2Socks|' \
  "$WORK/jni/hev-socks5-tunnel/src/hev-jni.c"
grep -n "define PKGNAME\|define CLSNAME" "$WORK/jni/hev-socks5-tunnel/src/hev-jni.c"

cat > "$WORK/jni/Android.mk" <<'EOF'
TOP_PATH := $(call my-dir)
include $(TOP_PATH)/hev-socks5-tunnel/Android.mk
EOF

# PKGNAME/CLSNAME make the library register its native methods on app.xvpn.vpn.Tun2Socks
cat > "$WORK/jni/Application.mk" <<'EOF'
APP_OPTIM := release
APP_PLATFORM := android-24
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
  # the JNI class path must be baked into the library, otherwise it fails with JNI_ERR at load time
  strings "$so" | grep -qx "app/xvpn/vpn/Tun2Socks" || { echo "ERROR: $so is not bound to app/xvpn/vpn/Tun2Socks"; exit 1; }
  mkdir -p "app/src/main/jniLibs/$abi"
  cp "$so" "app/src/main/jniLibs/$abi/"
done
echo "Native libs ready:"
ls -l app/src/main/jniLibs/*/
