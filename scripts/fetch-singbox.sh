#!/usr/bin/env bash
# Downloads the official sing-box Android CLI builds (SagerNet/sing-box releases) and drops them into
# app/src/main/jniLibs/<abi>/libsingbox.so. The app launches that binary as a child process.
# SINGBOX_VERSION: release without the "v" (default 1.14.4), or "latest".
# Pin it: config formats change between sing-box minor versions (ConfigBuilder targets 1.14.x).
set -euo pipefail
cd "$(dirname "$0")/.."

V="${SINGBOX_VERSION:-1.14.4}"
if [ "$V" = "latest" ]; then
  V="$(curl -fsSL -o /dev/null -w '%{url_effective}' https://github.com/SagerNet/sing-box/releases/latest | sed 's|.*/tag/v||')"
fi
echo "sing-box version: $V"

# jniLibs ABI  ->  sing-box asset arch
fetch() {
  local abi="$1" arch="$2" optional="${3:-}"
  local name="sing-box-${V}-android-${arch}"
  local url="https://github.com/SagerNet/sing-box/releases/download/v${V}/${name}.tar.gz"
  local tmp; tmp="$(mktemp -d)"
  echo "Downloading $url"
  if ! curl -fL --retry 4 --retry-delay 3 -o "$tmp/sb.tar.gz" "$url"; then
    rm -rf "$tmp"
    if [ "$optional" = "optional" ]; then
      echo "WARNING: no sing-box build for $abi in v$V - that ABI will be skipped"
      return 0
    fi
    echo "ERROR: could not download $url (check the version / asset name)"; exit 1
  fi
  tar -xzf "$tmp/sb.tar.gz" -C "$tmp"
  local bin; bin="$(find "$tmp" -type f -name sing-box | head -n1)"
  [ -n "$bin" ] || { echo "ERROR: no sing-box binary inside $name.tar.gz"; exit 1; }
  # must be an ELF executable
  [ "$(head -c4 "$bin" | od -An -tx1 | tr -d ' \n')" = "7f454c46" ] || { echo "ERROR: $bin is not an ELF file"; exit 1; }
  mkdir -p "app/src/main/jniLibs/$abi"
  cp "$bin" "app/src/main/jniLibs/$abi/libsingbox.so"
  chmod 755 "app/src/main/jniLibs/$abi/libsingbox.so"
  rm -rf "$tmp"
}

fetch arm64-v8a   arm64
fetch armeabi-v7a armv7 optional   # not published for every release
fetch x86_64      amd64

ls -l app/src/main/jniLibs/*/libsingbox.so
# the Gradle ABI split only builds ABIs that got a sing-box binary
