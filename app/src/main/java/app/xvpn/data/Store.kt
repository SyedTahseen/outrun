package app.xvpn.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** Subscription metadata (from response headers / body comments). Sizes in bytes, [expire] in epoch seconds (0 = none). */
data class SubInfo(
    val title: String = "",
    val upload: Long = 0, val download: Long = 0, val total: Long = 0,
    val expire: Long = 0,
    val intervalHours: Int = 0,
    val webPage: String = "",
    val support: String = "",
    val announce: String = "",
    val fetchedAt: Long = 0,
    val count: Int = 0,
) {
    val used: Long get() = upload + download
}

class SubSync(val added: Int, val removed: Int, val total: Int, val skipped: Map<String, Int> = emptyMap())

class DnsOption(val id: String, val name: String, val note: String, val servers: List<String>)
class TestUrlOption(val id: String, val name: String, val url: String)

/** Choices offered in Settings. */
object Choices {
    val dns = listOf(
        DnsOption("cloudflare", "Cloudflare", "1.1.1.1 · fast, private", listOf("1.1.1.1", "1.0.0.1")),
        DnsOption("google", "Google", "8.8.8.8 · reliable", listOf("8.8.8.8", "8.8.4.4")),
        DnsOption("quad9", "Quad9", "9.9.9.9 · blocks malicious domains", listOf("9.9.9.9", "149.112.112.112")),
        DnsOption("adguard", "AdGuard", "Blocks ads and trackers", listOf("94.140.14.14", "94.140.15.15")),
    )
    val testUrls = listOf(
        TestUrlOption("google", "Google", "https://www.gstatic.com/generate_204"),
        TestUrlOption("cloudflare", "Cloudflare", "https://cp.cloudflare.com/generate_204"),
        TestUrlOption("apple", "Apple", "https://captive.apple.com/"),
    )
    private val ipv4 = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")
    fun parseDns(text: String): List<String> = text.split(',', ' ', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        .let { parts -> if (parts.isNotEmpty() && parts.all { ipv4.matches(it) }) parts else emptyList() }
}

/** Stores saved share links + subscription URLs in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("xvpn", Context.MODE_PRIVATE)

    /** Sort order chosen in the Servers screen (SortMode name). Survives tab switches and restarts. */
    var sortName: String
        get() = prefs.getString("sort", "Default") ?: "Default"
        set(v) = prefs.edit().putString("sort", v).apply()

    /** Last location seen with the VPN off. Shown immediately on launch so the map doesn't start from a default. */
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

