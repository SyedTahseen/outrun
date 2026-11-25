package app.xvpn.data

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.net.URLDecoder

data class SubInfo(
    val title: String = "",
    val upload: Long = 0, val download: Long = 0, val total: Long = 0,
    val expire: Long = 0,
    val intervalHours: Int = 0,
    val announce: String = "",
    val fetchedAt: Long = 0,
    val count: Int = 0,
) {
    val used: Long get() = upload + download
}

class SubSync(val added: Int, val removed: Int, val total: Int, val skipped: Map<String, Int> = emptyMap())

class DnsOption(val id: String, val servers: List<String>)
class TestUrlOption(val id: String, val name: String, val url: String)

object Choices {
    val dns = listOf(
        DnsOption("cloudflare", listOf("1.1.1.1", "1.0.0.1")),
        DnsOption("google", listOf("8.8.8.8", "8.8.4.4")),
        DnsOption("quad9", listOf("9.9.9.9", "149.112.112.112")),
        DnsOption("adguard", listOf("94.140.14.14", "94.140.15.15")),
    )
    val testUrls = listOf(
        TestUrlOption("google", "Google", "https://www.gstatic.com/generate_204"),
        TestUrlOption("cloudflare", "Cloudflare", "https://cp.cloudflare.com/generate_204"),
        TestUrlOption("apple", "Apple", "https://captive.apple.com/"),
    )
    fun netShieldDns(adult: Boolean): List<String> =
        if (adult) listOf("94.140.14.15", "94.140.15.16") else listOf("94.140.14.14", "94.140.15.15")

    val mtuChoices = listOf(
        1500 to "Default, best throughput",
        1420 to "Good for most mobile networks",
        1400 to "Safer on tunnels and some carriers",
        1380 to "For networks that drop larger packets",
        1280 to "Minimum for IPv6, most compatible",
    )
    const val MTU_DEFAULT = 1500
    private val ipv4 = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")

    fun parseIp(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val addr = t.substringBefore('/')
        val v6 = ':' in addr
        val max = if (v6) 128 else 32
        val prefix = if ('/' in t) t.substringAfter('/').toIntOrNull()?.takeIf { it in 0..max } ?: return null else max
        val ok = if (v6) addr.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' } &&
            runCatching { InetAddress.getByName(addr) is Inet6Address }.getOrDefault(false)
        else ipv4.matches(addr)
        if (!ok) return null
        return if (prefix == max) addr else "$addr/$prefix"
    }
    fun parseDnsAddress(text: String): String? = parseIp(text)?.takeIf { '/' !in it }

    fun parseDns(text: String): List<String> = text.split(',', ' ', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        .let { parts -> if (parts.isNotEmpty() && parts.all { ipv4.matches(it) }) parts else emptyList() }
}

class Store(context: Context) {
    private val prefs = context.getSharedPreferences("xvpn", Context.MODE_PRIVATE)

    private fun lines(key: String): List<String> = (prefs.getString(key, "") ?: "").split("\n").filter { it.isNotBlank() }

    private fun putLines(key: String, items: Collection<String>) = prefs.edit().putString(key, items.joinToString("\n")).apply()

    var sortName: String
        get() = prefs.getString("sort", "Default") ?: "Default"
        set(v) = prefs.edit().putString("sort", v).apply()

    var lastReal: IpInfo?
        get() = runCatching {
            val j = JSONObject(prefs.getString("last_real", null) ?: return null)
            IpInfo(
                ip = j.getString("ip"), country = j.optString("country"), code = j.optString("code"),
                city = j.optString("city"), lat = j.getDouble("lat"), lon = j.getDouble("lon"),
            )
        }.getOrNull()
        set(v) {
            if (v == null) prefs.edit().remove("last_real").apply()
            else prefs.edit().putString("last_real", JSONObject()
                .put("ip", v.ip).put("country", v.country).put("city", v.city)
                .put("lat", v.lat).put("lon", v.lon).put("code", v.code).toString()).apply()
        }

    fun links(): List<String> = lines("links")

    fun profiles(): List<Profile> = links().mapNotNull { LinkParser.parse(it) }

