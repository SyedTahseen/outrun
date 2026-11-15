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

object RealDelay {
    private const val BATCH = 20
    private const val PARALLEL = 10
    private const val TIMEOUT_MS = 12_000
    const val DEFAULT_URL = "https://www.gstatic.com/generate_204"

    suspend fun test(ctx: Context, links: List<String>, url: String = DEFAULT_URL, onResult: (String, Int) -> Unit) = withContext(Dispatchers.IO) {
        suspend fun emit(link: String, ms: Int) = withContext(Dispatchers.Main) { onResult(link, ms) }
        for (chunk in links.chunked(BATCH)) {
            val usable = ArrayList<Pair<String, JSONObject>>()
            for (l in chunk) {
                val o = runCatching { ConfigBuilder.outbound(l) }.getOrNull()
                if (o == null) emit(l, -1) else usable += l to o
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

            core.start(ConfigBuilder.buildMulti(items.map { JSONObject(it.second.toString()) }, ports), ports.first())
        } catch (e: Exception) {
            core.stop()
            if (items.size == 1) { emit(items[0].first, -1); return }

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

            rout.write(byteArrayOf(5, 1, 0)); rout.flush()
            if (rin.read() != 5 || rin.read() != 0) return@runCatching -1
            val h = host.toByteArray(Charsets.US_ASCII)
            rout.write(byteArrayOf(5, 1, 0, 3, h.size.toByte()) + h + byteArrayOf((tport shr 8).toByte(), tport.toByte())); rout.flush()
            if (rin.read() != 5 || rin.read() != 0) return@runCatching -1
            rin.read()
            when (rin.read()) {
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
