package app.xvpn.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent event log for the Log screen and "Share logs". It keeps what Android's logcat drops or buries:
 * connect / disconnect steps, errors and sing-box's own output. Never write links, passwords or keys here: log the
 * server name and protocol only.
 */
object AppLog {
    private const val MAX_BYTES = 256 * 1024
    private const val FILE = "outrun.log"
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun i(ctx: Context, tag: String, msg: String) {
        runCatching {
            val f = File(ctx.applicationContext.filesDir, FILE)
            if (f.length() > MAX_BYTES) {
                val keep = f.readText().takeLast(MAX_BYTES / 2)
                f.writeText(keep.substringAfter('\n', keep))
            }
            f.appendText("${fmt.format(Date())} $tag: ${msg.replace('\n', ' ').take(600)}\n")
        }
    }

    @Synchronized
    fun tail(ctx: Context, lines: Int = 300): List<String> = runCatching {
        File(ctx.applicationContext.filesDir, FILE).takeIf { it.exists() }?.readLines()?.takeLast(lines).orEmpty()
    }.getOrDefault(emptyList())
}
