package app.xvpn.ui

import java.util.Locale

internal fun fmtSize(b: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = 0
    while (v >= 1000 && i < units.lastIndex) { v /= 1000; i++ }
    return if (i == 0) "$b B" else String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}
