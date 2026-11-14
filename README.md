# SingBox VPN (Android)

Compose UI (Home / Servers / Menu) + **sing-box** core, for your own vless / trojan / shadowsocks / vmess
proxies. Config import via clipboard and subscription URLs.

Data path: apps -> VpnService TUN -> hev-socks5-tunnel -> sing-box SOCKS (127.0.0.1:<free port>) -> your server.

sing-box is the official CLI build from SagerNet/sing-box releases, packaged as `libsingbox.so` per ABI and
started as a child process by the VPN service (so there is no gomobile/AAR to build or keep in sync).

## Build with GitHub Actions (recommended)

1. Push this folder to a GitHub repo (branch `main`).
2. Open the **Actions** tab -> **Build APK** runs automatically (or press *Run workflow*).
3. In the run's Artifacts, click an APK directly (`SingBoxVpn-<ref>-arm64-v8a.apk` etc.) - no zip.
   Most phones need `arm64-v8a`.
4. Push a tag like `v1.0` to also attach the APKs to a GitHub Release.

The workflow:
- `scripts/fetch-singbox.sh` downloads sing-box (default **1.14.4**) for arm64-v8a and x86_64, plus armeabi-v7a when
  upstream publishes it (1.14.4 has no 32-bit ARM build, so no armeabi-v7a APK is produced for it)
- `scripts/build-native.sh` builds hev-socks5-tunnel (tun2socks) with the NDK, bound to `app.xvpn.vpn.Tun2Socks`
- Gradle 8.9 builds `:app:assembleRelease` (signed with the debug key, for personal use)

"Run workflow" lets you set the sing-box version and the hev-socks5-tunnel ref. Config formats change between
sing-box minor versions; `ConfigBuilder.kt` targets 1.14.x, so keep the version pinned.

## Build locally

Run the two scripts (need curl, git, an Android NDK via `ANDROID_NDK_HOME`), then open the folder in
Android Studio (Koala+) and run. First connect asks for VPN + notification permission.

## Using it

- Servers tab -> "+ Paste link" after copying a `vless://`, `trojan://`, `ss://` or `vmess://` link
- Menu tab -> "Import from clipboard" (multi-line lists / base64 subscriptions) or add a subscription URL
- Home tab -> tap the power button

## sing-box limitations

Stock sing-box does not support these, so such links fail with a clear error instead of connecting:
- XHTTP / SplitHTTP, mKCP and QUIC transports (Xray-only)
- VLESS `encryption` other than `none` (post-quantum)

## Status

Not compiled or run by the author (no Android SDK/network where it was written), so the first CI run may need
small fixes. Likely spots: the tun2socks JNI binding (`-DPKGNAME/-DCLSNAME` in `scripts/build-native.sh`) and the
sing-box asset names in `scripts/fetch-singbox.sh`. Split tunneling, ping tests and QR import are not implemented.
