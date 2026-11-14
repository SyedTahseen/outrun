package app.xvpn.vpn

/**
 * Bridge to hev-socks5-tunnel (the same tun2socks v2rayNG uses). It reads packets from the TUN fd
 * and forwards them to Xray's local SOCKS inbound.
 *
 * You must build libhev-socks5-tunnel.so for this exact class name (see README) and place it in
 * app/src/main/jniLibs/<abi>/. The JNI functions below are registered against this class.
 */
object Tun2Socks {
    init { System.loadLibrary("hev-socks5-tunnel") }

    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int)
    @JvmStatic external fun TProxyStopService()
    @JvmStatic external fun TProxyGetStats(): LongArray?   // [txPackets, txBytes, rxPackets, rxBytes]

    fun yaml(socksPort: Int, mtu: Int) = """
        tunnel:
          mtu: $mtu
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
