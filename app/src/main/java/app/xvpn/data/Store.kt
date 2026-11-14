package app.xvpn.data

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL

/** Stores saved share links + subscription URLs in SharedPreferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("xvpn", Context.MODE_PRIVATE)

    fun links(): List<String> =
        (prefs.getString("links", "") ?: "").split("\n").filter { it.isNotBlank() }

    fun profiles(): List<Profile> = links().mapNotNull { LinkParser.parse(it) }

    fun add(profiles: List<Profile>): Int {
        val existing = links().toMutableList()
        var n = 0
        for (p in profiles) if (p.link !in existing) { existing += p.link; n++ }
        prefs.edit().putString("links", existing.joinToString("\n")).apply()
        return n
    }

    fun remove(link: String) {
        prefs.edit().putString("links", links().filter { it != link }.joinToString("\n")).apply()
        if (selected == link) selected = null
    }

    fun clear() {
        prefs.edit().putString("links", "").putString("selected", null).apply()
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
        return add(LinkParser.parseMany(body))
    }
}
