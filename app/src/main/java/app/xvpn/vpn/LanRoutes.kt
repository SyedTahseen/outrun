package app.xvpn.vpn

object LanRoutes {
    private val excluded = listOf(
        "10.0.0.0" to 8, "172.16.0.0" to 12, "192.168.0.0" to 16, "169.254.0.0" to 16, "224.0.0.0" to 3,
    )

    const val IPV6_ADDRESS = "2000::"
    const val IPV6_PREFIX = 3

    val ipv4: List<Pair<String, Int>> by lazy {
        val ex = excluded.map { (a, len) -> val s = parse(a); s to (s + (1L shl (32 - len)) - 1) }
        val out = ArrayList<Pair<String, Int>>()
        fun walk(start: Long, len: Int) {
            val end = start + (1L shl (32 - len)) - 1
            if (ex.any { start >= it.first && end <= it.second }) return
            if (ex.none { start <= it.second && end >= it.first }) { out += format(start) to len; return }
            walk(start, len + 1)
            walk(start + (1L shl (31 - len)), len + 1)
        }
        walk(0L, 0)
        out
    }

    private fun parse(ip: String): Long = ip.split('.').fold(0L) { acc, part -> (acc shl 8) or part.toLong() }

    private fun format(v: Long): String =
        "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"
}
