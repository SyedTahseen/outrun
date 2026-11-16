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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
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
)

/**
 * Speed test against Cloudflare's public speed endpoints. Traffic goes through the local SOCKS proxy of the
 * running VPN core (the same way the IP lookup does), so the numbers are what the VPN server delivers.
 * It lives in a singleton so a running test and its results survive switching tabs.
 */
object SpeedTest {
    private const val BASE = "https://speed.cloudflare.com"
    private const val PHASE_MS = 7_000L
    private const val WARMUP_MS = 1_000L
    private const val STREAMS = 4
    private const val DOWN_BYTES = 50_000_000
    private const val CHUNK = 64 * 1024
    private const val UP_BYTES = CHUNK * 150L // a whole number of chunks

    val state = MutableStateFlow(SpeedUi())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start(socksPort: Int) {
        if (job?.isActive == true) return
        job = scope.launch {
            state.value = SpeedUi(phase = SpeedPhase.Ping, viaVpn = socksPort > 0)
            try {
                val (ping, jitter) = measureLatency(socksPort)
                state.update { it.copy(pingMs = ping, jitterMs = jitter, phase = SpeedPhase.Download) }
                val down = transfer(socksPort, upload = false) { v -> state.update { it.copy(live = v) } }
                state.update { it.copy(downMbps = down, phase = SpeedPhase.Upload, live = 0.0) }
                val up = transfer(socksPort, upload = true) { v -> state.update { it.copy(live = v) } }
                state.update { it.copy(upMbps = up, phase = SpeedPhase.Done, live = 0.0) }
            } catch (e: CancellationException) {
                state.update { it.copy(phase = SpeedPhase.Idle, live = 0.0) }
                throw e
            } catch (e: Exception) {
                state.update { it.copy(phase = SpeedPhase.Failed, live = 0.0) }
            }
        }
    }

    fun stop() { job?.cancel() }

    private fun nowMs() = System.nanoTime() / 1_000_000

    /** Median round-trip time and jitter of small requests on one kept-alive connection. */
    private suspend fun measureLatency(socks: Int): Pair<Int, Int> {
        val times = ArrayList<Long>()
        repeat(7) {
            currentCoroutineContext().ensureActive()
            val t0 = nowMs()
            val c = openConnection("$BASE/__down?bytes=0", socks, 5000)
            try {
                c.inputStream.use { it.readBytes() }
            } catch (e: Exception) {
                c.disconnect(); throw e
            }
            // Not disconnected on success: the next request reuses the connection, so it measures the round trip alone.
            times += nowMs() - t0
        }
        val rest = times.drop(1)
        val ping = rest.sorted()[rest.size / 2].toInt()
        val jitter = rest.zipWithNext { a, b -> abs(a - b) }.average().toInt()
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
