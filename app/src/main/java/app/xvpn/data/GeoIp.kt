package app.xvpn.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class IpInfo(
    val ip: String,
    val country: String,
    val city: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Public-IP + location lookup.
 * The app is excluded from the VPN tunnel, so a plain request always returns the REAL address.
 * To see the proxy's exit address we send the same request through sing-box's local SOCKS port.
 */
object GeoIp {
    /** Blocking: call from a background thread. socksPort = 0 -> direct (real IP). */
    fun lookup(socksPort: Int = 0): IpInfo? = runCatching {
        val url = URL("https://ipwho.is/")
        val c = (if (socksPort > 0)
            url.openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", socksPort)))
        else url.openConnection()) as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        if (!j.optBoolean("success", true)) return null
        IpInfo(
            ip = j.getString("ip"),
            country = j.optString("country"),
            city = j.optString("city"),
            lat = j.getDouble("latitude"),
            lon = j.getDouble("longitude"),
        )
    }.getOrNull()
}

/** Process-wide location info observed by the UI. */
object NetInfo {
    val real = kotlinx.coroutines.flow.MutableStateFlow<IpInfo?>(null)    // your real address (VPN off)
    val proxy = kotlinx.coroutines.flow.MutableStateFlow<IpInfo?>(null)   // exit address (VPN on)
}
