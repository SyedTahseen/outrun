package app.xvpn.data

import android.util.Base64
import java.net.URI
import java.net.URLDecoder

data class Profile(
    val name: String,
    val protocol: String,   // vless | trojan | ss | vmess
    val host: String,
    val port: Int,
    val link: String,       // original share link, config is built from this at connect time
)

object LinkParser {

    /** Parse one share link. Returns null if unsupported or malformed. */
    fun parse(raw: String): Profile? = try {
        val link = raw.trim()
        when {
            link.startsWith("vless://") -> uriProfile(link, "vless")
            link.startsWith("trojan://") -> uriProfile(link, "trojan")
            link.startsWith("ss://") -> ssProfile(link)
            link.startsWith("vmess://") -> vmessProfile(link)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    /** Parse many links (one per line), or a base64 subscription body. */
    fun parseMany(text: String): List<Profile> {
        val body = text.trim()
        val decoded = if (body.contains("://")) body else decodeB64(body) ?: body
        return decoded.lines().mapNotNull { parse(it) }
    }

    fun query(uri: URI): Map<String, String> =
        (uri.rawQuery ?: "").split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    fun decodeB64(s: String): String? = try {
        String(Base64.decode(s.trim(), Base64.DEFAULT or Base64.URL_SAFE))
    } catch (e: Exception) {
        null
    }

    private fun uriProfile(link: String, proto: String): Profile {
        val u = URI(link)
        val name = u.rawFragment?.let { URLDecoder.decode(it, "UTF-8") } ?: "${u.host}:${u.port}"
        return Profile(name, proto, u.host, u.port, link)
    }

    private fun ssProfile(link: String): Profile {
        val u = parseSs(link)
        return Profile(u.name, "ss", u.host, u.port, link)
    }

    class Ss(val method: String, val password: String, val host: String, val port: Int, val name: String)

    /** Handles both ss://base64(method:pass)@host:port and ss://base64(method:pass@host:port). */
    fun parseSs(link: String): Ss {
        var rest = link.removePrefix("ss://")
        var name = ""
        if (rest.contains("#")) {
            name = URLDecoder.decode(rest.substringAfter("#"), "UTF-8")
            rest = rest.substringBefore("#")
        }
        rest = rest.substringBefore("?")
        val userInfo: String
        val hostPort: String
        if (rest.contains("@")) {
            val ui = rest.substringBeforeLast("@")
            userInfo = if (ui.contains(":")) URLDecoder.decode(ui, "UTF-8") else decodeB64(ui)!!
            hostPort = rest.substringAfterLast("@")
        } else {
            val all = decodeB64(rest)!!
            userInfo = all.substringBeforeLast("@")
            hostPort = all.substringAfterLast("@")
        }
        val method = userInfo.substringBefore(":")
        val password = userInfo.substringAfter(":")
        val host = hostPort.substringBeforeLast(":").trim('[', ']')
        val port = hostPort.substringAfterLast(":").toInt()
        return Ss(method, password, host, port, name.ifEmpty { "$host:$port" })
    }

    private fun vmessProfile(link: String): Profile {
        val j = org.json.JSONObject(decodeB64(link.removePrefix("vmess://"))!!)
        val host = j.getString("add")
        val port = j.get("port").toString().toInt()
        return Profile(j.optString("ps").ifEmpty { "$host:$port" }, "vmess", host, port, link)
    }

    private val describeCache = HashMap<String, String>()

    /** Human-readable protocol line, e.g. "VLESS / Reality", "VMess / WS / TLS", "Trojan / TLS", "Shadowsocks". */
    fun describe(link: String): String = describeCache.getOrPut(link) {
        runCatching {
            val parts = mutableListOf<String>()
            when {
                link.startsWith("vless://") || link.startsWith("trojan://") -> {
                    val vless = link.startsWith("vless://")
                    parts += if (vless) "VLESS" else "Trojan"
                    val q = query(URI(link))
                    val net = (q["type"] ?: "tcp").lowercase()
                    val sec = (q["security"] ?: if (vless) "none" else "tls").lowercase()
                    if (net != "tcp") parts += net.uppercase()
                    when (sec) { "reality" -> parts += "Reality"; "tls" -> parts += "TLS"; "xtls" -> parts += "XTLS" }
                }
                link.startsWith("vmess://") -> {
                    parts += "VMess"
                    val j = org.json.JSONObject(decodeB64(link.removePrefix("vmess://"))!!)
                    val net = j.optString("net").lowercase()
                    if (net.isNotEmpty() && net != "tcp") parts += net.uppercase()
                    if (j.optString("tls").lowercase() == "tls") parts += "TLS"
                }
                link.startsWith("ss://") -> parts += "Shadowsocks"
            }
            parts.joinToString(" / ")
        }.getOrDefault("")
    }
}
