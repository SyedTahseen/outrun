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
            link.startsWith("hysteria2://") || link.startsWith("hy2://") -> authProfile(link, "hysteria2", defaultPort = 443)
            link.startsWith("tuic://") -> authProfile(link, "tuic")
            link.startsWith("wireguard://") || link.startsWith("wg://") -> authProfile(link, "wireguard")
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
        fromWgConf(body)?.let { return it }
        if (body.lines().any { parse(it) != null }) return body
        return decodeB64(body) ?: body
    }

    fun fromWgConf(text: String): String? {
        if (!text.contains("[Interface]", ignoreCase = true)) return null
        val iface = HashMap<String, String>()
        val peer = HashMap<String, String>()
        var cur: HashMap<String, String>? = null
        var peers = 0
        for (raw in text.lines()) {
            val l = raw.substringBefore('#').trim()
            if (l.isEmpty()) continue
            if (l.equals("[Interface]", ignoreCase = true)) cur = iface
            else if (l.equals("[Peer]", ignoreCase = true)) { peers++; cur = if (peers == 1) peer else null }
            else if ('=' in l) cur?.put(l.substringBefore('=').trim().lowercase(), l.substringAfter('=').trim())
        }
        val key = iface["privatekey"] ?: return null
        val addr = iface["address"] ?: return null
        val pub = peer["publickey"] ?: return null
        val ep = peer["endpoint"] ?: return null
        val host = ep.substringBeforeLast(':')
        val port = ep.substringAfterLast(':').toIntOrNull() ?: return null
        fun e(v: String) = java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")
        val q = StringBuilder("publickey=${e(pub)}&address=${e(addr.replace(" ", ""))}")
        iface["mtu"]?.let { q.append("&mtu=").append(e(it)) }
        peer["presharedkey"]?.let { q.append("&presharedkey=").append(e(it)) }
        return "wireguard://${e(key)}@$host:$port?$q#${e("WireGuard $host")}"
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

    class Auth(
        val user: String, val pass: String?, val host: String, val port: Int, val portSpec: String,
        val query: Map<String, String>, val name: String,
    ) {
        val userinfo: String get() = if (pass != null) "$user:$pass" else user
    }

    private val authorityRe = Regex("^[A-Za-z0-9]+://(?:([^@/?#]*)@)?(\\[[^\\]]+\\]|[^:/?#,]+)(?::([^/?#]*))?")

    private fun dec(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    fun authority(link: String, defaultPort: Int = -1): Auth {
        val m = authorityRe.find(link) ?: error("Invalid link")
        val rawUser = m.groupValues[1]
        val user = dec(rawUser.substringBefore(':'))
        val pass = if (':' in rawUser) dec(rawUser.substringAfter(':')) else null
        val host = m.groupValues[2].trim('[', ']')
        val spec = m.groupValues[3]
        val port = Regex("\\d+").find(spec)?.value?.toIntOrNull() ?: defaultPort
        if (host.isEmpty() || port !in 1..65535) error("Invalid server address")
        val q = link.substringAfter('?', "").substringBefore('#').split("&").filter { it.contains("=") }.associate {
            val (k, v) = it.split("=", limit = 2); k to dec(v)
        }
        val name = link.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let { dec(it) } ?: "$host:$port"
        return Auth(user, pass, host, port, spec, q, name)
    }

    private fun authProfile(link: String, proto: String, defaultPort: Int = -1): Profile {
        val a = authority(link, defaultPort)
        return Profile(a.name, proto, a.host, a.port, link)
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
                link.startsWith("hysteria2://") || link.startsWith("hy2://") -> parts += "Hysteria2"
                link.startsWith("tuic://") -> parts += "TUIC"
                link.startsWith("wireguard://") || link.startsWith("wg://") -> parts += "WireGuard"
            }
            parts.joinToString(" / ")
        }.getOrDefault("")
    }
}
