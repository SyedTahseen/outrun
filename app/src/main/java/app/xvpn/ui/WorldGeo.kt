package app.xvpn.ui

import android.content.Context
import android.util.Log
import app.xvpn.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

class Country(
    val code: String,
    val rings: List<FloatArray>,
    val minX: Float, val maxX: Float, val minY: Float, val maxY: Float,
)

object WorldGeo {
    private const val MAGIC = 0x574D5031
    @Volatile var cached: List<Country>? = null

    fun mercY(lat: Double): Float {
        val l = lat.coerceIn(-85.0, 85.0)
        return Math.toDegrees(ln(tan(PI / 4 + Math.toRadians(l) / 2))).toFloat()
    }

    suspend fun load(ctx: Context): List<Country> {
        cached?.let { return it }
        val app = ctx.applicationContext
        return withContext(Dispatchers.Default) {
            runCatching { read(app) }
                .onFailure {
                    Log.e("WorldGeo", "could not read world.bin - run scripts/build-map.py", it)
                    AppLog.i(app, "map", "could not read world.bin: ${it.javaClass.simpleName}")
                }
                .getOrElse { emptyList() }
                .also { if (it.isNotEmpty()) cached = it }
        }
    }

    private fun read(ctx: Context): List<Country> =
        DataInputStream(ctx.assets.open("world.bin").buffered(64 * 1024)).use { d ->
            require(d.readInt() == MAGIC) { "bad world.bin" }
            val n = d.readUnsignedShort()
            val out = ArrayList<Country>(n)
            repeat(n) {
                val code = String(charArrayOf(d.readByte().toInt().toChar(), d.readByte().toInt().toChar()))
                val rings = ArrayList<FloatArray>()
                var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE
                var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
                repeat(d.readUnsignedShort()) {
                    val cnt = d.readInt()
                    val ring = FloatArray(cnt * 2)
                    for (i in 0 until cnt) {
                        val lon = d.readShort() / 100f
                        val lat = d.readShort() / 100f
                        ring[i * 2] = lon; ring[i * 2 + 1] = lat
                        val y = mercY(lat.toDouble())
                        if (lon < x0) x0 = lon; if (lon > x1) x1 = lon
                        if (y < y0) y0 = y; if (y > y1) y1 = y
                    }
                    rings += ring
                }
                out += Country(code, rings, x0, x1, y0, y1)
            }
            out
        }

    fun centerOf(code: String): GeoPoint? {
        val c = cached?.firstOrNull { it.code == code.uppercase() } ?: return null
        val ring = c.rings.maxByOrNull { it.size } ?: return null
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        var i = 0
        while (i < ring.size) {
            if (ring[i] < x0) x0 = ring[i]; if (ring[i] > x1) x1 = ring[i]
            if (ring[i + 1] < y0) y0 = ring[i + 1]; if (ring[i + 1] > y1) y1 = ring[i + 1]
            i += 2
        }
        return GeoPoint(((x0 + x1) / 2f).toDouble(), ((y0 + y1) / 2f).toDouble())
    }

    fun codeAt(countries: List<Country>, lon: Double, lat: Double): String? {
        val y = mercY(lat)
        for (c in countries) {
            if (c.code == "--" || lon < c.minX || lon > c.maxX || y < c.minY || y > c.maxY) continue
            var inside = false
            for (r in c.rings) if (contains(r, lon, lat)) inside = !inside
            if (inside) return c.code
        }
        return null
    }

    private fun contains(r: FloatArray, lon: Double, lat: Double): Boolean {
        var inside = false
        var j = r.size - 2
        var i = 0
        while (i < r.size) {
            val xi = r[i]; val yi = r[i + 1]; val xj = r[j]; val yj = r[j + 1]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i; i += 2
        }
        return inside
    }
}
