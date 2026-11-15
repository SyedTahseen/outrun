package app.xvpn.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

data class IpInfo(
    val ip: String,
    val country: String,
    val code: String = "",
    val city: String,
    val lat: Double,
    val lon: Double,
)

object GeoIp {

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
            code = j.optString("country_code"),
            city = j.optString("city"),
            lat = j.getDouble("latitude"),
            lon = j.getDouble("longitude"),
        )
    }.getOrNull()
}

object NetInfo {
    val real = kotlinx.coroutines.flow.MutableStateFlow<IpInfo?>(null)
    val proxy = kotlinx.coroutines.flow.MutableStateFlow<IpInfo?>(null)
}
