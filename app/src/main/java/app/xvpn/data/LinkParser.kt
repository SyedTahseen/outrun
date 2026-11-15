package app.xvpn.data

import android.util.Base64
import java.net.URI
import java.net.URLDecoder

data class Profile(
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val link: String,
)

object LinkParser {

    fun parse(raw: String): Profile? = try {
        val link = sanitize(raw.trim())
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

    private const val ALLOWED = "-._~:/?[]@!\$&'()*+,;="

    fun sanitize(link: String): String {
        val sb = StringBuilder(link.length + 16)
        var seenHash = false
        fun enc(c: Char) { for (b in c.toString().toByteArray(Charsets.UTF_8)) sb.append('%').append("%02X".format(b.toInt() and 0xFF)) }
        var i = 0
        while (i < link.length) {
            val c = link[i]
            when {
                c == '%' -> {
                    val ok = i + 2 < link.length && link[i + 1].isHex() && link[i + 2].isHex()
                    if (ok) sb.append(c) else sb.append("%25")
                }
                c == '#' -> { if (seenHash) sb.append("%23") else { seenHash = true; sb.append(c) } }
                c.isLetterOrDigit() && c.code < 128 -> sb.append(c)
                c in ALLOWED -> sb.append(c)
                c.code < 128 -> enc(c)
                Character.isSpaceChar(c) || Character.isISOControl(c) -> enc(c)
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    fun skipped(text: String): Map<String, Int> =
        text.lines().map { it.trim() }.filter { it.contains("://") && parse(it) == null }
            .groupingBy { it.substringBefore("://").lowercase().takeIf { s -> s.length in 1..12 && s.all { c -> c.isLetterOrDigit() } } ?: "invalid" }
            .eachCount()

    fun plainText(text: String): String {
        val body = text.trim().trimStart('\uFEFF')
        if (body.lines().any { parse(it) != null }) return body
        return decodeB64(body) ?: body
    }

    fun parseMany(text: String): List<Profile> = plainText(text).lines().mapNotNull { parse(it) }

    fun query(uri: URI): Map<String, String> =
        (uri.rawQuery ?: "").split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    fun decodeB64(s: String): String? = try {
        var t = s.filter { !it.isWhitespace() }.replace('-', '+').replace('_', '/').trimEnd('=')
        t += "=".repeat((4 - t.length % 4) % 4)
        String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
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
