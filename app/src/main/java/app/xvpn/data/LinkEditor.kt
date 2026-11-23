package app.xvpn.data

import android.util.Base64
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

data class EditModel(
    val protocol: String,

    val scheme: String,
    val name: String,
    val host: String,
    val port: String,
    val user: String,
    val pass: String,

    val params: List<Pair<String, String>>,

    val kinds: Map<String, Char> = emptyMap(),
)

class EditLabels(val user: String, val pass: String?)

object LinkEditor {
    private val vmessFixed = setOf("ps", "add", "port", "id")

    fun labels(protocol: String): EditLabels = when (protocol) {
        "vless", "vmess" -> EditLabels("UUID", null)
        "trojan" -> EditLabels("Password", null)
        "ss" -> EditLabels("Method (for example aes-256-gcm)", "Password")
        "hysteria2" -> EditLabels("Password, or username", "Password (only if the first field is a username)")
        "tuic" -> EditLabels("UUID", "Password")
        "wireguard" -> EditLabels("Private key", null)
        else -> EditLabels("Username (optional)", "Password (optional)")
    }

    fun describeProtocol(protocol: String) = when (protocol) {
        "vless" -> "VLESS"
        "vmess" -> "VMess"
        "trojan" -> "Trojan"
        "ss" -> "Shadowsocks"
        "hysteria2" -> "Hysteria2"
        "tuic" -> "TUIC"
        "wireguard" -> "WireGuard"
        "socks" -> "SOCKS5 proxy"
        else -> "HTTP proxy"
    }

    fun paramsTitle(protocol: String) = if (protocol == "vmess") "Settings" else "Parameters"

    fun parse(link: String): EditModel? = runCatching {
        when (LinkParser.protocolOf(link)) {
            null -> null
            "ss" -> parseSs(link)
            "vmess" -> parseVmess(link)
            else -> parseUri(link)
        }
    }.getOrNull()

    fun finish(m: EditModel): Pair<String?, String?> {
        val p = LinkParser.parse(build(m))
        if (p == null || p.protocol != m.protocol) return null to "These details don't make a valid link"
        val err = runCatching { ConfigBuilder.outbound(p.link) }.exceptionOrNull()
        if (err != null) return null to (err.message?.takeIf { it.isNotBlank() }?.take(160) ?: "Invalid settings")
        return p.link to null
    }

    fun build(m: EditModel): String = when (m.protocol) {
        "ss" -> buildSs(m)
        "vmess" -> buildVmess(m)
        else -> buildUri(m)
    }

    private fun dec(s: String) = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")

    private fun fragment(link: String) = link.substringAfter('#', "").takeIf { it.isNotEmpty() }?.let { dec(it) } ?: ""

    private fun parseUri(link: String): EditModel {
        val protocol = LinkParser.protocolOf(link) ?: error("Unsupported link")
        val a = LinkParser.authority(link, defaultPort = when (protocol) { "socks" -> 1080; "hysteria2" -> 443; else -> -1 })
        return EditModel(protocol, link.substringBefore("://"), fragment(link), a.host, a.portSpec, a.user, a.pass ?: "", a.query.toList())
    }

    private fun parseSs(link: String): EditModel {
        val s = LinkParser.parseSs(link)
        val query = link.substringBefore('#').substringAfter('?', "").split('&').filter { '=' in it }
            .map { it.substringBefore('=') to dec(it.substringAfter('=')) }
        return EditModel("ss", "ss", fragment(link), s.host, s.port.toString(), s.method, s.password, query)
    }

    private fun parseVmess(link: String): EditModel {
        val j = LinkParser.vmessJson(link)
        val kinds = HashMap<String, Char>()
        val params = ArrayList<Pair<String, String>>()
        for (k in j.keys()) {
            val v = j.get(k)
            if (v is Number) kinds[k] = 'n' else if (v is Boolean) kinds[k] = 'b'
            if (k !in vmessFixed) params += k to (if (v == JSONObject.NULL) "" else v.toString())
        }
        return EditModel("vmess", "vmess", j.optString("ps"), j.getString("add"), j.get("port").toString(), j.optString("id"), "", params, kinds)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun hostPart(h: String) = h.trim().let { if (':' in it && !it.startsWith("[")) "[$it]" else it }

    private fun query(params: List<Pair<String, String>>): String {
        val ps = params.map { it.first.trim() to it.second }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }
        return if (ps.isEmpty()) "" else "?" + ps.joinToString("&") { "${enc(it.first)}=${enc(it.second)}" }
    }

    private fun buildUri(m: EditModel): String {
        val sb = StringBuilder(m.scheme).append("://")
        if (m.user.isNotEmpty() || m.pass.isNotEmpty()) {
            sb.append(enc(m.user))
            if (m.pass.isNotEmpty()) sb.append(':').append(enc(m.pass))
            sb.append('@')
        }
        sb.append(hostPart(m.host))
        if (m.port.isNotBlank()) sb.append(':').append(m.port.trim())
        sb.append(query(m.params))
        if (m.name.isNotBlank()) sb.append('#').append(enc(m.name.trim()))
        return sb.toString()
    }

    private fun buildSs(m: EditModel): String {
        val cred = Base64.encodeToString(
            "${m.user}:${m.pass}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
        )
        val sb = StringBuilder("ss://").append(cred).append('@').append(hostPart(m.host)).append(':').append(m.port.trim())
        sb.append(query(m.params))
        if (m.name.isNotBlank()) sb.append('#').append(enc(m.name.trim()))
        return sb.toString()
    }

    private fun buildVmess(m: EditModel): String {
        val j = JSONObject()
        j.put("ps", m.name.trim())
        j.put("add", m.host.trim())
        j.put("port", typed(m, "port", m.port.trim()))
        j.put("id", m.user.trim())
        for ((k, v) in m.params) {
            val key = k.trim()
            if (key.isNotEmpty() && key !in vmessFixed) j.put(key, typed(m, key, v))
        }
        return "vmess://" + Base64.encodeToString(j.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    private fun typed(m: EditModel, key: String, v: String): Any = when (m.kinds[key]) {
        'n' -> {
            val l = v.toLongOrNull()
            if (l != null) (if (l >= Int.MIN_VALUE && l <= Int.MAX_VALUE) l.toInt() else l) else (v.toDoubleOrNull() ?: v)
        }
        'b' -> if (v.equals("true", ignoreCase = true)) true else if (v.equals("false", ignoreCase = true)) false else v
        else -> v
    }
}
