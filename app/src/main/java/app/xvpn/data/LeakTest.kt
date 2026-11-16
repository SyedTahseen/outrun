package app.xvpn.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import app.xvpn.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

enum class Level { Ok, Warn, Fail, Info }

data class LeakCheck(val title: String, val detail: String, val level: Level)

object LeakTest {
    private val ipChars = Regex("^[0-9a-fA-F:.]{3,45}$")

    private fun fetch(url: String, socksPort: Int): String? = runCatching {
        val u = URL(url)
        val c = (if (socksPort > 0) u.openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
        else u.openConnection()) as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.inputStream.bufferedReader().use { it.readText() }.trim().takeIf { ipChars.matches(it) }
        } finally { c.disconnect() }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun vpnLink(ctx: Context): android.net.LinkProperties? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.allNetworks.firstOrNull { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
        return n?.let { cm.getLinkProperties(it) }
    }

    suspend fun run(ctx: Context, socksPort: Int, ipv6Mode: String, expectedDns: List<String>): List<LeakCheck> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<LeakCheck>()
            val lp = vpnLink(ctx)
            val routes = lp?.routes?.filter { it.isDefaultRoute }?.map { it.toString() }.orEmpty()
            val v4Default = routes.any { "0.0.0.0/0" in it }
            val v6Default = routes.any { "::/0" in it }

            val real4 = fetch("https://api.ipify.org", 0)
            val vpn4 = fetch("https://api.ipify.org", socksPort)
            out += when {
                vpn4 == null -> LeakCheck("IPv4 address", "Couldn't reach the test server through the VPN.", Level.Warn)
                real4 != null && real4 == vpn4 -> LeakCheck("IPv4 address", "The VPN exit address is the same as your real address ($real4).", Level.Fail)
                else -> LeakCheck("IPv4 address", "Apps appear as $vpn4" + (real4?.let { ", not your real address ($it)." } ?: "."), Level.Ok)
            }

            out += when {
                lp == null -> LeakCheck("Routing", "Couldn't find the VPN network.", Level.Warn)
                v4Default -> LeakCheck("Routing", "All IPv4 traffic is sent into the VPN.", Level.Ok)
                else -> LeakCheck("Routing", "IPv4 is not fully routed into the VPN. Split tunneling may be limiting it.", Level.Info)
            }

            if (lp != null) {
                val dns = lp.dnsServers.mapNotNull { it.hostAddress }
                val stray = dns.filter { it !in expectedDns }
                out += when {
                    dns.isEmpty() -> LeakCheck("DNS servers", "The VPN network has no DNS servers set.", Level.Warn)
                    stray.isNotEmpty() -> LeakCheck("DNS servers", "Unexpected resolvers in use: ${stray.joinToString()}.", Level.Fail)
                    else -> LeakCheck("DNS servers", "Lookups go to ${dns.joinToString()} inside the VPN.", Level.Ok)
                }
                if (Build.VERSION.SDK_INT >= 28 && lp.isPrivateDnsActive) {
                    out += LeakCheck(
                        "Private DNS",
                        "Android Private DNS is on" + (lp.privateDnsServerName?.let { " ($it)" } ?: "") +
                            ". Lookups use it instead of the DNS above, still through the VPN.",
                        Level.Info,
                    )
                }
            }

            if (ipv6Mode == "block") {
                out += if (v6Default || lp == null)
                    LeakCheck("IPv6", "IPv6 is blocked inside the VPN, so apps can only use IPv4.", Level.Ok)
                else LeakCheck("IPv6", "IPv6 is set to block, but no IPv6 route points into the VPN. IPv6 may bypass it.", Level.Warn)
            } else {
                val real6 = fetch("https://api6.ipify.org", 0)
                val vpn6 = fetch("https://api6.ipify.org", socksPort)
                out += when {
                    vpn6 != null && real6 != null && vpn6 == real6 -> LeakCheck("IPv6", "Your real IPv6 address ($real6) is exposed.", Level.Fail)
                    vpn6 != null -> LeakCheck("IPv6", "IPv6 goes through the VPN as $vpn6.", Level.Ok)
                    v6Default || lp == null -> LeakCheck("IPv6", "The server has no IPv6. IPv6 traffic is dropped inside the VPN, so nothing leaks.", Level.Ok)
                    else -> LeakCheck("IPv6", "No IPv6 route points into the VPN. IPv6 may bypass it.", Level.Warn)
                }
            }

            out += if (Build.VERSION.SDK_INT < 29)
                LeakCheck("Kill switch", "Can't be detected on this Android version.", Level.Info)
            else when {
                VpnState.lockdown.value -> LeakCheck("Kill switch", "Always-on VPN with \"Block connections without VPN\" is on.", Level.Ok)
                VpnState.alwaysOn.value -> LeakCheck("Kill switch", "Always-on VPN is on, but \"Block connections without VPN\" is off. Traffic can leave if the VPN drops.", Level.Warn)
                else -> LeakCheck("Kill switch", "Off. Traffic can leave unprotected if the VPN drops.", Level.Warn)
            }
            out
        }
}
