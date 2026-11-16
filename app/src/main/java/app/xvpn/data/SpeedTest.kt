package app.xvpn.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

enum class SpeedPhase { Idle, Ping, Download, Upload, Done, Failed }

/** What a test measures. Alternate = download, then upload; Bidirectional = both at the same time. */
enum class TransmitMode { Download, Upload, Bidirectional, Alternate }

data class SpeedConfig(
    val mode: TransmitMode = TransmitMode.Alternate,
    val seconds: Int = 7,
    val streams: Int = 4,
    /** Run the test through the VPN when it is connected; otherwise direct. */
    val useVpn: Boolean = true,
)

val SpeedPhase.running get() = this == SpeedPhase.Ping || this == SpeedPhase.Download || this == SpeedPhase.Upload

data class SpeedUi(
    val phase: SpeedPhase = SpeedPhase.Idle,
    /** Current transfer speed in Mbps while a download or upload is being measured. */
    val live: Double = 0.0,
    /** Current upload speed while download and upload run together (Bidirectional). */
    val liveUp: Double = 0.0,
    val cfg: SpeedConfig = SpeedConfig(),
    val pingMs: Int = -1,
    val jitterMs: Int = -1,
    val downMbps: Double = -1.0,
    val upMbps: Double = -1.0,
    val viaVpn: Boolean = false,
    /** Live speed samples (Mbps, every 250 ms) of the download and upload phases, for the chart. */
    val downSeries: List<Float> = emptyList(),
    val upSeries: List<Float> = emptyList(),
    val log: List<String> = emptyList(),
    /** Why the last test failed, for the message on the start screen. */
    val error: String = "",
    val startedAt: Long = 0L,
    /** When the current phase began, for the per-phase timer. */
    val phaseStartedAt: Long = 0L,
)

/** One finished test, kept for the "Client history" sheet. */
data class SpeedResult(
    val at: Long,
    val downMbps: Double,
    val upMbps: Double,
    val pingMs: Int,
    val jitterMs: Int,
    val viaVpn: Boolean,
)

/**
 * Speed test against Cloudflare's public speed endpoints. Traffic goes through the local SOCKS proxy of the
 * running VPN core (the same way the IP lookup does), so the numbers are what the VPN server delivers.
 * It lives in a singleton so a running test and its results survive switching tabs.
 */
object SpeedTest {
    private const val BASE = "https://speed.cloudflare.com"
    private const val WARMUP_MS = 1_000L
    private const val DOWN_BYTES = 10_000_000   // many shorter requests, like Cloudflare's own test, instead of one very long one
    private const val CHUNK = 64 * 1024
    private const val UP_BYTES = CHUNK * 150L // a whole number of chunks

    val state = MutableStateFlow(SpeedUi())

    /** Settings picked on the start screen; kept here so they survive switching tabs. */
    val config = MutableStateFlow(SpeedConfig())

    /** Finished tests, newest first. Call [attach] once so they are loaded and saved. */
    val history = MutableStateFlow<List<SpeedResult>>(emptyList())
    private const val PREFS = "xvpn_speed"
    private const val MAX_HISTORY = 30
    private var appCtx: Context? = null

