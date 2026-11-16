package app.xvpn.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

enum class SpeedPhase { Idle, Ping, Download, Upload, Done, Failed }

val SpeedPhase.running get() = this == SpeedPhase.Ping || this == SpeedPhase.Download || this == SpeedPhase.Upload

data class SpeedUi(
    val phase: SpeedPhase = SpeedPhase.Idle,
    /** Current transfer speed in Mbps while a download or upload is being measured. */
    val live: Double = 0.0,
    val pingMs: Int = -1,
    val jitterMs: Int = -1,
    val downMbps: Double = -1.0,
    val upMbps: Double = -1.0,
    val viaVpn: Boolean = false,
    /** Live speed samples (Mbps, every 250 ms) of the download and upload phases, for the chart. */
    val downSeries: List<Float> = emptyList(),
    val upSeries: List<Float> = emptyList(),
    val log: List<String> = emptyList(),
    val startedAt: Long = 0L,
)

/**
 * Speed test against Cloudflare's public speed endpoints. Traffic goes through the local SOCKS proxy of the
 * running VPN core (the same way the IP lookup does), so the numbers are what the VPN server delivers.
 * It lives in a singleton so a running test and its results survive switching tabs.
 */
object SpeedTest {
    private const val BASE = "https://speed.cloudflare.com"
    const val PHASE_SECONDS = 7
    private const val PHASE_MS = PHASE_SECONDS * 1_000L
    private const val WARMUP_MS = 1_000L
    const val STREAMS = 4
    private const val DOWN_BYTES = 50_000_000
    private const val CHUNK = 64 * 1024
    private const val UP_BYTES = CHUNK * 150L // a whole number of chunks

    val state = MutableStateFlow(SpeedUi())

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
            state.value = SpeedUi(phase = SpeedPhase.Ping, viaVpn = socksPort > 0, startedAt = System.currentTimeMillis())
            log("Test started " + if (socksPort > 0) "(through VPN)" else "(direct, VPN off)")
            try {
                val (ping, jitter) = measureLatency(socksPort)
                log("Latency: ping $ping ms, jitter $jitter ms")
                state.update { it.copy(pingMs = ping, jitterMs = jitter, phase = SpeedPhase.Download) }
                log("Download: $STREAMS streams, ${PHASE_SECONDS}s")
                val down = transfer(socksPort, upload = false) { v ->
                    state.update { it.copy(live = v, downSeries = it.downSeries + v.toFloat()) }
                }
                log("Download: " + "%.1f".format(down) + " Mbps")
                state.update { it.copy(downMbps = down, phase = SpeedPhase.Upload, live = 0.0) }
                log("Upload: $STREAMS streams, ${PHASE_SECONDS}s")
                val up = transfer(socksPort, upload = true) { v ->
                    state.update { it.copy(live = v, upSeries = it.upSeries + v.toFloat()) }
                }
                log("Upload: " + "%.1f".format(up) + " Mbps")
                state.update { it.copy(upMbps = up, phase = SpeedPhase.Done, live = 0.0) }
                log("Finished")
            } catch (e: CancellationException) {
                state.update { SpeedUi(log = it.log) }
                log("Stopped")
                throw e
            } catch (e: Exception) {
                state.update { it.copy(phase = SpeedPhase.Failed, live = 0.0) }
                log("Failed: " + (e.message ?: e.javaClass.simpleName))
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

    /** Runs [STREAMS] parallel transfers for [PHASE_MS] and returns the average speed in Mbps after the warm-up. */
    private suspend fun transfer(socks: Int, upload: Boolean, onLive: (Double) -> Unit): Double = coroutineScope {
        val bytes = AtomicLong()
        val start = nowMs()
        val deadline = start + PHASE_MS
        val chunk = ByteArray(CHUNK).also { Random.nextBytes(it) }

        val workers = List(STREAMS) {
            launch {
                var fails = 0
                while (isActive && nowMs() < deadline && fails < 2) {
                    try {
                        if (upload) uploadOnce(socks, chunk, bytes, deadline) else downloadOnce(socks, bytes, deadline)
                    } catch (e: IOException) {
                        fails++; delay(200)
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
        if (endB <= 0 || endT <= warmT) throw IOException("no data")
        (endB - warmB) * 8.0 / 1e6 / ((endT - warmT) / 1000.0)
    }

    private fun downloadOnce(socks: Int, bytes: AtomicLong, deadline: Long) {
        val c = openConnection("$BASE/__down?bytes=$DOWN_BYTES", socks, 6000)
        try {
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
        val c = openConnection("$BASE/__up", socks, 6000)
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
