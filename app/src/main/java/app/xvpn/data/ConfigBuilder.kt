package app.xvpn.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

object ConfigBuilder {
    fun outbound(link: String): JSONObject {
        val proxy = when (LinkParser.protocolOf(link)) {
            "vless" -> vless(link)
            "trojan" -> trojan(link)
            "ss" -> shadowsocks(link)
            "vmess" -> vmess(link)
            "hysteria2" -> hysteria2(link)
            "tuic" -> tuic(link)
            "wireguard" -> wireguard(link)
            "socks" -> socks(link)
            "http" -> httpProxy(link)
            else -> error("Unsupported link")
        }
        proxy.put("tag", "proxy")
        return proxy
    }

    private fun isEndpoint(o: JSONObject) = o.optString("type") == "wireguard"

    private fun directOutbound() = JSONObject().put("type", "direct").put("tag", "direct")

    private fun singleOutbounds(proxy: JSONObject, endpoint: Boolean) = JSONArray().apply {
        if (!endpoint) put(proxy)
        put(directOutbound())
    }

    private fun socksInbound(tag: String, port: Int, creds: Creds) =
        JSONObject().put("type", "socks").put("tag", tag).put("listen", "127.0.0.1").put("listen_port", port)
            .put("users", JSONArray().put(JSONObject().put("username", creds.user).put("password", creds.pass)))

    private fun dnsKind(proxy: JSONObject) = when (proxy.optString("type")) {
        "http" -> "https"
        "socks" -> "tcp"
        else -> "udp"
    }

    private fun carriesUdp(proxy: JSONObject) = dnsKind(proxy) == "udp"

    private fun dnsBlock(dns: String = "1.1.1.1", viaProxy: Boolean = false, kind: String = "udp"): JSONObject {
        val servers = JSONArray().put(JSONObject().put("type", "udp").put("tag", "dns-direct").put("server", dns))
        if (viaProxy) servers.put(
            JSONObject().put("type", kind).put("tag", "dns-proxy").put("server", dns).put("detour", "proxy")
        )
        return JSONObject().put("servers", servers).put("final", if (viaProxy) "dns-proxy" else "dns-direct")
    }

    fun buildMulti(outs: List<JSONObject>, ports: List<Int>, creds: Creds): String {
        val inbounds = JSONArray(); val outbounds = JSONArray(); val rules = JSONArray(); val endpoints = JSONArray()
        outs.forEachIndexed { i, o ->
            (if (isEndpoint(o)) endpoints else outbounds).put(o.put("tag", "p$i"))
            inbounds.put(socksInbound("in$i", ports[i], creds))
            rules.put(JSONObject().put("inbound", JSONArray().put("in$i")).put("action", "route").put("outbound", "p$i"))
        }
        outbounds.put(directOutbound())
        val root = JSONObject()
            .put("log", JSONObject().put("level", "error"))
            .put("dns", dnsBlock())
            .put("inbounds", inbounds)
            .put("outbounds", outbounds)
            .put("route", JSONObject().put("rules", rules).put("final", "direct").put("default_domain_resolver", "dns-direct"))
        if (endpoints.length() > 0) root.put("endpoints", endpoints)
        return root.toString()
    }

    private fun quicRule() = JSONObject().put("network", "udp").put("port", 443).put("action", "reject")

    fun build(link: String, socksPort: Int, creds: Creds, dns: String = "1.1.1.1", blockQuic: Boolean = false): String {
        val proxy = outbound(link)
        val endpoint = isEndpoint(proxy)

        val outs = singleOutbounds(proxy, endpoint)

        val root = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put("dns", dnsBlock(dns, viaProxy = endpoint || !carriesUdp(proxy), kind = dnsKind(proxy)))
            .put("inbounds", JSONArray().put(socksInbound("socks-in", socksPort, creds)))
            .put("outbounds", outs)
            .put(
                "route", JSONObject()
                    .put("final", "proxy")
                    .put("default_domain_resolver", "dns-direct")
                    .apply {
                        val rules = JSONArray()
                        if (!carriesUdp(proxy)) rules.put(JSONObject().put("port", 53).put("action", "hijack-dns"))
                        if (blockQuic) rules.put(quicRule())
                        if (rules.length() > 0) put("rules", rules)
                    }
            )
        if (endpoint) root.put("endpoints", JSONArray().put(proxy))
        return root.toString()
    }

    class RootTun(
        val iface: String,
        val mtu: Int,
        val blockIpv6: Boolean,
        val lanExclude: List<String>,
        val includePackages: List<String>,
        val excludePackages: List<String>,
        val includeIps: List<String>,
        val excludeIps: List<String>,
        val clashPort: Int,
        val blockQuic: Boolean = false,
    )

