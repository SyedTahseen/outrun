# Outrun VPN (Android)

Compose UI (Home / Servers / Settings) + **sing-box** core, for your own vless / trojan / shadowsocks / vmess /
hysteria2 / tuic / wireguard proxies. Config import via clipboard and subscription URLs.

Data path: apps -> VpnService TUN -> hev-socks5-tunnel -> sing-box SOCKS (127.0.0.1:<free port>) -> your server.

sing-box is the official CLI build from SagerNet/sing-box releases, packaged as `libsingbox.so` per ABI and
started as a child process by the VPN service (so there is no gomobile/AAR to build or keep in sync).

## Build with GitHub Actions (recommended)

1. Push this folder to a GitHub repo (branch `main`).
2. Open the **Actions** tab -> **Build APK** runs automatically (or press *Run workflow*).
3. In the run's Artifacts, click an APK directly (`OutrunVpn-<ref>-arm64-v8a.apk` etc.) - no zip.
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

## Home-screen widget

Long-press the home screen -> Widgets -> **Outrun VPN** (2x2 by default). Long-press the placed widget to resize it
from 2x1 up to about 4x3 cells; the layout stretches and the server line is hidden when it is only one row tall.
It shows VPN ON / OFF / connecting, the server address (or the selected server name when off), and a power button
that connects to the selected server or disconnects. If no server is selected, or the system VPN permission has not
been granted yet, the power button opens the app instead.

## Using it

- Servers tab -> "+" menu: scan a QR code, paste from clipboard, import a file, add manually, or add a subscription link.
  Supported links: `vless://`, `trojan://`, `ss://`, `vmess://`, `hysteria2://` (`hy2://`), `tuic://`, `wireguard://` (`wg://`),
  plus WireGuard `.conf` files, multi-line lists and base64 subscriptions
- Home tab -> tap Connect (tap the server card while connected for statistics and the leak test)
- Settings tab -> split tunneling, kill switch help, DNS, IPv6, latency test URL, connect on launch, usage & history

## Protocols

- **Hysteria2**: `hysteria2://auth@host:port/?sni=&insecure=1&obfs=salamander&obfs-password=&mport=20000-30000#name`
- **TUIC v5**: `tuic://uuid:password@host:port?sni=&alpn=h3&congestion_control=bbr&udp_relay_mode=native#name`
- **WireGuard**: `wireguard://<private-key>@host:port?publickey=&address=10.0.0.2/32,fd00::2/128&mtu=1420&reserved=1,2,3&presharedkey=#name`
  (built as a sing-box *endpoint*; needs a sing-box build with the `with_wireguard` tag, which the official releases have;
  DNS is sent through the tunnel for WireGuard servers)

## Leak protection, usage and notification

- **Home -> tap the server card while connected -> Leak protection -> Run test**: compares your real IP with the VPN exit, checks that routes and DNS
  point into the VPN, IPv6 handling and whether Android's Always-on / lockdown (kill switch) is on.
  The app itself is excluded from the tunnel, so the test reads the device's VPN network settings plus requests sent
  through the proxy, it can't observe other apps' packets.
- **Settings -> IPv6**: tunnel (default) or block.
- **Settings -> Usage & history**: per-day totals, 14-day chart, last connections, and per-app usage (needs Usage access).
  Per-app numbers come from Android's NetworkStatsManager and are approximate (hourly buckets).
- **Notification**: live speed, Disconnect, and Switch server (cycles through favorites, or all servers if you have fewer than 2 favorites).

## Features added from the Proton VPN comparison

- **NetShield** (Settings -> NetShield): blocks ads, trackers and malware while connected by using AdGuard DNS as the VPN's DNS; an extra switch uses AdGuard's family servers to block adult content too. It replaces the DNS setting while on. DNS-level only: no blocked-count statistics.
- **Auto-reconnect** (Settings -> Auto-reconnect, on by default): rebuilds the tunnel when the real network changes (Wi-Fi <-> mobile) or returns after a drop, probes the tunnel every 60 s (2 failures -> reconnect), and when a server fails to connect tries up to 3 other servers (favorites first). The server you picked stays selected. The service also starts correctly when Android launches it as an Always-on VPN.
- **Auto-connect on boot** (Settings -> Auto-connect): needs the VPN permission to have been granted once.
- **Quick Settings tile**: add "Outrun VPN" from the notification shade's edit menu. Opens the app when no server is selected or the VPN permission is missing.
- **Recents**: the last 3 servers appear on Home while disconnected for one-tap reconnect.
- **Fastest** (first row of the Servers list, like Proton's "Fastest country" row): reuses a latency test from the last 10 minutes or runs one first. While connecting is slow, Home offers "Try another server" (random).
- **Local network (LAN)** (Settings): keeps 10/8, 172.16/12, 192.168/16, link-local and multicast outside the tunnel (works on every Android version, not only 13+). Off by default.
- **MTU** (Settings): 1500 (default) down to 1280.

All of the above was written without a compiler, like the rest of the project, so expect small fixes on the first build.

## sing-box limitations

Stock sing-box does not support these, so such links fail with a clear error instead of connecting:
- XHTTP / SplitHTTP, mKCP and QUIC transports (Xray-only)
- VLESS `encryption` other than `none` (post-quantum)

## Status

Not compiled or run by the author (no Android SDK/network where it was written), so the first CI run may need
small fixes. Likely spots: the tun2socks JNI binding (`-DPKGNAME/-DCLSNAME` in `scripts/build-native.sh`) and the
sing-box asset names in `scripts/fetch-singbox.sh`. The newer features (Hysteria2/TUIC/WireGuard, leak test, usage history, notification actions) were also written without a compiler, so expect to fix a few small compile errors on the first build.
