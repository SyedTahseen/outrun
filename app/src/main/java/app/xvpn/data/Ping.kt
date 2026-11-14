package app.xvpn.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object Ping {
    /** TCP connect time to the server in ms, or -1 on failure/timeout. Our app is outside the VPN tunnel, so this is the real path to the server. */
    suspend fun tcp(host: String, port: Int, timeoutMs: Int = 3000): Int = withContext(Dispatchers.IO) {
        runCatching {
            val addr = InetSocketAddress(host, port)          // DNS resolved here, not counted in the time
            val t = System.nanoTime()
            Socket().use { it.connect(addr, timeoutMs) }
            ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1)
        }.getOrDefault(-1)
    }
}