    fun buildRoot(link: String, socksPort: Int, creds: Creds, dns: String, o: RootTun): String {
        val proxy = outbound(link)
        val endpoint = isEndpoint(proxy)

        val outs = singleOutbounds(proxy, endpoint)

        val tun = JSONObject()
            .put("type", "tun").put("tag", "tun-in").put("interface_name", o.iface)
            .put("address", JSONArray().put("172.19.0.1/30").put("fdfe:dcba:9876::1/126"))
            .put("mtu", o.mtu)
            .put("auto_route", true)
            .put("auto_redirect", true)
        if (o.includePackages.isNotEmpty()) tun.put("include_package", JSONArray(o.includePackages))
        else if (o.excludePackages.isNotEmpty()) tun.put("exclude_package", JSONArray(o.excludePackages))
        if (o.includeIps.isNotEmpty()) tun.put("route_address", JSONArray(o.includeIps))
        val excluded = (if (o.includeIps.isEmpty()) o.lanExclude else emptyList()) + o.excludeIps
        if (excluded.isNotEmpty()) tun.put("route_exclude_address", JSONArray(excluded))

        val rules = JSONArray()
            .put(JSONObject().put("action", "sniff"))
            .put(JSONObject().put("protocol", "dns").put("action", "hijack-dns"))
        if (o.blockIpv6) rules.put(JSONObject().put("ip_version", 6).put("action", "reject"))
        if (o.blockQuic) rules.put(quicRule())

        val dnsObj = dnsBlock(dns, viaProxy = true, kind = dnsKind(proxy))
        val resolver: Any = if (o.blockIpv6) {
            dnsObj.put("strategy", "ipv4_only")
            JSONObject().put("server", "dns-direct").put("strategy", "ipv4_only")
        } else "dns-direct"

        val root = JSONObject()
            .put("log", JSONObject().put("level", "warn"))
            .put("dns", dnsObj)
            .put("inbounds", JSONArray().put(socksInbound("socks-in", socksPort, creds)).put(tun))
            .put("outbounds", outs)
            .put(
                "route", JSONObject()
                    .put("rules", rules)
                    .put("final", "proxy")
                    .put("auto_detect_interface", true)
                    .put("default_domain_resolver", resolver)
            )
        if (endpoint) root.put("endpoints", JSONArray().put(proxy))
        if (o.clashPort > 0) {
            root.put(
                "experimental",
                JSONObject().put(
                    "clash_api",
                    JSONObject().put("external_controller", "127.0.0.1:${o.clashPort}").put("secret", creds.secret),
                ),
            )
        }
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
            .put("password", URLDecoder.decode(u.rawUserInfo, "UTF-8"))
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
        val j = LinkParser.vmessJson(link)
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

    private fun truthy(v: String?) = v == "1" || v.equals("true", ignoreCase = true)

    private fun quicTls(q: Map<String, String>, host: String, defaultAlpn: String): JSONObject {
        val sni = q["sni"]?.takeIf { it.isNotEmpty() } ?: q["peer"]?.takeIf { it.isNotEmpty() } ?: host
        val t = JSONObject().put("enabled", true).put("server_name", sni)
        if (truthy(q["insecure"]) || truthy(q["allowInsecure"]) || truthy(q["allow_insecure"])) t.put("insecure", true)
        t.put("alpn", alpnList(q["alpn"]?.takeIf { it.isNotEmpty() } ?: defaultAlpn))
        return t
    }

    private fun alpnList(csv: String) = JSONArray(csv.split(",").map { it.trim() }.filter { it.isNotEmpty() })

    private fun hysteria2(link: String): JSONObject {
        val a = LinkParser.authority(link, defaultPort = 443)
        val q = a.query

        if ((q["pinSHA256"] ?: q["pin-sha256"] ?: q["pinsha256"]).orEmpty().isNotEmpty()) {
            error("This link pins its certificate (pinSHA256), which sing-box does not support. Use a server with a valid certificate or remove the pin.")
        }
        val o = JSONObject()
            .put("type", "hysteria2")
            .put("server", a.host)
            .put("server_port", a.port)
            .put("password", a.userinfo)
            .put("tls", quicTls(q, a.host, "h3"))

        val ranges = ArrayList<String>()
        a.portSpec.split(',').drop(1).map { it.trim() }.filter { it.isNotEmpty() }.forEach { ranges += it.replace('-', ':') }
        q["mport"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach { ranges += it.replace('-', ':') }
        if (ranges.isNotEmpty()) {
            val all = JSONArray().put("${a.port}:${a.port}")
            ranges.forEach { all.put(if (':' in it) it else "$it:$it") }
            o.put("server_ports", all)
        }
        val obfs = q["obfs"].orEmpty()
        if (obfs.isNotEmpty() && obfs != "none") {
            o.put("obfs", JSONObject().put("type", obfs).put("password", q["obfs-password"] ?: q["obfs_password"] ?: ""))
        }
        return o
    }

    private fun tuic(link: String): JSONObject {
        val a = LinkParser.authority(link)
        val q = a.query
        val o = JSONObject()
            .put("type", "tuic")
            .put("server", a.host)
            .put("server_port", a.port)
            .put("uuid", a.user)
            .put("tls", quicTls(q, a.host, "h3"))
        a.pass?.let { o.put("password", it) }
        (q["congestion_control"] ?: q["congestion-control"])?.takeIf { it.isNotEmpty() }?.let { o.put("congestion_control", it) }
        (q["udp_relay_mode"] ?: q["udp-relay-mode"])?.takeIf { it == "native" || it == "quic" }?.let { o.put("udp_relay_mode", it) }
        return o
    }

    private fun socks(link: String): JSONObject {
        val a = LinkParser.authority(link, defaultPort = 1080)
        var user = a.user
        var pass = a.pass

        if (pass == null && user.isNotEmpty() && link.startsWith("socks://", ignoreCase = true)) {
            LinkParser.decodeB64(user)?.takeIf { ':' in it }?.let { user = it.substringBefore(':'); pass = it.substringAfter(':') }
        }
        val o = JSONObject().put("type", "socks").put("server", a.host).put("server_port", a.port).put("version", "5")
        if (user.isNotEmpty()) {
            o.put("username", user)
            pass?.let { o.put("password", it) }
        }
        return o
    }

    private fun httpProxy(link: String): JSONObject {
        val a = LinkParser.authority(link)
        val o = JSONObject().put("type", "http").put("server", a.host).put("server_port", a.port)
        if (a.user.isNotEmpty()) {
            o.put("username", a.user)
            a.pass?.let { o.put("password", it) }
        }
        if (link.startsWith("https://", ignoreCase = true)) {
            val t = JSONObject().put("enabled", true)
                .put("server_name", a.query["sni"]?.takeIf { it.isNotEmpty() } ?: a.host)
            if (truthy(a.query["insecure"]) || truthy(a.query["allowInsecure"])) t.put("insecure", true)
            o.put("tls", t)
        }
        return o
    }

    private fun wireguard(link: String): JSONObject {
        val a = LinkParser.authority(link)
        val q = a.query
        fun pick(vararg keys: String) = keys.firstNotNullOfOrNull { k -> q[k]?.takeIf { it.isNotEmpty() } }
        val pub = pick("publickey", "public_key", "peer_public_key", "pbk") ?: error("WireGuard link has no public key")
        val addrs = (pick("address", "ip") ?: error("WireGuard link has no local address"))
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .map { if ('/' in it) it else if (':' in it) "$it/128" else "$it/32" }

        val peer = JSONObject()
            .put("address", a.host)
            .put("port", a.port)
            .put("public_key", pub)
            .put("allowed_ips", JSONArray().put("0.0.0.0/0").put("::/0"))
        pick("presharedkey", "psk", "preshared_key", "pre_shared_key")?.let { peer.put("pre_shared_key", it) }
        pick("reserved")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 3 }
            ?.let { peer.put("reserved", JSONArray(it)) }

        return JSONObject()
            .put("type", "wireguard")
            .put("system", false)
            .put("mtu", pick("mtu")?.toIntOrNull() ?: 1408)
            .put("address", JSONArray(addrs))
            .put("private_key", a.userinfo)
            .put("peers", JSONArray().put(peer))
    }

    private fun tls(q: Map<String, String>, host: String): JSONObject? {
        val security = q["security"].orEmpty()
        if (security != "tls" && security != "reality") return null

        val sni = q["sni"]?.takeIf { it.isNotEmpty() } ?: q["host"]?.takeIf { it.isNotEmpty() } ?: host
        val t = JSONObject().put("enabled", true).put("server_name", sni)
        if (truthy(q["allowInsecure"]) || truthy(q["insecure"])) t.put("insecure", true)
        q["alpn"]?.takeIf { it.isNotEmpty() }?.let { t.put("alpn", alpnList(it)) }

        val fp = q["fp"].orEmpty()
        if (security == "reality") {
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
