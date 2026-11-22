package app.xvpn.vpn

import android.content.Context
import app.xvpn.data.AppLog
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

/**
 * Runs the same sing-box binary as [SingBoxCore], but as root and with its own TUN inbound (auto_route +
 * auto_redirect), so no VpnService is involved. The core is started from the shared root shell and keeps
 * running if the app process dies, so its pid is kept in a file and any stale core is stopped before a new one.
 */
class RootCore(private val context: Context) {
    companion object {
        const val IFACE = "outrun0"
        private const val DIR = "singbox-root"

        private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

        /** True when a sing-box core from an earlier app process is still running. Only uses su if a pid file exists. */
        fun isRunning(context: Context): Boolean {
            val dir = File(context.filesDir, DIR)
            if (!File(dir, "core.pid").exists()) return false
            val pid = q(File(dir, "core.pid").absolutePath)
            val r = RootShell.run(
                "P=\$(cat $pid 2>/dev/null); if grep -q libsingbox /proc/\$P/cmdline 2>/dev/null; then echo alive; else rm -f $pid; fi",
                15_000,
            )
            return "alive" in r.out
        }

        private fun stopScript(dir: File): String {
            val pid = q(File(dir, "core.pid").absolutePath)
            return listOf(
                "FORCED=0",
                "if [ -s $pid ]; then",
                "  P=\$(cat $pid)",
                "  if grep -q libsingbox /proc/\$P/cmdline 2>/dev/null; then",
                "    kill -TERM \$P 2>/dev/null",
                "    i=0",
                "    while kill -0 \$P 2>/dev/null && [ \$i -lt 25 ]; do sleep 0.2; i=\$((i+1)); done",
                "    if kill -0 \$P 2>/dev/null; then kill -KILL \$P 2>/dev/null; FORCED=1; sleep 0.3; fi",
                "  fi",
                "  rm -f $pid",
                "fi",
                "if [ \$FORCED = 1 ] || [ -d /sys/class/net/$IFACE ]; then",
                "  for fam in -4 -6; do",
                "    ip \$fam rule show 2>/dev/null | grep 'lookup 2022' | cut -d: -f1 | while read n; do ip \$fam rule del pref \$n; done",
                "    ip \$fam route flush table 2022 2>/dev/null",
                "  done",
                "  ip link del $IFACE 2>/dev/null",
                "fi",
                "if [ \$FORCED = 1 ]; then",
                "  for ipt in iptables ip6tables; do for t in nat mangle filter raw; do",
                "    \$ipt -t \$t -S 2>/dev/null | grep '^-A' | grep -i 'sing-box' | sed 's/^-A/-D/' | while read r; do \$ipt -t \$t \$r; done",
                "    \$ipt -t \$t -S 2>/dev/null | grep -i '^-N .*sing-box' | cut -d' ' -f2 | while read c; do \$ipt -t \$t -F \$c; \$ipt -t \$t -X \$c; done",
                "  done; done",
                "fi",
                "true",
            ).joinToString("\n")
        }
    }

    private val dir = File(context.filesDir, DIR)
    private val bin = File(context.applicationInfo.nativeLibraryDir, "libsingbox.so")

    @Volatile private var running = false
    @Volatile private var up = 0L
    @Volatile private var down = 0L
    private var useClash = false
    private var seenLog = 0

    /** `sing-box version` lists its build tags; the Clash API gives exact traffic numbers when it is compiled in. */
    fun hasClashApi(): Boolean = runCatching {
        val p = ProcessBuilder(bin.absolutePath, "version").redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        p.destroy()
        "with_clash_api" in text
    }.getOrDefault(false)

    fun start(configJson: String, port: Int, clashPort: Int) {
        if (!bin.exists()) error("sing-box binary missing")
        dir.mkdirs()
        val cfg = File(dir, "config.json").apply { writeText(configJson) }
        File(dir, "core.port").writeText(port.toString())
        File(dir, "core.clash").writeText(clashPort.toString())

        RootShell.run(stopScript(dir), 20_000)

        val script = listOf(
            "cd ${q(dir.absolutePath)} || exit 1",
            "rm -f core.log core.pid",
            "HOME=${q(dir.absolutePath)} DISABLE_NFTABLES=1 nohup ${q(bin.absolutePath)} run -c ${q(cfg.absolutePath)} -D ${q(dir.absolutePath)} > core.log 2>&1 &",
            "echo \$! > core.pid",
        ).joinToString("\n")
        AppLog.i(context, "root", "starting sing-box as root")
        val r = RootShell.run(script)
        if (!r.ok) error("Root shell: ${r.out.ifEmpty { "failed to start sing-box" }}")

        waitReady(port)
        AppLog.i(context, "root", "core ready, TUN $IFACE is up, traffic stats via ${if (clashPort > 0) "clash api" else "interface counters"}")
        flushConnections()
        running = true
        seenLog = 0
        startLogPump()
        useClash = clashPort > 0
        if (useClash) startTraffic(clashPort)
    }

