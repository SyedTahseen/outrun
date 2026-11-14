package app.xvpn.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Builds a sing-box (1.14.x format) JSON config from a share link:
 * one SOCKS inbound on 127.0.0.1:socksPort -> "proxy" outbound.
 *
 * Not supported by stock sing-box (a clear error is shown instead of a broken connection):
 * XHTTP / SplitHTTP, mKCP, QUIC transports and VLESS "encryption" (post-quantum).
 */
object ConfigBuilder {

    /** The "proxy" outbound for one share link. Throws if the link can't be used by sing-box. */
    fun outbound(link: String): JSONObject {
        val proxy = when {
            link.startsWith("vless://") -> vless(link)
            link.startsWith("trojan://") -> trojan(link)
            link.startsWith("ss://") -> shadowsocks(link)
            link.startsWith("vmess://") -> vmess(link)
            else -> error("Unsupported link")
        }
        proxy.put("tag", "proxy")
        return proxy
    }

    // Plain UDP DNS, used only to resolve the proxy server's own hostname.
    private fun dnsBlock() = JSONObject()
        .put(
            "servers", JSONArray().put(
                JSONObject().put("type", "udp").put("tag", "dns-direct").put("server", "1.1.1.1")
            )
        )
        .put("final", "dns-direct")

    /**
     * One config for many servers (used by the real-delay test): every server gets its own SOCKS inbound
     * (ports[i]) that is routed to its own outbound, so each one can be probed independently in one process.
     */
    fun buildMulti(outs: List<JSONObject>, ports: List<Int>): String {
        val inbounds = JSONArray(); val outbounds = JSONArray(); val rules = JSONArray()
        outs.forEachIndexed { i, o ->
            outbounds.put(o.put("tag", "p$i"))
            inbounds.put(JSONObject().put("type", "socks").put("tag", "in$i").put("listen", "127.0.0.1").put("listen_port", ports[i]))
            rules.put(JSONObject().put("inbound", JSONArray().put("in$i")).put("action", "route").put("outbound", "p$i"))
        }
        outbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))
        return JSONObject()
            .put("log", JSONObject().put("level", "error"))
            .put("dns", dnsBlock())
            .put("inbounds", inbounds)
            .put("outbounds", outbounds)
            .put("route", JSONObject().put("rules", rules).put("final", "direct").put("default_domain_resolver", "dns-direct"))
            .toString()
    }

    fun build(link: String, socksPort: Int): String {
        val proxy = outbound(link)

        val root = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            // Plain UDP DNS, used only to resolve the proxy server's own hostname.
            // (Go on Android has no /etc/resolv.conf, so the "local" resolver can't be relied on.)
            .put(
                "dns", JSONObject()
                    .put(
                        "servers", JSONArray().put(
                            JSONObject().put("type", "udp").put("tag", "dns-direct").put("server", "1.1.1.1")
                        )
                    )
                    .put("final", "dns-direct")
            )
            .put(
                "inbounds", JSONArray().put(
                    JSONObject()
                        .put("type", "socks")
                        .put("tag", "socks-in")
                        .put("listen", "127.0.0.1")
                        .put("listen_port", socksPort)
                )
            )
            .put(
                "outbounds", JSONArray()
                    .put(proxy)
                    .put(JSONObject().put("type", "direct").put("tag", "direct"))
            )
            .put(
                "route", JSONObject()
                    .put("final", "proxy")
                    .put("default_domain_resolver", "dns-direct")
            )
        return root.toString()
    }

    private fun host(u: URI) = u.host.trim('[', ']')

    private fun vless(link: String): JSONObject {
        val u = URI(link)
        val q = LinkParser.query(u)
        val enc = q["encryption"].orEmpty()
        if (enc.isNotEmpty() && enc != "none") error("VLESS encryption isn't supported by sing-box")
        val o = JSONObject()
            .put("type", "vless")
            .put("server", host(u))
            .put("server_port", u.port)
            .put("uuid", u.rawUserInfo)
            .put("packet_encoding", "xudp")
        q["flow"]?.takeIf { it.isNotEmpty() }?.let { o.put("flow", it) }
        tls(q, host(u))?.let { o.put("tls", it) }
        transport(q)?.let { o.put("transport", it) }
        return o
    }

    private fun trojan(link: String): JSONObject {
        val u = URI(link)
        val q = LinkParser.query(u)
        val withDefault = if (q["security"].isNullOrEmpty()) q + ("security" to "tls") else q
        val o = JSONObject()
            .put("type", "trojan")
            .put("server", host(u))
            .put("server_port", u.port)
            .put("password", java.net.URLDecoder.decode(u.rawUserInfo, "UTF-8"))
        tls(withDefault, host(u))?.let { o.put("tls", it) }
        transport(withDefault)?.let { o.put("transport", it) }
        return o
    }

    private fun shadowsocks(link: String): JSONObject {
        val s = LinkParser.parseSs(link)
        return JSONObject()
            .put("type", "shadowsocks")
            .put("server", s.host)
            .put("server_port", s.port)
            .put("method", s.method)
            .put("password", s.password)
    }

    private fun vmess(link: String): JSONObject {
        val j = JSONObject(LinkParser.decodeB64(link.removePrefix("vmess://"))!!)
        val net = j.optString("net", "tcp").ifEmpty { "tcp" }
        val q = mutableMapOf(
            "type" to net,
            "security" to j.optString("tls", "none").ifEmpty { "none" },
            "sni" to j.optString("sni"),
            "host" to j.optString("host"),
            "path" to j.optString("path"),
            "alpn" to j.optString("alpn"),
            "fp" to j.optString("fp"),
            "serviceName" to j.optString("path"),
        )
        val host = j.getString("add")
        val o = JSONObject()
            .put("type", "vmess")
            .put("server", host)
            .put("server_port", j.get("port").toString().toInt())
            .put("uuid", j.getString("id"))
            .put("security", j.optString("scy", "auto").ifEmpty { "auto" })
            .put("alter_id", 0)
            .put("packet_encoding", "xudp")
        tls(q, host)?.let { o.put("tls", it) }
        transport(q)?.let { o.put("transport", it) }
        return o
    }

    private fun tls(q: Map<String, String>, host: String): JSONObject? {
        val security = q["security"].orEmpty()
        if (security != "tls" && security != "reality") return null

        val sni = q["sni"]?.takeIf { it.isNotEmpty() } ?: q["host"]?.takeIf { it.isNotEmpty() } ?: host
        val t = JSONObject().put("enabled", true).put("server_name", sni)
        if (q["allowInsecure"] == "1" || q["insecure"] == "1") t.put("insecure", true)
        q["alpn"]?.takeIf { it.isNotEmpty() }?.let { t.put("alpn", JSONArray(it.split(","))) }

        val fp = q["fp"].orEmpty()
        if (security == "reality") {
            // REALITY requires uTLS in sing-box
            t.put("utls", JSONObject().put("enabled", true).put("fingerprint", fp.ifEmpty { "chrome" }))
            t.put(
                "reality", JSONObject()
                    .put("enabled", true)
                    .put("public_key", q["pbk"].orEmpty())
                    .put("short_id", q["sid"].orEmpty())
            )
        } else if (fp.isNotEmpty()) {
            t.put("utls", JSONObject().put("enabled", true).put("fingerprint", fp))
        }
        return t
    }

    private fun transport(q: Map<String, String>): JSONObject? {
        val type = q["type"].orEmpty().ifEmpty { "tcp" }
        val path = q["path"]?.takeIf { it.isNotEmpty() } ?: "/"
        val hostHeader = q["host"]?.takeIf { it.isNotEmpty() }
        return when (type) {
            "tcp" -> null
            "ws" -> {
                val o = JSONObject().put("type", "ws")
                // Xray-style early data: path "/x?ed=2048"
                val ed = Regex("[?&]ed=(\\d+)").find(path)?.groupValues?.get(1)?.toIntOrNull()
                o.put("path", if (ed != null) path.replace(Regex("[?&]ed=\\d+"), "").ifEmpty { "/" } else path)
                if (ed != null) {
                    o.put("max_early_data", ed).put("early_data_header_name", "Sec-WebSocket-Protocol")
                }
                hostHeader?.let { o.put("headers", JSONObject().put("Host", it)) }
                o
            }
            "grpc" -> JSONObject().put("type", "grpc").put("service_name", q["serviceName"].orEmpty())
            "httpupgrade" -> JSONObject().put("type", "httpupgrade").put("path", path)
                .apply { hostHeader?.let { put("host", it) } }
            "h2", "http" -> JSONObject().put("type", "http").put("path", path)
                .apply { hostHeader?.let { put("host", JSONArray(it.split(","))) } }
            else -> error("Transport '$type' isn't supported by sing-box (XHTTP, mKCP, QUIC)")
        }
    }
}
