package app.xvpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.math.BigInteger
import java.net.Inet6Address
import java.net.InetAddress

object LanRoutes {
    const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"

    private val privateV4 = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16", "224.0.0.0/3",
    )
    private val privateV6 = listOf("fc00::/7", "fe80::/10", "ff00::/8")
    private val loopback = listOf("127.0.0.0/8", "::1/128")

    val excludedCidrs: List<String> = privateV4
    val excludedCidrsV6: List<String> = privateV6

    private val literal = Regex("^[0-9a-fA-F:.]+$")

    private class Block(val v6: Boolean, val start: BigInteger, val end: BigInteger)

    private fun bits(v6: Boolean) = if (v6) 128 else 32
    private fun topOf(v6: Boolean) = BigInteger.ONE.shiftLeft(bits(v6)) - BigInteger.ONE

    private fun parse(cidr: String): Block? {
        val addr = cidr.substringBefore('/').trim().substringBefore('%')
        if (addr.isEmpty() || !literal.matches(addr)) return null
        val ip = runCatching { InetAddress.getByName(addr) }.getOrNull() ?: return null
        val raw = ip.address
        val v6 = raw.size == 16
        val len = cidr.substringAfter('/', "").trim().toIntOrNull() ?: bits(v6)
        if (len !in 0..bits(v6)) return null
        val value = BigInteger(1, raw)
        val size = BigInteger.ONE.shiftLeft(bits(v6) - len)
        val start = value.andNot(size - BigInteger.ONE)
        return Block(v6, start, start + size - BigInteger.ONE)
    }

    private fun format(v6: Boolean, value: BigInteger): String {
        val n = bits(v6) / 8
        val src = value.toByteArray()
        val raw = ByteArray(n)
        for (i in 0 until minOf(n, src.size)) raw[n - 1 - i] = src[src.size - 1 - i]
        return InetAddress.getByAddress(raw).hostAddress.orEmpty().substringBefore('%')
    }

    private fun cidr(v6: Boolean, start: BigInteger, len: Int) = "${format(v6, start)}/$len"

    fun routes(v6: Boolean, exclusions: List<String>): List<Pair<String, Int>> {
        val plain = complement(v6, exclusions)
        val loop = loopback.mapNotNull { parse(it) }.first { it.v6 == v6 }
        val hitsLoopback = plain.any { (addr, _) ->
            val a = parse(addr)?.start ?: return@any false
            a >= loop.start && a <= loop.end
        }
        return if (hitsLoopback) complement(v6, exclusions + loopback) else plain
    }

    private fun complement(v6: Boolean, exclusions: List<String>): List<Pair<String, Int>> {
        val ex = exclusions.mapNotNull { parse(it) }.filter { it.v6 == v6 }.sortedBy { it.start }
        val merged = ArrayList<Pair<BigInteger, BigInteger>>()
        for (b in ex) {
            val last = merged.lastOrNull()
            if (last != null && b.start <= last.second + BigInteger.ONE) {
                if (b.end > last.second) merged[merged.lastIndex] = last.first to b.end
            } else merged += b.start to b.end
        }
        val out = ArrayList<Pair<String, Int>>()
        val top = topOf(v6)
        fun emit(from: BigInteger, to: BigInteger) {
            var lo = from
            while (lo <= to) {
                var size = if (lo.signum() == 0) bits(v6) else lo.lowestSetBit
                while (size > 0 && lo + BigInteger.ONE.shiftLeft(size) - BigInteger.ONE > to) size--
                out += format(v6, lo) to (bits(v6) - size)
                lo += BigInteger.ONE.shiftLeft(size)
            }
        }
        var cur = BigInteger.ZERO
        for ((s, e) in merged) {
            if (s > cur) emit(cur, s - BigInteger.ONE)
            cur = e + BigInteger.ONE
        }
        if (cur <= top) emit(cur, top)
        return out
    }

    fun ipv4Except(extra: List<String>, includeLan: Boolean): List<Pair<String, Int>> =
        routes(false, (if (includeLan) privateV4 else emptyList()) + extra)

    @Suppress("DEPRECATION")
    fun attachedNetworks(ctx: Context, v6: Boolean): List<String> {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return emptyList()
        val out = LinkedHashSet<String>()
        val nets = runCatching { cm.allNetworks }.getOrNull().orEmpty()
        for (n in nets) {
            val caps = runCatching { cm.getNetworkCapabilities(n) }.getOrNull() ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) continue
            val lp = runCatching { cm.getLinkProperties(n) }.getOrNull() ?: continue
            for (la in lp.linkAddresses) {
                val a = la.address
                if ((a is Inet6Address) != v6) continue
                val host = a.hostAddress.orEmpty().substringBefore('%')
                if (!isPrivate(host)) continue
                parse("$host/${la.prefixLength}")?.let { out += cidr(v6, it.start, la.prefixLength) }
            }
        }
        return out.toList()
    }

    private fun isPrivate(host: String): Boolean {
        val p = parse(host) ?: return false
        val ranges = (if (p.v6) privateV6 else privateV4).mapNotNull { parse(it) }
        return ranges.any { p.start >= it.start && p.end <= it.end }
    }

    fun lanExclusions(ctx: Context, direct: Boolean, ipv6: Boolean): List<String> =
        if (direct) privateV4 + (if (ipv6) privateV6 else emptyList())
        else attachedNetworks(ctx, false) + (if (ipv6) attachedNetworks(ctx, true) else emptyList())
}
