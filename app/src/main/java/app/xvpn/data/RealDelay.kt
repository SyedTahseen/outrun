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
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * "Real delay" test: starts sing-box with the servers as outbounds and times an HTTPS request that goes
 * through each server (client -> server -> internet), instead of only connecting to the server's port.
 * Our app is excluded from the VPN tunnel, so this also works while connected.
 */
object RealDelay {
    private const val BATCH = 20          // servers per sing-box process
    private const val PARALLEL = 10       // probes at the same time inside one process
    private const val TIMEOUT_MS = 12_000   // whole request, same budget as v2rayNG's measureOutboundDelay
    const val DEFAULT_URL = "https://www.gstatic.com/generate_204"

    /** [onResult] is called on the main thread with (link, milliseconds) or (link, -1) for a failure/timeout. */
    suspend fun test(ctx: Context, links: List<String>, url: String = DEFAULT_URL, onResult: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        suspend fun emit(link: String, ms: Int) = withContext(Dispatchers.Main) { onResult(link, ms) }
        for (chunk in links.chunked(BATCH)) {
            val usable = ArrayList<Pair<String, JSONObject>>()
            for (l in chunk) {
                val o = runCatching { ConfigBuilder.outbound(l) }.getOrNull()
                if (o == null) emit(l, -1) else usable += l to o      // e.g. unsupported transport
            }
            if (usable.isNotEmpty()) runGroup(ctx, usable, url) { l, ms -> emit(l, ms) }
        }
    }

    private fun freePorts(n: Int): List<Int> {
        val set = LinkedHashSet<Int>()
        while (set.size < n) set += SingBoxCore.freePort()
        return set.toList()
    }

    private suspend fun runGroup(ctx: Context, items: List<Pair<String, JSONObject>>, url: String, emit: suspend (String, Int) -> Unit) {
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
            runGroup(ctx, items.subList(0, h), url, emit)
            runGroup(ctx, items.subList(h, items.size), url, emit)
            return
        }
        try {
            val sem = Semaphore(PARALLEL)
            coroutineScope {
                items.forEachIndexed { i, (link, _) ->
                    launch(Dispatchers.IO) { sem.withPermit { emit(link, probe(ports[i], url)) } }
                }
            }
        } finally {
            core.stop()
        }
    }

    /**
     * Time of one request through the SOCKS port, or -1. Measured like v2rayNG / Xray's measureOutboundDelay: a brand-new
     * connection (no keep-alive), the target hostname is handed to the proxy so the *server* resolves it (java's
     * SOCKS support would resolve it on the phone and add local DNS time), and the clock runs from dialing through
     * the proxy until the response status line arrives (proxy handshake + TLS + request). No retries, no fallback URL.
     */
    private fun probe(port: Int, url: String): Int = runCatching {
        val u = URL(url)
        val tls = u.protocol == "https"
        val host = u.host
        val tport = if (u.port > 0) u.port else if (tls) 443 else 80
        val path = u.file.ifEmpty { "/" }
        val start = System.nanoTime()
        val left = { (TIMEOUT_MS - (System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1) }
        Socket().use { raw ->
            raw.tcpNoDelay = true
            raw.connect(InetSocketAddress("127.0.0.1", port), left())
            raw.soTimeout = left()
            val rin = raw.getInputStream(); val rout = raw.getOutputStream()
            // SOCKS5: no auth, CONNECT <domain>:<port>
            rout.write(byteArrayOf(5, 1, 0)); rout.flush()
            if (rin.read() != 5 || rin.read() != 0) return@runCatching -1
            val h = host.toByteArray(Charsets.US_ASCII)
            rout.write(byteArrayOf(5, 1, 0, 3, h.size.toByte()) + h + byteArrayOf((tport shr 8).toByte(), tport.toByte())); rout.flush()
            if (rin.read() != 5 || rin.read() != 0) return@runCatching -1      // reply: version, status
            rin.read()                                                         // reserved
            when (rin.read()) {                                                // bound address, skipped
                1 -> rin.skip(4 + 2L)
                4 -> rin.skip(16 + 2L)
                3 -> rin.skip(rin.read() + 2L)
                else -> return@runCatching -1
            }
            val sock: Socket = if (tls) {
                val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, tport, false) as SSLSocket
                ssl.soTimeout = left()
                ssl.startHandshake()
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, ssl.session)) return@runCatching -1
                ssl
            } else raw
            sock.soTimeout = left()
            sock.getOutputStream().apply {
                write("GET $path HTTP/1.1\r\nHost: $host\r\nUser-Agent: Mozilla/5.0\r\nAccept: */*\r\nConnection: close\r\n\r\n".toByteArray()); flush()
            }
            val line = StringBuilder()
            val inp = sock.getInputStream()
            while (line.length < 64) { val c = inp.read(); if (c < 0 || c == '\n'.code) break; line.append(c.toChar()) }
            val code = line.toString().trim().split(' ').getOrNull(1)?.toIntOrNull() ?: return@runCatching -1
            if (code in 200..399) ((System.nanoTime() - start) / 1_000_000L).toInt().coerceAtLeast(1) else -1
        }
    }.getOrDefault(-1)
}
