package app.xvpn.vpn

/**
 * Bridge to hev-socks5-tunnel (tun2socks). It reads packets from the TUN fd and forwards them
 * to sing-box's local SOCKS inbound.
 *
 * The native library registers ALL FOUR methods below on this exact class with these exact
 * signatures (see hev-jni.c); if any is missing or has a different return type, System.loadLibrary
 * fails with JNI_ERR from JNI_OnLoad. scripts/build-native.sh builds the .so bound to this class name.
 */
object Tun2Socks {
    init { System.loadLibrary("hev-socks5-tunnel") }

    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int): Boolean
    @JvmStatic external fun TProxyStopService(): Boolean
    @JvmStatic external fun TProxyIsRunning(): Boolean
    @JvmStatic external fun TProxyGetStats(): LongArray   // [txPackets, txBytes, rxPackets, rxBytes]

    fun yaml(socksPort: Int, mtu: Int) = """
        tunnel:
          mtu: $mtu
          ipv4: 10.10.14.1
          ipv6: 'fd00:10:10:14::1'
        socks5:
          port: $socksPort
          address: 127.0.0.1
          udp: 'udp'
        misc:
          task-stack-size: 20480
          connect-timeout: 5000
          read-write-timeout: 60000
          log-level: warn
    """.trimIndent()
}
