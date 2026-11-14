package app.xvpn.data

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Stores saved share links + subscription URLs in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("xvpn", Context.MODE_PRIVATE)

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
        return gone.size
    }

    var favorites: Set<String>
        get() = (prefs.getString("favs", "") ?: "").split("\n").filter { it.isNotBlank() }.toSet()
        set(v) = prefs.edit().putString("favs", v.joinToString("\n")).apply()

    fun clear() {
        prefs.edit().putString("links", "").putString("src", "{}").putString("favs", "").putString("selected", null).apply()
    }

    var selected: String?
        get() = prefs.getString("selected", null)
        set(v) = prefs.edit().putString("selected", v).apply()

    var subscriptions: List<String>
        get() = (prefs.getString("subs", "") ?: "").split("\n").filter { it.isNotBlank() }
        set(v) = prefs.edit().putString("subs", v.joinToString("\n")).apply()

    /** Blocking: call from a background thread. Returns number of new profiles. */
    fun importSubscription(url: String): Int {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "VpnClient/1.0")
        val body = c.inputStream.bufferedReader().use { it.readText() }
        if (url !in subscriptions) subscriptions = subscriptions + url
        return add(LinkParser.parseMany(body), source = url)
    }
}
