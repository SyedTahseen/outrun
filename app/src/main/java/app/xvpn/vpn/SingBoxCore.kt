package app.xvpn.vpn

import android.content.Context
import android.os.Build
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Runs the official sing-box CLI binary (packaged as lib/<abi>/libsingbox.so, see scripts/fetch-singbox.sh)
 * as a child process with only a local SOCKS inbound. Packets reach it through tun2socks.
 *
 * Its outbound sockets bypass the VPN because SingBoxVpnService excludes this app (same uid) from the tunnel.
 */
class SingBoxCore(private val context: Context, private val dirName: String = "singbox") {

    companion object {
        /** Ask the OS for a free loopback port so a leftover process can never block a new connection. */
        fun freePort(): Int = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    }

    private var process: Process? = null
    private val tail = ArrayDeque<String>()

    private fun alive(p: Process): Boolean = try { p.exitValue(); false } catch (_: IllegalThreadStateException) { true }

    val isRunning: Boolean get() = process?.let { alive(it) } == true

    fun start(configJson: String, port: Int) {
        val bin = File(context.applicationInfo.nativeLibraryDir, "libsingbox.so")
        if (!bin.exists()) error("sing-box binary missing for ${Build.SUPPORTED_ABIS.firstOrNull()}")

        val dir = File(context.filesDir, dirName).apply { mkdirs() }
        val cfg = File(dir, "config.json").apply { writeText(configJson) }

        val pb = ProcessBuilder(bin.absolutePath, "run", "-c", cfg.absolutePath, "-D", dir.absolutePath)
        pb.environment()["HOME"] = dir.absolutePath
        pb.redirectErrorStream(true)
        val p = pb.start()
        process = p

        // keep the last log lines so a failed start can show the real reason
        thread(isDaemon = true, name = "singbox-log") {
            runCatching {
                p.inputStream.bufferedReader().forEachLine {
                    synchronized(tail) { tail.addLast(it); while (tail.size > 20) tail.removeFirst() }
                }
            }
        }

        waitUntilListening(p, port)
    }

    private fun waitUntilListening(p: Process, port: Int) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (!alive(p)) {
                Thread.sleep(150) // let the log reader catch the last lines
                error("sing-box exited: " + lastLog())
            }
            try {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) }
                return
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
        stop()
        error("sing-box did not start in time: " + lastLog())
    }

    private fun lastLog(): String = synchronized(tail) { tail.takeLast(4).joinToString(" | ") }.ifEmpty { "no output" }

    fun stop() {
        val p = process ?: return
        process = null
        runCatching { p.destroy() }
        val deadline = System.currentTimeMillis() + 2000
        while (alive(p) && System.currentTimeMillis() < deadline) Thread.sleep(50)
    }
}