    val sources: Map<String, String>
        get() = runCatching {
            val j = JSONObject(prefs.getString("src", "{}") ?: "{}")
            j.keys().asSequence().associateWith { j.getString(it) }
        }.getOrDefault(emptyMap())

    private fun saveSources(m: Map<String, String>) = prefs.edit().putString("src", JSONObject(m).toString()).apply()

    fun add(profiles: List<Profile>, source: String? = null): Int {
        val existing = links().toMutableList()
        val src = sources.toMutableMap()
        var n = 0
        for (p in profiles) if (p.link !in existing) {
            existing += p.link; n++
            if (source != null) src[p.link] = source
        }
        putLines("links", existing)
        saveSources(src)
        return n
    }

    fun remove(link: String) {
        removeMany(listOf(link))
    }

    fun removeMany(gone: Collection<String>): Int {
        val set = gone.toSet()
        val before = links()
        putLines("links", before.filter { it !in set })
        saveSources(sources.filterKeys { it !in set })
        favorites = favorites - set
        putLines("recents", recentLinks().filter { it !in set })
        if (selected in set) selected = null
        return before.count { it in set }
    }

    fun replace(old: String, new: String): Boolean {
        if (old == new) return true
        val all = links()
        if (old !in all || new in all) return false
        putLines("links", all.map { if (it == old) new else it })
        saveSources(sources.filterKeys { it != old })
        if (old in favorites) favorites = favorites - old + new
        putLines("recents", recentLinks().map { if (it == old) new else it })
        if (selected == old) selected = new
        return true
    }

    fun removeSubscription(url: String): Int {
        val gone = sources.filterValues { it == url }.keys
        removeMany(gone)
        subscriptions = subscriptions - url
        subInfo = subInfo - url
        return gone.size
    }

    var favorites: Set<String>
        get() = lines("favs").toSet()
        set(v) = putLines("favs", v)

    var splitEnabled: Boolean
        get() = prefs.getBoolean("split_on", false)
        set(v) = prefs.edit().putBoolean("split_on", v).apply()

    var splitApps: Set<String>
        get() = lines("split_apps").toSet()
        set(v) = putLines("split_apps", v)

    var splitMode: String
        get() = prefs.getString("split_mode", "exclude") ?: "exclude"
        set(v) = prefs.edit().putString("split_mode", v).apply()
    var splitIncludeApps: Set<String>
        get() = lines("split_inc_apps").toSet()
        set(v) = putLines("split_inc_apps", v)
    var splitExcludeIps: List<String>
        get() = lines("split_exc_ips")
        set(v) = putLines("split_exc_ips", v)
    var splitIncludeIps: List<String>
        get() = lines("split_inc_ips")
        set(v) = putLines("split_inc_ips", v)

    private val dnsChoice: String get() = prefs.getString("dns", "cloudflare") ?: "cloudflare"
    private val dnsCustom: String get() = prefs.getString("dns_custom", "") ?: ""
    var testUrlChoice: String
        get() = prefs.getString("test_url", "google") ?: "google"
        set(v) = prefs.edit().putString("test_url", v).apply()
    var ipv6Mode: String
        get() = prefs.getString("ipv6_mode", "tunnel") ?: "tunnel"
        set(v) = prefs.edit().putString("ipv6_mode", v).apply()
    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(v) = prefs.edit().putString("theme_mode", v).apply()

    var accentColor: Int
        get() = prefs.getInt("accent_color", 0xFF2563EB.toInt())
        set(v) = prefs.edit().putInt("accent_color", v).apply()

    var displayScale: Float
        get() = prefs.getFloat("display_scale", 1f)
        set(v) = prefs.edit().putFloat("display_scale", v).apply()
    var autoUpdateSubs: Boolean
        get() = prefs.getBoolean("auto_update_subs", true)
        set(v) = prefs.edit().putBoolean("auto_update_subs", v).apply()

    var netShield: Boolean
        get() = prefs.getBoolean("netshield", false)
        set(v) = prefs.edit().putBoolean("netshield", v).apply()
    var blockAdult: Boolean
        get() = prefs.getBoolean("block_adult", false)
        set(v) = prefs.edit().putBoolean("block_adult", v).apply()

