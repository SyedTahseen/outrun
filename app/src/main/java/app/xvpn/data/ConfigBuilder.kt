package app.xvpn.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Builds a complete Xray JSON config (SOCKS inbound on 127.0.0.1:SOCKS_PORT) from a share link. */
object ConfigBuilder {
    const val SOCKS_PORT = 10808

    fun build(link: String): String {
        val outbound = when {
            link.startsWith("vless://") -> vless(link)
            link.startsWith("trojan://") -> trojan(link)
            link.startsWith("ss://") -> shadowsocks(link)
            link.startsWith("vmess://") -> vmess(link)
            else -> error("Unsupported link")
        }
        outbound.put("tag", "proxy")

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("stats", JSONObject())
        root.put(
            "policy", JSONObject().put(
                "system", JSONObject()
                    .put("statsOutboundUplink", true)
                    .put("statsOutboundDownlink", true)
            )
        )
        root.put(
            "dns", JSONObject().put(
                "servers", JSONArray().put("1.1.1.1").put("8.8.8.8")
            )
        )
        root.put(
            "inbounds", JSONArray().put(
                JSONObject()
                    .put("tag", "socks-in")
                    .put("listen", "127.0.0.1")
                    .put("port", SOCKS_PORT)
                    .put("protocol", "socks")
                    .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
                    .put(
                        "sniffing", JSONObject()
                            .put("enabled", true)
                            .put("destOverride", JSONArray().put("http").put("tls"))
                    )
            )
        )
        root.put(
            "outbounds", JSONArray()
                .put(outbound)
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
        )
        root.put(
            "routing", JSONObject()
                .put("domainStrategy", "AsIs")
                .put(
                    "rules", JSONArray().put(
                        JSONObject().put("type", "field").put("inboundTag", JSONArray().put("socks-in"))
                            .put("outboundTag", "proxy")
                    )
                )
        )
        return root.toString()
    }

    private fun vless(link: String): JSONObject {
        val u = URI(link)
        val q = LinkParser.query(u)
        val user = JSONObject()
            .put("id", u.rawUserInfo)
            .put("encryption", q["encryption"] ?: "none")
        q["flow"]?.takeIf { it.isNotEmpty() }?.let { user.put("flow", it) }
        return JSONObject()
            .put("protocol", "vless")
            .put(
                "settings", JSONObject().put(
                    "vnext", JSONArray().put(
                        JSONObject().put("address", u.host).put("port", u.port)
                            .put("users", JSONArray().put(user))
                    )
                )
            )
            .put("streamSettings", stream(q, u.host))
    }

    private fun trojan(link: String): JSONObject {
        val u = URI(link)
        val q = LinkParser.query(u)
        val sec = if (q["security"].isNullOrEmpty()) mapOf("security" to "tls") else emptyMap()
        return JSONObject()
            .put("protocol", "trojan")
            .put(
                "settings", JSONObject().put(
                    "servers", JSONArray().put(
                        JSONObject().put("address", u.host).put("port", u.port)
                            .put("password", java.net.URLDecoder.decode(u.rawUserInfo, "UTF-8"))
                    )
                )
            )
            .put("streamSettings", stream(q + sec, u.host))
    }

    private fun shadowsocks(link: String): JSONObject {
        val s = LinkParser.parseSs(link)
        return JSONObject()
            .put("protocol", "shadowsocks")
            .put(
                "settings", JSONObject().put(
                    "servers", JSONArray().put(
                        JSONObject().put("address", s.host).put("port", s.port)
                            .put("method", s.method).put("password", s.password)
                    )
                )
            )
    }

    private fun vmess(link: String): JSONObject {
        val j = JSONObject(LinkParser.decodeB64(link.removePrefix("vmess://"))!!)
        val q = mutableMapOf(
            "type" to j.optString("net", "tcp"),
            "security" to j.optString("tls", "none").ifEmpty { "none" },
            "sni" to j.optString("sni"),
            "host" to j.optString("host"),
            "path" to j.optString("path"),
            "alpn" to j.optString("alpn"),
            "fp" to j.optString("fp"),
            "serviceName" to j.optString("path"),
        )
        val host = j.getString("add")
        return JSONObject()
            .put("protocol", "vmess")
            .put(
                "settings", JSONObject().put(
                    "vnext", JSONArray().put(
                        JSONObject().put("address", host).put("port", j.get("port").toString().toInt())
                            .put(
                                "users", JSONArray().put(
                                    JSONObject().put("id", j.getString("id")).put("alterId", 0)
                                        .put("security", j.optString("scy", "auto").ifEmpty { "auto" })
                                )
                            )
                    )
                )
            )
            .put("streamSettings", stream(q, host))
    }

    /** Shared transport/security block driven by share-link query params. */
    private fun stream(q: Map<String, String>, host: String): JSONObject {
        val network = (q["type"] ?: "tcp").ifEmpty { "tcp" }
        val security = (q["security"] ?: "none").ifEmpty { "none" }
        val s = JSONObject().put("network", network).put("security", security)

        val sni = q["sni"]?.takeIf { it.isNotEmpty() } ?: q["host"]?.takeIf { it.isNotEmpty() } ?: host
        when (security) {
            "tls" -> s.put(
                "tlsSettings", JSONObject()
                    .put("serverName", sni)
                    .put("allowInsecure", q["allowInsecure"] == "1")
                    .apply {
                        q["alpn"]?.takeIf { it.isNotEmpty() }?.let { put("alpn", JSONArray(it.split(","))) }
                        q["fp"]?.takeIf { it.isNotEmpty() }?.let { put("fingerprint", it) }
                    }
            )
            "reality" -> s.put(
                "realitySettings", JSONObject()
                    .put("serverName", sni)
                    .put("fingerprint", q["fp"]?.ifEmpty { null } ?: "chrome")
                    .put("publicKey", q["pbk"] ?: "")
                    .put("shortId", q["sid"] ?: "")
                    .put("spiderX", q["spx"] ?: "")
            )
        }

        when (network) {
            "ws" -> s.put(
                "wsSettings", JSONObject()
                    .put("path", q["path"]?.ifEmpty { null } ?: "/")
                    .apply {
                        q["host"]?.takeIf { it.isNotEmpty() }?.let { put("headers", JSONObject().put("Host", it)) }
                    }
            )
            "grpc" -> s.put(
                "grpcSettings", JSONObject()
                    .put("serviceName", q["serviceName"] ?: "")
                    .put("multiMode", q["mode"] == "multi")
            )
            "httpupgrade" -> s.put(
                "httpupgradeSettings", JSONObject()
                    .put("path", q["path"]?.ifEmpty { null } ?: "/")
                    .put("host", q["host"] ?: "")
            )
            "xhttp", "splithttp" -> s.put("network", "xhttp").put(
                "xhttpSettings", JSONObject()
                    .put("path", q["path"]?.ifEmpty { null } ?: "/")
                    .put("host", q["host"] ?: "")
                    .put("mode", q["mode"]?.ifEmpty { null } ?: "auto")
            )
        }
        return s
    }
}