    /** Re-attaches to a core that outlived the app process. Returns its SOCKS port, or null if it is not usable. */
    fun attach(): Int? {
        val port = runCatching { File(dir, "core.port").readText().trim().toInt() }.getOrNull() ?: return null
        val clash = runCatching { File(dir, "core.clash").readText().trim().toInt() }.getOrDefault(0)
        if (!isRunning(context)) return null
        val ok = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 500) } }.isSuccess
        if (!ok) return null
        val log = q(File(dir, "core.log").absolutePath)
        seenLog = RootShell.run("wc -l < $log 2>/dev/null", 5_000).out.trim().toIntOrNull() ?: 0
        running = true
        useClash = clash > 0
        startLogPump()
        if (useClash) startTraffic(clash)
        AppLog.i(context, "root", "re-attached to a running core")
        return port
    }

    private fun lastLog(): String {
        val r = RootShell.run("tail -n 5 ${q(File(dir, "core.log").absolutePath)} 2>/dev/null", 5_000)
        return r.out.lines().filter { it.isNotBlank() }.takeLast(4).joinToString(" | ").ifEmpty { "no output" }
    }

    private fun waitReady(port: Int) {
        val pid = q(File(dir, "core.pid").absolutePath)
        val probe = "P=\$(cat $pid 2>/dev/null); kill -0 \$P 2>/dev/null && echo alive; [ -d /sys/class/net/$IFACE ] && echo iface"
        val deadline = System.currentTimeMillis() + 20_000
        var socks = false
        var iface = false
        while (System.currentTimeMillis() < deadline) {
            val out = RootShell.run(probe, 5_000).out
            if ("alive" !in out) {
                Thread.sleep(150)
                error("sing-box exited: " + lastLog())
            }
            iface = "iface" in out
            if (!socks) socks = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) } }.isSuccess
            if (socks && iface) return
            Thread.sleep(250)
        }
        stop()
        error(
            (if (!iface) "TUN interface did not come up" else "sing-box did not start in time") + ": " + lastLog()
        )
    }

    private fun startTraffic(clashPort: Int) {
        thread(isDaemon = true, name = "root-traffic") {
            while (running) {
                try {
                    val c = URL("http://127.0.0.1:$clashPort/traffic").openConnection(Proxy.NO_PROXY) as HttpURLConnection
                    c.connectTimeout = 2000
                    c.readTimeout = 5000
                    c.inputStream.bufferedReader().use { r ->
                        while (running) {
                            val line = r.readLine() ?: break
                            val j = JSONObject(line)
                            up += j.optLong("up"); down += j.optLong("down")
                        }
                    }
                } catch (_: Exception) {
                }
                if (running) runCatching { Thread.sleep(1000) }
            }
        }
    }

    /** Cumulative (up, down) bytes since start. */
    fun totals(): Pair<Long, Long> {
        if (useClash) return up to down
        val r = RootShell.run(
            "cat /sys/class/net/$IFACE/statistics/tx_bytes /sys/class/net/$IFACE/statistics/rx_bytes 2>/dev/null", 3_000,
        )
        val n = r.out.lines().mapNotNull { it.trim().toLongOrNull() }
        return if (n.size >= 2) n[0] to n[1] else 0L to 0L
    }

    fun stop() {
        running = false
        RootShell.run(stopScript(dir), 20_000)
        drainLog()
        AppLog.i(context, "root", "core stopped")
        flushConnections()
    }

    /** sing-box's output goes to a root-owned file, so it is read through the root shell and copied into the app log. */
    @Synchronized
    private fun drainLog() {
        val log = q(File(dir, "core.log").absolutePath)
        val r = RootShell.run("n=\$(wc -l < $log 2>/dev/null); echo \$n; [ \"\$n\" -gt $seenLog ] && tail -n +${seenLog + 1} $log", 5_000)
        val lines = r.out.lines()
        val n = lines.firstOrNull()?.trim()?.toIntOrNull() ?: return
        lines.drop(1).filter { it.isNotBlank() }.forEach { AppLog.i(context, "core", it) }
        seenLog = n
    }

    private fun startLogPump() {
        thread(isDaemon = true, name = "root-log") {
            while (running) {
                runCatching { drainLog() }
                runCatching { Thread.sleep(3000) }
            }
        }
    }

    /**
     * Without a VpnService Android does not reset apps' open sockets when the route changes, so a browser keeps using
     * its old (direct or proxied) connections until restarted. Clear tracked connections, kill established TCP
     * sockets and drop the DNS cache; each step is best effort because tools differ between ROMs.
     */
    private fun flushConnections() {
        RootShell.run(
            listOf(
                "conntrack -F >/dev/null 2>&1",
                "ss -t -K state established >/dev/null 2>&1",
                "ndc resolver flushdefaultif >/dev/null 2>&1",
                "true",
            ).joinToString("\n"),
            10_000,
        )
    }
}
