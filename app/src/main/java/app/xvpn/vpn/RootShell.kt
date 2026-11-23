package app.xvpn.vpn

import java.io.BufferedWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RootResult(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
}

object RootShell {
    private const val MARK = "__OUTRUN_DONE__"
    private const val EOF = "\u0000EOF"

    private var proc: Process? = null
    private var stdin: BufferedWriter? = null
    private var queue: LinkedBlockingQueue<String>? = null

    private fun alive(): Boolean = proc?.let { runCatching { it.exitValue(); false }.getOrDefault(true) } ?: false

    @Synchronized
    private fun open(): Boolean {
        if (alive()) return true
        close()
        return try {
            val p = ProcessBuilder("su").redirectErrorStream(true).start()
            val q = LinkedBlockingQueue<String>()
            proc = p
            stdin = p.outputStream.bufferedWriter()
            queue = q
            thread(isDaemon = true, name = "root-shell-reader") {
                runCatching { p.inputStream.bufferedReader().forEachLine { q.put(it) } }
                q.put(EOF)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    @Synchronized
    fun close() {
        runCatching { stdin?.close() }
        runCatching { proc?.destroy() }
        proc = null; stdin = null; queue = null
    }

    @Synchronized
    fun run(cmd: String, timeoutMs: Long = 15_000): RootResult {
        if (!open()) return RootResult(-1, "su is not available")
        val q = queue ?: return RootResult(-1, "su is not available")
        val w = stdin ?: return RootResult(-1, "su is not available")
        q.clear()
        try {
            w.apply { write(cmd); write("\necho \"$MARK\$?\"\n"); flush() }
        } catch (e: Exception) {
            close()
            return RootResult(-1, "could not talk to su")
        }
        val out = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val left = deadline - System.currentTimeMillis()
            val line = if (left > 0) q.poll(left, TimeUnit.MILLISECONDS) else null
            if (line == null) { close(); return RootResult(-1, "timed out") }
            if (line == EOF) { close(); return RootResult(-1, out.toString().trim().ifEmpty { "su closed (root denied?)" }) }
            val at = line.indexOf(MARK)
            if (at >= 0) {
                if (at > 0) out.append(line, 0, at).append('\n')
                val code = line.substring(at + MARK.length).trim().toIntOrNull() ?: -1
                return RootResult(code, out.toString().trim())
            }
            out.append(line).append('\n')
        }
    }

    fun granted(timeoutMs: Long = 30_000): Boolean {
        val r = run("id -u", timeoutMs)
        return r.ok && r.out.lines().lastOrNull()?.trim() == "0"
    }
}
