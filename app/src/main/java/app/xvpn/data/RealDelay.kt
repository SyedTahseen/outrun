package app.xvpn.data

import android.content.Context
import app.xvpn.vpn.SingBoxCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * "Real delay" test: starts sing-box with the servers as outbounds and times an HTTPS request that goes
 * through each server (client -> server -> internet), instead of only connecting to the server's port.
 * Our app is excluded from the VPN tunnel, so this also works while connected.
 */
object RealDelay {
    private const val BATCH = 20          // servers per sing-box process
    private const val PARALLEL = 10       // probes at the same time inside one process
    private val URLS = listOf("https://www.gstatic.com/generate_204", "https://cp.cloudflare.com/generate_204")

    /** [onResult] is called on the main thread with (link, milliseconds) or (link, -1) for a failure/timeout. */
    suspend fun test(ctx: Context, links: List<String>, onResult: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        suspend fun emit(link: String, ms: Int) = withContext(Dispatchers.Main) { onResult(link, ms) }
        for (chunk in links.chunked(BATCH)) {
            val usable = ArrayList<Pair<String, JSONObject>>()
            for (l in chunk) {
                val o = runCatching { ConfigBuilder.outbound(l) }.getOrNull()
                if (o == null) emit(l, -1) else usable += l to o      // e.g. unsupported transport
            }
            if (usable.isNotEmpty()) runGroup(ctx, usable) { l, ms -> emit(l, ms) }
        }
    }

    private fun freePorts(n: Int): List<Int> {
        val set = LinkedHashSet<Int>()
        while (set.size < n) set += SingBoxCore.freePort()
        return set.toList()
    }

    private suspend fun runGroup(ctx: Context, items: List<Pair<String, JSONObject>>, emit: suspend (String, Int) -> Unit) {
        val ports = freePorts(items.size)
        val core = SingBoxCore(ctx, "singbox-test")
        try {
            // JSONObjects are re-tagged per config, so give the config its own copies
            core.start(ConfigBuilder.buildMulti(items.map { JSONObject(it.second.toString()) }, ports), ports.first())
        } catch (e: Exception) {
            core.stop()
            if (items.size == 1) { emit(items[0].first, -1); return }
            // one bad outbound makes sing-box refuse the whole config: split to isolate it
            val h = items.size / 2
            runGroup(ctx, items.subList(0, h), emit)
            runGroup(ctx, items.subList(h, items.size), emit)
            return
        }
        try {
            val sem = Semaphore(PARALLEL)
            coroutineScope {
                items.forEachIndexed { i, (link, _) ->
                    launch(Dispatchers.IO) { sem.withPermit { emit(link, probe(ports[i])) } }
                }
            }
        } finally {
            core.stop()
        }
    }

    /** Time of one HTTPS request through the SOCKS port, or -1. */
    private fun probe(port: Int): Int {
        for (url in URLS) {
            val ms = runCatching {
                val c = URL(url).openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))) as HttpURLConnection
                try {
                    c.connectTimeout = 5000
                    c.readTimeout = 5000
                    c.instanceFollowRedirects = false
                    val t = System.nanoTime()
                    val code = c.responseCode
                    if (code == 204 || code == 200) ((System.nanoTime() - t) / 1_000_000).toInt().coerceAtLeast(1) else -1
                } finally { c.disconnect() }
            }.getOrDefault(-1)
            if (ms > 0) return ms
        }
        return -1
    }
}
