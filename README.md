# Xray VPN (Android)

Compose UI modelled on your design (Home / Servers / Menu) + Xray core, for your own
vless / trojan / shadowsocks / vmess proxies. The "Premium" screens are replaced by
config import (clipboard + subscription URLs).

Data path: apps -> VpnService TUN -> hev-socks5-tunnel -> Xray SOCKS (127.0.0.1:10808) -> your server.

## Build with GitHub Actions (recommended)

1. Push this folder to a GitHub repo (branch `main`).
2. Open the **Actions** tab -> **Build APK** runs automatically (or press *Run workflow*).
3. Download `XrayVpn-apk` from the run's Artifacts and install the APK.
4. Push a tag like `v1.0` to also attach the APK to a GitHub Release.

The workflow does everything the app needs:
- `scripts/fetch-xray.sh` downloads `libv2ray.aar` (Xray core) from 2dust/AndroidLibXrayLite releases
- `scripts/build-native.sh` builds hev-socks5-tunnel (tun2socks) with the NDK for arm64-v8a, armeabi-v7a, x86_64,
  bound to the class `app.xvpn.vpn.Tun2Socks`
- Gradle 8.9 builds `:app:assembleRelease` (signed with the debug key, for personal use)

"Run workflow" lets you pin the Xray AAR release tag and the hev-socks5-tunnel ref. Pinning both is wise
once you have a working build, since upstream changes can break the API this code targets.

## Build locally

Run the two scripts (need curl, git, an Android NDK via `ANDROID_NDK_HOME`), then open the folder in
Android Studio (Koala+) and run. First connect asks for VPN + notification permission.

## Using it

- Servers tab -> "+ Paste link" after copying a `vless://`, `trojan://`, `ss://` or `vmess://` link
- Menu tab -> "Import from clipboard" (multi-line lists / base64 subscriptions) or add a subscription URL
- Home tab -> tap the power button

## Status

Not compiled or run by the author (no Android SDK/network where it was written), so the first CI run may need
small fixes. Most likely spots: `XrayCore.kt` if the AAR's API differs from the version it targets, and the
tun2socks JNI binding (`-DPKGNAME/-DCLSNAME` in `scripts/build-native.sh`). Split tunneling, ping tests and QR import
are not implemented.
