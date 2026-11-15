package app.xvpn.vpn

object Tun2Socks {
    init { System.loadLibrary("hev-socks5-tunnel") }

    @JvmStatic external fun TProxyStartService(configPath: String, fd: Int): Boolean
    @JvmStatic external fun TProxyStopService(): Boolean
    @JvmStatic external fun TProxyIsRunning(): Boolean
    @JvmStatic external fun TProxyGetStats(): LongArray

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
