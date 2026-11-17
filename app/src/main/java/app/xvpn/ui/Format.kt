package app.xvpn.ui

import java.util.Locale

private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

internal fun fmtSize(b: Long, binary: Boolean = false): String {
    val step = if (binary) 1024 else 1000
    var v = b.toDouble(); var i = 0
    while (v >= step && i < UNITS.lastIndex) { v /= step; i++ }
    return if (i == 0) "$b B" else String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, UNITS[i])
}

internal fun fmtKb(b: Long): String = String.format(Locale.US, "%.2f KB/s", b / 1000.0)

internal fun fmtDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return when {
        h > 0 -> String.format(Locale.US, "%d h %02d min", h, m)
        m > 0 -> "$m min $sec sec"
        else -> "$sec sec"
    }
}

internal fun fmtAgo(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60000
    return when { m < 1 -> "just now"; m < 60 -> "${m}m ago"; m < 1440 -> "${m / 60}h ago"; else -> "${m / 1440}d ago" }
}
