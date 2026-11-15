package app.xvpn.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GlobeRing(
    val xyz: FloatArray, val n: Int,
    val cx: Float, val cy: Float, val cz: Float,
    val radius: Float,
    val ccw: Boolean,
)

class GlobeCountry(val code: String, val rings: List<GlobeRing>)

object GlobeGeo {
    @Volatile var cached: List<GlobeCountry>? = null

    suspend fun load(ctx: Context): List<GlobeCountry> {
        cached?.let { return it }
        val countries = WorldGeo.load(ctx)
        return withContext(Dispatchers.Default) {
            countries.map { c -> GlobeCountry(c.code, c.rings.filter { it.size >= 6 }.map { ring(it) }) }
                .also { if (it.isNotEmpty()) cached = it }
        }
    }

    private fun ring(r: FloatArray): GlobeRing {
        val n = r.size / 2
        val xyz = FloatArray(n * 3)
        var sx = 0.0; var sy = 0.0; var sz = 0.0
        var area = 0.0
        for (i in 0 until n) {
            val lon = Math.toRadians(r[i * 2].toDouble()); val lat = Math.toRadians(r[i * 2 + 1].toDouble())
            val x = cos(lat) * cos(lon); val y = cos(lat) * sin(lon); val z = sin(lat)
            xyz[i * 3] = x.toFloat(); xyz[i * 3 + 1] = y.toFloat(); xyz[i * 3 + 2] = z.toFloat()
            sx += x; sy += y; sz += z
            val j = (i + 1) % n
            area += r[i * 2].toDouble() * r[j * 2 + 1] - r[j * 2].toDouble() * r[i * 2 + 1]
        }
        val len = sqrt(sx * sx + sy * sy + sz * sz)
        if (len < 1e-6) return GlobeRing(xyz, n, 1f, 0f, 0f, Math.PI.toFloat(), area > 0)
        val cx = (sx / len).toFloat(); val cy = (sy / len).toFloat(); val cz = (sz / len).toFloat()
        var maxAng = 0f
        for (i in 0 until n) {
            val d = (xyz[i * 3] * cx + xyz[i * 3 + 1] * cy + xyz[i * 3 + 2] * cz).coerceIn(-1f, 1f)
            val a = acos(d)
            if (a > maxAng) maxAng = a
        }
        return GlobeRing(xyz, n, cx, cy, cz, maxAng + 0.002f, area > 0)
    }
}