    var lanAccess: Boolean
        get() = prefs.getBoolean("lan_access", false)
        set(v) = prefs.edit().putBoolean("lan_access", v).apply()

    var blockQuic: Boolean
        get() = prefs.getBoolean("block_quic", false)
        set(v) = prefs.edit().putBoolean("block_quic", v).apply()

    var lanDirect: Boolean
        get() = prefs.getBoolean("lan_direct", false)
        set(v) = prefs.edit().putBoolean("lan_direct", v).apply()

    var autoConnectBoot: Boolean
        get() = prefs.getBoolean("auto_boot", false)
        set(v) = prefs.edit().putBoolean("auto_boot", v).apply()

    var rootMode: Boolean
        get() = prefs.getBoolean("root_mode", false)
        set(v) = prefs.edit().putBoolean("root_mode", v).apply()

    var mtu: Int
        get() = prefs.getInt("mtu", Choices.MTU_DEFAULT).coerceIn(1280, 1500)
        set(v) = prefs.edit().putInt("mtu", v.coerceIn(1280, 1500)).apply()

    fun recentLinks(): List<String> = lines("recents")

    fun pushRecent(link: String) = putLines("recents", (listOf(link) + recentLinks().filter { it != link }).take(10))

    var customDnsEnabled: Boolean
        get() = if (prefs.contains("cdns_on")) prefs.getBoolean("cdns_on", false) else dnsChoice != "cloudflare"
        set(v) = prefs.edit().putBoolean("cdns_on", v).apply()

    var customDnsList: List<String>
        get() = if (prefs.contains("cdns_list")) lines("cdns_list") else
            if (dnsChoice == "custom") Choices.parseDns(dnsCustom)
            else Choices.dns.firstOrNull { it.id == dnsChoice && it.id != "cloudflare" }?.servers ?: emptyList()
        set(v) = putLines("cdns_list", v)

    private fun customDnsEffective(): List<String> =
        if (customDnsEnabled) customDnsList.filter { ipv6Mode != "block" || ':' !in it } else emptyList()

    fun dnsServers(): List<String> {
        val custom = customDnsEffective()
        if (custom.isEmpty()) return if (netShield) Choices.netShieldDns(blockAdult) else Choices.dns.first().servers
        val fallback = Choices.dns.first().servers.filter { ipv6Mode != "block" || ':' !in it }
        return (custom + fallback).distinct()
    }

    fun resolverDns(): String {
        val list = customDnsEffective().ifEmpty { Choices.dns.first().servers }
        return list.firstOrNull { ':' !in it } ?: list.first()
    }

    fun testUrl(): String = (Choices.testUrls.firstOrNull { it.id == testUrlChoice } ?: Choices.testUrls.first()).url

    fun clear() {
        prefs.edit().putString("links", "").putString("src", "{}").putString("favs", "").putString("recents", "").putString("selected", null).apply()
    }

    var selected: String?
        get() = prefs.getString("selected", null)
        set(v) = prefs.edit().putString("selected", v).apply()

    fun selectedOrNull(): String? = selected?.takeIf { LinkParser.parse(it) != null }

    fun linkToConnect(): String? = (selected ?: profiles().firstOrNull()?.link)?.takeIf { LinkParser.parse(it) != null }

    var subscriptions: List<String>
        get() = lines("subs")
        set(v) = putLines("subs", v)

    var subInfo: Map<String, SubInfo>
        get() = runCatching {
            val j = JSONObject(prefs.getString("subinfo", "{}") ?: "{}")
            j.keys().asSequence().associateWith { k ->
                val o = j.getJSONObject(k)
                SubInfo(
                    o.optString("title"), o.optLong("up"), o.optLong("down"), o.optLong("total"), o.optLong("expire"),
                    o.optInt("interval"), o.optString("announce"), o.optLong("fetched"), o.optInt("count"),
                )
            }
        }.getOrDefault(emptyMap())
        set(v) {
            val j = JSONObject()
            v.forEach { (k, i) ->
                j.put(k, JSONObject().put("title", i.title).put("up", i.upload).put("down", i.download).put("total", i.total)
                    .put("expire", i.expire).put("interval", i.intervalHours)
                    .put("announce", i.announce).put("fetched", i.fetchedAt).put("count", i.count))
            }
            prefs.edit().putString("subinfo", j.toString()).apply()
        }

