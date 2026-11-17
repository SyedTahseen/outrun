package app.xvpn.data

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

internal fun openConnection(url: String, socksPort: Int = 0, timeoutMs: Int = 8000): HttpURLConnection {
    val u = URL(url)
    val c = (if (socksPort > 0) u.openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
    else u.openConnection()) as HttpURLConnection
    c.connectTimeout = timeoutMs
    c.readTimeout = timeoutMs
    return c
}