    fun attach(ctx: Context) {
        if (appCtx != null) return
        appCtx = ctx.applicationContext
        history.value = runCatching {
            val arr = JSONArray(appCtx!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("history", "[]") ?: "[]")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SpeedResult(o.optLong("t"), o.optDouble("d"), o.optDouble("u"), o.optInt("p", -1), o.optInt("j", -1), o.optBoolean("v"))
            }
        }.getOrDefault(emptyList())
    }

    private fun saveResult(r: SpeedResult) {
        val list = (listOf(r) + history.value).take(MAX_HISTORY)
        history.value = list
        val ctx = appCtx ?: return
        runCatching {
            val arr = JSONArray()
            list.forEach {
                arr.put(JSONObject().put("t", it.at).put("d", it.downMbps).put("u", it.upMbps).put("p", it.pingMs).put("j", it.jitterMs).put("v", it.viaVpn))
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("history", arr.toString()).apply()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun log(msg: String) {
        val line = synchronized(clock) { clock.format(Date()) } + "  " + msg
        state.update { it.copy(log = (it.log + line).takeLast(100)) }
    }

    fun start(socksPort: Int) {
        if (job?.isActive == true) return
        job = scope.launch {
            val cfg = config.value
            val phaseMs = cfg.seconds * 1_000L
            val t0 = System.currentTimeMillis()
            state.value = SpeedUi(phase = SpeedPhase.Ping, viaVpn = socksPort > 0, startedAt = t0, phaseStartedAt = t0, cfg = cfg)
            log("Test started " + if (socksPort > 0) "(through VPN)" else "(direct, VPN off)")
            try {
                val (ping, jitter) = measureLatency(socksPort)
                log("Latency: ping $ping ms, jitter $jitter ms")
                state.update { it.copy(pingMs = ping, jitterMs = jitter) }
                var down = -1.0
                var up = -1.0
                suspend fun runDown() {
                    state.update { it.copy(phase = SpeedPhase.Download, live = 0.0, phaseStartedAt = System.currentTimeMillis()) }
                    log("Download: ${cfg.streams} streams, ${cfg.seconds}s")
                    down = transfer(socksPort, upload = false, cfg.streams, phaseMs) { v ->
                        state.update { it.copy(live = v, downSeries = it.downSeries + v.toFloat()) }
                    }
                    log("Download: " + "%.1f".format(down) + " Mbps")
                    state.update { it.copy(downMbps = down, live = 0.0) }
                }
                suspend fun runUp() {
                    state.update { it.copy(phase = SpeedPhase.Upload, live = 0.0, phaseStartedAt = System.currentTimeMillis()) }
                    log("Upload: ${cfg.streams} streams, ${cfg.seconds}s")
                    up = transfer(socksPort, upload = true, cfg.streams, phaseMs) { v ->
                        state.update { it.copy(live = v, upSeries = it.upSeries + v.toFloat()) }
                    }
                    log("Upload: " + "%.1f".format(up) + " Mbps")
                    state.update { it.copy(upMbps = up, live = 0.0) }
                }
                when (cfg.mode) {
                    TransmitMode.Download -> runDown()
                    TransmitMode.Upload -> runUp()
                    TransmitMode.Alternate -> { runDown(); runUp() }
                    TransmitMode.Bidirectional -> coroutineScope {
                        state.update { it.copy(phase = SpeedPhase.Download, live = 0.0, liveUp = 0.0, phaseStartedAt = System.currentTimeMillis()) }
                        log("Download + upload together: ${cfg.streams} streams each, ${cfg.seconds}s")
                        val d = async {
                            transfer(socksPort, upload = false, cfg.streams, phaseMs) { v ->
                                state.update { it.copy(live = v, downSeries = it.downSeries + v.toFloat()) }
                            }
                        }
                        val u = async {
                            transfer(socksPort, upload = true, cfg.streams, phaseMs) { v ->
                                state.update { it.copy(liveUp = v, upSeries = it.upSeries + v.toFloat()) }
                            }
                        }
                        down = d.await()
                        up = u.await()
                        log("Download: " + "%.1f".format(down) + " Mbps, upload: " + "%.1f".format(up) + " Mbps")
                        state.update { it.copy(downMbps = down, upMbps = up, live = 0.0, liveUp = 0.0) }
                    }
                }
                state.update { it.copy(phase = SpeedPhase.Done, live = 0.0, liveUp = 0.0) }
                log("Finished")
                saveResult(SpeedResult(System.currentTimeMillis(), down, up, ping, jitter, socksPort > 0))
            } catch (e: CancellationException) {
                state.update { SpeedUi(log = it.log) }
                log("Stopped")
                throw e
            } catch (e: Exception) {
                val why = e.message ?: e.javaClass.simpleName
                state.update { it.copy(phase = SpeedPhase.Failed, live = 0.0, liveUp = 0.0, error = why) }
                log("Failed: $why")
            }
        }
    }

    fun stop() { job?.cancel() }

    /** Back to the start screen after a finished or failed test (the log is kept). */
    fun reset() {
        if (job?.isActive != true) state.update { SpeedUi(log = it.log) }
    }

    private fun nowMs() = System.nanoTime() / 1_000_000

    private val serverTime = Regex("cfRequestDuration;dur=([0-9.]+)")

    /**
     * Round-trip time of tiny requests on one kept-alive connection. The time the server spent on each request
     * (from its Server-Timing header) is subtracted, and the best sample is reported like other speed tests do.
     */
    private suspend fun measureLatency(socks: Int): Pair<Int, Int> {
        val times = ArrayList<Double>()
        repeat(11) {
            currentCoroutineContext().ensureActive()
            val t0 = System.nanoTime()
            val c = openConnection("$BASE/__down?bytes=0", socks, 5000)
            val server: Double
            try {
                c.inputStream.use { it.readBytes() }
                server = c.getHeaderField("Server-Timing")?.let { serverTime.find(it)?.groupValues?.get(1)?.toDoubleOrNull() } ?: 0.0
            } catch (e: Exception) {
                c.disconnect(); throw e
            }
            // Not disconnected on success: the next request reuses the connection, so it measures the round trip alone.
            times += ((System.nanoTime() - t0) / 1e6 - server).coerceAtLeast(1.0)
        }
        val rest = times.drop(1) // the first request pays for connecting
        val ping = rest.min().roundToInt()
        val jitter = rest.zipWithNext { a, b -> abs(a - b) }.average().roundToInt()
        return ping to jitter
    }

    /** Runs [streams] parallel transfers for [phaseMs] and returns the average speed in Mbps after the warm-up. */
    private suspend fun transfer(socks: Int, upload: Boolean, streams: Int, phaseMs: Long, onLive: (Double) -> Unit): Double = coroutineScope {
        val bytes = AtomicLong()
        val start = nowMs()
        val deadline = start + phaseMs
        val chunk = ByteArray(CHUNK).also { Random.nextBytes(it) }

        val lastError = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val workers = List(streams) {
            launch {
                var fails = 0
                while (isActive && nowMs() < deadline && fails < 5) {
                    try {
                        if (upload) uploadOnce(socks, chunk, bytes, deadline) else downloadOnce(socks, bytes, deadline)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        lastError.set(e.message ?: e.javaClass.simpleName)
                        fails++; delay(300)
                    }
                }
            }
        }

        var lastB = 0L
        var lastT = start
        var smooth = 0.0
        var warmB = 0L
        var warmT = 0L
        while (workers.any { it.isActive }) {
            delay(250)
            val t = nowMs()
            val b = bytes.get()
            val inst = (b - lastB) * 8.0 / 1e6 / ((t - lastT) / 1000.0)
            smooth = if (smooth == 0.0) inst else smooth * 0.6 + inst * 0.4
            onLive(smooth)
            lastB = b; lastT = t
            if (warmT == 0L && t - start >= WARMUP_MS) { warmB = b; warmT = t }
        }

        val endB = bytes.get()
        val endT = nowMs()
        if (warmT == 0L) { warmB = 0; warmT = start }
        if (endB <= 0 || endT <= warmT) throw IOException(lastError.get() ?: "no data")
        (endB - warmB) * 8.0 / 1e6 / ((endT - warmT) / 1000.0)
    }

    /** A request to the test server that asks for the raw bytes (no compression, so the count is what really crossed the network). */
    private fun speedConnection(url: String, socks: Int, timeoutMs: Int) = openConnection(url, socks, timeoutMs).also {
        it.setRequestProperty("Accept-Encoding", "identity")
        it.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Mobile Safari/537.36")
        it.setRequestProperty("Cache-Control", "no-cache")
    }

    private fun downloadOnce(socks: Int, bytes: AtomicLong, deadline: Long) {
        val c = speedConnection("$BASE/__down?bytes=$DOWN_BYTES&r=${System.nanoTime()}", socks, 10_000)
        try {
            if (c.responseCode != 200) throw IOException("Server answered HTTP ${c.responseCode}")
            val buf = ByteArray(CHUNK)
            c.inputStream.use { ins ->
                while (nowMs() < deadline) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    bytes.addAndGet(n.toLong())
                }
            }
        } finally {
            c.disconnect()
        }
    }

    private fun uploadOnce(socks: Int, chunk: ByteArray, bytes: AtomicLong, deadline: Long) {
        val c = speedConnection("$BASE/__up", socks, 10_000)
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setFixedLengthStreamingMode(UP_BYTES)
            var sent = 0L
            val out = c.outputStream
            while (sent < UP_BYTES && nowMs() < deadline) {
                out.write(chunk)
                sent += chunk.size
                bytes.addAndGet(chunk.size.toLong())
            }
        } finally {
            // Stopping before the declared length is sent is expected once the time is up.
            c.disconnect()
        }
    }
}
