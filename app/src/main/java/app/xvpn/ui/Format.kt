package app.xvpn.ui

import java.util.Locale

private val UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

internal fun fmtSize(b: Long, binary: Boolean = false): String {
    val step = if (binary) 1024 else 1000
    var v = b.toDouble(); var i = 0
    while (v >= step && i < UNITS.lastIndex) { v /= step; i++ }
    return if (i == 0) "$b B" else String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, UNITS[i])
}

internal fun speedUnit(maxBytesPerSec: Long): Pair<String, Double> = when {
    maxBytesPerSec >= 1_000_000_000_000 -> "TB/s" to 1e12
    maxBytesPerSec >= 1_000_000_000 -> "GB/s" to 1e9
    maxBytesPerSec >= 1_000_000 -> "MB/s" to 1e6
    maxBytesPerSec >= 1_000 -> "KB/s" to 1e3
    else -> "B/s" to 1.0
}

internal fun fmtBytes(b: Long): String {
    var v = b.toDouble(); var i = 0
    while (v >= 1000 && i < UNITS.lastIndex) { v /= 1000; i++ }
    return String.format(Locale.US, "%.2f %s", v, UNITS[i])
}

internal fun fmtSpeed(b: Long): String {
    val (unit, factor) = speedUnit(b)
    return String.format(Locale.US, "%.2f %s", b / factor, unit)
}

internal fun fmtDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return when {
        h > 0 -> String.format(Locale.US, "%d h %02d min", h, m)
        m > 0 -> "$m min $sec sec"
        else -> "$sec sec"
    }
}
