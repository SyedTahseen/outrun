package app.xvpn.data

import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

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
        val c = openConnection("https://ipwho.is/", socksPort)
        val j = try {
            JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
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
    val real = MutableStateFlow<IpInfo?>(null)
    val proxy = MutableStateFlow<IpInfo?>(null)
}