    private class Fetched(val body: String, val headers: Map<String, String>)

    private fun fetch(url: String): Fetched {
        var u = url
        repeat(6) {
            val c = openConnection(u)
            try {
                c.connectTimeout = 20000
                c.readTimeout = 25000
                c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "v2rayNG/1.9.19")
                c.setRequestProperty("Accept", "*/*")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IOException("Redirect without location")
                    u = URL(URL(u), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException("Server answered HTTP $code")
                val headers = HashMap<String, String>()
                c.headerFields.forEach { (k, v) -> if (k != null && v.isNotEmpty()) headers[k.lowercase()] = v.first() }
                return Fetched(c.inputStream.bufferedReader().use { it.readText() }, headers)
            } finally {
                c.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private fun decodeValue(v: String): String {
        val t = v.trim()
        if (t.startsWith("base64:", true)) return LinkParser.decodeB64(t.substring(7)) ?: t
        return if (t.contains('%')) runCatching { URLDecoder.decode(t, "UTF-8") }.getOrDefault(t) else t
    }

    private fun parseInfo(headers: Map<String, String>, text: String, count: Int): SubInfo {
        val h = HashMap(headers)

        text.lines().forEach { l ->
            val t = l.trim()
            if (t.startsWith("#")) {
                val m = Regex("^#\\s*([A-Za-z-]+)\\s*[:=]\\s*(.*)$").find(t)
                if (m != null) h.putIfAbsent(m.groupValues[1].lowercase(), m.groupValues[2])
            }
        }
        var up = 0L; var down = 0L; var total = 0L; var expire = 0L
        (h["subscription-userinfo"] ?: "").split(";").forEach { kv ->
            val k = kv.substringBefore("=").trim().lowercase()
            val v = kv.substringAfter("=", "").trim().toDoubleOrNull()?.toLong() ?: return@forEach
            when (k) { "upload" -> up = v; "download" -> down = v; "total" -> total = v; "expire" -> expire = v }
        }
        val title = h["profile-title"]?.let { decodeValue(it) }
            ?: Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)").find(h["content-disposition"] ?: "")?.groupValues?.get(1)
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }?.substringBeforeLast('.')
            ?: ""
        return SubInfo(
            title = title.trim(), upload = up, download = down, total = total, expire = expire,
            intervalHours = h["profile-update-interval"]?.trim()?.toDoubleOrNull()?.toInt() ?: 0,
            announce = h["announce"]?.let { decodeValue(it) }.orEmpty(),
            fetchedAt = System.currentTimeMillis(), count = count,
        )
    }

    fun importSubscription(rawUrl: String): SubSync {
        val url = rawUrl.trim()
        val f = fetch(url)
        val text = LinkParser.plainText(f.body)
        val found = text.lines().mapNotNull { LinkParser.parse(it) }.distinctBy { it.link }
        if (found.isEmpty()) throw IOException("No supported servers found in this subscription")

        if (url !in subscriptions) subscriptions = subscriptions + url
        val src = sources.toMutableMap()
        val fresh = found.map { it.link }.toSet()
        val gone = src.filterValues { it == url }.keys - fresh
        val list = links().filter { it !in gone }.toMutableList()
        gone.forEach { src.remove(it) }
        var added = 0
        for (p in found) if (p.link !in list) { list += p.link; src[p.link] = url; added++ }
        putLines("links", list)
        saveSources(src)
        if (gone.isNotEmpty()) favorites = favorites - gone
        if (selected in gone) selected = null
        subInfo = subInfo + (url to parseInfo(f.headers, text, found.size))
        return SubSync(added, gone.size, found.size, LinkParser.skipped(text))
    }
}