    fun links(): List<String> =
        (prefs.getString("links", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun profiles(): List<Profile> = links().mapNotNull { LinkParser.parse(it) }

    /** link -> subscription URL it came from. Links not in the map are "Default" (pasted, scanned, file, manual). */
    val sources: Map<String, String>
        get() = runCatching {
            val j = JSONObject(prefs.getString("src", "{}") ?: "{}")
            j.keys().asSequence().associateWith { j.getString(it) }
        }.getOrDefault(emptyMap())

    private fun saveSources(m: Map<String, String>) = prefs.edit().putString("src", JSONObject(m).toString()).apply()

    /** [source] = subscription URL, or null for Default. Returns the number of new profiles. */
    fun add(profiles: List<Profile>, source: String? = null): Int {
        val existing = links().toMutableList()
        val src = sources.toMutableMap()
        var n = 0
        for (p in profiles) if (p.link !in existing) {
            existing += p.link; n++
            if (source != null) src[p.link] = source
        }
        prefs.edit().putString("links", existing.joinToString("\n")).apply()
        saveSources(src)
        return n
    }

    fun remove(link: String) {
        prefs.edit().putString("links", links().filter { it != link }.joinToString("\n")).apply()
        saveSources(sources - link)
        favorites = favorites - link
        if (selected == link) selected = null
    }

    /** Removes a subscription URL together with every server that came from it. Returns how many servers were removed. */
    fun removeSubscription(url: String): Int {
        val gone = sources.filterValues { it == url }.keys
        prefs.edit().putString("links", links().filter { it !in gone }.joinToString("\n")).apply()
        saveSources(sources.filterValues { it != url })
        favorites = favorites - gone
        if (selected in gone) selected = null
        subscriptions = subscriptions - url
        subInfo = subInfo - url
        return gone.size
    }

    var favorites: Set<String>
        get() = (prefs.getString("favs", "") ?: "").split("\n").filter { it.isNotBlank() }.toSet()
        set(v) = prefs.edit().putString("favs", v.joinToString("\n")).apply()

    // ---- settings ----
    var splitEnabled: Boolean
        get() = prefs.getBoolean("split_on", false)
        set(v) = prefs.edit().putBoolean("split_on", v).apply()
    /** Package names that bypass the VPN while split tunneling is on. */
    var splitApps: Set<String>
        get() = (prefs.getString("split_apps", "") ?: "").split("\n").filter { it.isNotBlank() }.toSet()
        set(v) = prefs.edit().putString("split_apps", v.joinToString("\n")).apply()
    var dnsChoice: String
        get() = prefs.getString("dns", "cloudflare") ?: "cloudflare"
        set(v) = prefs.edit().putString("dns", v).apply()
    var dnsCustom: String
        get() = prefs.getString("dns_custom", "") ?: ""
        set(v) = prefs.edit().putString("dns_custom", v).apply()
    var testUrlChoice: String
        get() = prefs.getString("test_url", "google") ?: "google"
        set(v) = prefs.edit().putString("test_url", v).apply()
    var autoConnect: Boolean
        get() = prefs.getBoolean("auto_connect", false)
        set(v) = prefs.edit().putBoolean("auto_connect", v).apply()
    var autoUpdateSubs: Boolean
        get() = prefs.getBoolean("auto_update_subs", true)
        set(v) = prefs.edit().putBoolean("auto_update_subs", v).apply()

    /** DNS servers handed to the VPN interface. */
    fun dnsServers(): List<String> {
        if (dnsChoice == "custom") Choices.parseDns(dnsCustom).takeIf { it.isNotEmpty() }?.let { return it }
        return (Choices.dns.firstOrNull { it.id == dnsChoice } ?: Choices.dns.first()).servers
    }

    /** Latency test URLs: the chosen one first, the others as fallback. */
    fun testUrls(): List<String> {
        val first = Choices.testUrls.firstOrNull { it.id == testUrlChoice } ?: Choices.testUrls.first()
        return listOf(first.url) + Choices.testUrls.filter { it !== first }.map { it.url }
    }

    fun clear() {
        prefs.edit().putString("links", "").putString("src", "{}").putString("favs", "").putString("selected", null).apply()
    }

    var selected: String?
        get() = prefs.getString("selected", null)
        set(v) = prefs.edit().putString("selected", v).apply()

    var subscriptions: List<String>
        get() = (prefs.getString("subs", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("subs", v.joinToString("\n")).apply()

    /** Metadata of each subscription URL, refreshed on every successful fetch. */
    var subInfo: Map<String, SubInfo>
        get() = runCatching {
            val j = JSONObject(prefs.getString("subinfo", "{}") ?: "{}")
            j.keys().asSequence().associateWith { k ->
                val o = j.getJSONObject(k)
                SubInfo(
                    o.optString("title"), o.optLong("up"), o.optLong("down"), o.optLong("total"), o.optLong("expire"),
                    o.optInt("interval"), o.optString("web"), o.optString("support"), o.optString("announce"),
                    o.optLong("fetched"), o.optInt("count"),
                )
            }
        }.getOrDefault(emptyMap())
        set(v) {
            val j = JSONObject()
            v.forEach { (k, i) ->
                j.put(k, JSONObject().put("title", i.title).put("up", i.upload).put("down", i.download).put("total", i.total)
                    .put("expire", i.expire).put("interval", i.intervalHours).put("web", i.webPage).put("support", i.support)
                    .put("announce", i.announce).put("fetched", i.fetchedAt).put("count", i.count))
            }
            prefs.edit().putString("subinfo", j.toString()).apply()
        }

    private class Fetched(val body: String, val headers: Map<String, String>)

    /** GET with manual redirects (HttpURLConnection won't follow http<->https) and a client-style User-Agent. */
    private fun fetch(url: String): Fetched {
        var u = url
        repeat(6) {
            val c = URL(u).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 20000
                c.readTimeout = 25000
                c.instanceFollowRedirects = false
                // panels pick the output format from the User-Agent: a v2ray-style client gets plain/base64 share links
                c.setRequestProperty("User-Agent", "v2rayNG/1.9.19")
                c.setRequestProperty("Accept", "*/*")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw java.io.IOException("Redirect without location")
                    u = URL(URL(u), loc).toString()
                    return@repeat
                }
                if (code !in 200..299) throw java.io.IOException("Server answered HTTP $code")
                val headers = HashMap<String, String>()
                c.headerFields.forEach { (k, v) -> if (k != null && v.isNotEmpty()) headers[k.lowercase()] = v.first() }
                return Fetched(c.inputStream.bufferedReader().use { it.readText() }, headers)
            } finally {
                c.disconnect()
            }
        }
        throw java.io.IOException("Too many redirects")
    }

    /** Header values may be "base64:..." or percent-encoded (profile-title, announce). */
    private fun decodeValue(v: String): String {
        val t = v.trim()
        if (t.startsWith("base64:", true)) return LinkParser.decodeB64(t.substring(7)) ?: t
        return if (t.contains('%')) runCatching { URLDecoder.decode(t, "UTF-8") }.getOrDefault(t) else t
    }

    private fun parseInfo(url: String, headers: Map<String, String>, text: String, count: Int): SubInfo {
        val h = HashMap(headers)
        // some panels put the same fields in the body as "#profile-title: x" comment lines
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
            webPage = h["profile-web-page-url"]?.trim().orEmpty(),
            support = h["support-url"]?.trim().orEmpty(),
            announce = h["announce"]?.let { decodeValue(it) }.orEmpty(),
            fetchedAt = System.currentTimeMillis(), count = count,
        )
    }

    /**
     * Blocking: call from a background thread. Downloads the subscription and syncs it: new servers are added,
     * servers that disappeared from the subscription are removed, and its metadata (quota, expiry...) is saved.
     */
    fun importSubscription(rawUrl: String): SubSync {
        val url = rawUrl.trim()
        val f = fetch(url)
        val text = LinkParser.plainText(f.body)
        val found = text.lines().mapNotNull { LinkParser.parse(it) }.distinctBy { it.link }
        if (found.isEmpty()) throw java.io.IOException("No supported servers found in this subscription")

        if (url !in subscriptions) subscriptions = subscriptions + url
        val src = sources.toMutableMap()
        val fresh = found.map { it.link }.toSet()
        val gone = src.filterValues { it == url }.keys - fresh
        val list = links().filter { it !in gone }.toMutableList()
        gone.forEach { src.remove(it) }
        var added = 0
        for (p in found) if (p.link !in list) { list += p.link; src[p.link] = url; added++ }
        prefs.edit().putString("links", list.joinToString("\n")).apply()
        saveSources(src)
        if (gone.isNotEmpty()) favorites = favorites - gone
        if (selected in gone) selected = null
        subInfo = subInfo + (url to parseInfo(url, f.headers, text, found.size))
        return SubSync(added, gone.size, found.size, LinkParser.skipped(text))
    }
}
