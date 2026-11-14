package app.xvpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

data class GeoPoint(val lon: Double, val lat: Double)

private fun poly(vararg v: Double) = FloatArray(v.size) { v[it].toFloat() }

/** Very coarse continent outlines, (lon, lat) pairs. Enough for a stylised backdrop; no assets needed. */
private val LAND: List<FloatArray> = listOf(
    // North America
    poly(-167.0,68.0, -156.0,71.0, -141.0,69.0, -125.0,70.0, -110.0,68.0, -95.0,69.0, -85.0,70.0, -82.0,64.0, -92.0,62.0,
        -94.0,57.0, -86.0,55.0, -79.0,52.0, -78.0,58.0, -70.0,60.0, -64.0,58.0, -60.0,54.0, -56.0,52.0, -65.0,47.0,
        -70.0,43.0, -74.0,40.0, -76.0,35.0, -81.0,31.0, -80.0,26.0, -83.0,29.0, -89.0,30.0, -94.0,29.0, -97.0,26.0,
        -97.0,21.0, -95.0,18.0, -91.0,19.0, -87.0,21.0, -88.0,16.0, -84.0,15.0, -83.0,10.0, -79.0,9.0, -77.0,8.0,
        -80.0,7.0, -85.0,10.0, -87.0,13.0, -92.0,14.0, -96.0,16.0, -105.0,20.0, -106.0,23.0, -110.0,27.0, -114.0,31.0,
        -117.0,32.0, -121.0,35.0, -124.0,40.0, -124.0,47.0, -128.0,51.0, -134.0,57.0, -140.0,60.0, -148.0,60.0,
        -152.0,58.0, -160.0,56.0, -165.0,60.0, -166.0,64.0),
    // Greenland
    poly(-73.0,78.0, -60.0,82.0, -35.0,83.0, -20.0,81.0, -20.0,72.0, -30.0,68.0, -43.0,60.0, -50.0,64.0, -55.0,70.0, -68.0,76.0),
    // South America
    poly(-77.0,8.0, -72.0,12.0, -62.0,10.0, -52.0,5.0, -50.0,0.0, -44.0,-2.0, -35.0,-5.0, -39.0,-14.0, -41.0,-22.0,
        -48.0,-26.0, -53.0,-34.0, -58.0,-38.0, -63.0,-41.0, -66.0,-47.0, -69.0,-52.0, -68.0,-55.0, -73.0,-52.0,
        -74.0,-45.0, -73.0,-37.0, -71.0,-30.0, -70.0,-18.0, -76.0,-14.0, -81.0,-6.0, -80.0,-1.0, -78.0,3.0),
    // Eurasia
    poly(-9.0,37.0, -9.0,43.0, -2.0,43.5, -1.0,46.0, -4.0,48.0, 2.0,51.0, 5.0,53.0, 8.0,54.0, 10.0,54.5, 14.0,54.0,
        20.0,54.5, 21.0,57.0, 24.0,57.5, 24.0,59.5, 30.0,60.0, 34.0,66.0, 40.0,66.0, 44.0,68.0, 60.0,69.0, 70.0,73.0,
        80.0,73.0, 100.0,77.0, 113.0,74.0, 130.0,72.0, 140.0,72.0, 160.0,70.0, 170.0,70.0, 180.0,68.0, 180.0,65.0,
        174.0,62.0, 164.0,60.0, 162.0,56.0, 156.0,51.0, 155.0,57.0, 143.0,59.0, 137.0,54.0, 141.0,52.0, 140.0,48.0,
        135.0,43.0, 130.0,42.0, 129.0,35.0, 126.0,35.0, 126.0,38.0, 125.0,40.0, 122.0,40.0, 121.0,37.0, 119.0,38.0,
        122.0,31.0, 120.0,27.0, 117.0,23.0, 110.0,21.0, 108.0,21.0, 106.0,18.0, 109.0,12.0, 105.0,9.0, 101.0,13.0,
        100.0,8.0, 103.0,1.5, 100.0,6.0, 98.0,10.0, 98.0,16.0, 94.0,17.0, 94.0,20.0, 92.0,22.0, 88.0,22.0, 86.0,20.0,
        80.0,15.0, 80.0,10.0, 77.0,8.0, 73.0,16.0, 72.0,21.0, 68.0,23.0, 66.0,25.0, 57.0,25.0, 56.0,27.0, 50.0,30.0,
        48.0,30.0, 50.0,27.0, 51.0,24.0, 56.0,25.0, 59.0,22.0, 52.0,16.0, 43.0,13.0, 39.0,21.0, 35.0,28.0, 34.0,31.0,
        36.0,36.0, 30.0,36.0, 27.0,37.0, 26.0,40.0, 23.0,40.0, 23.0,37.0, 21.0,39.0, 19.0,42.0, 13.0,45.0, 12.0,44.0,
        18.0,40.0, 16.0,38.0, 15.0,40.0, 10.0,44.0, 7.0,44.0, 3.0,43.0, 0.0,39.0, -2.0,37.0, -6.0,36.0),
    // Scandinavia
    poly(5.0,59.0, 5.0,62.0, 14.0,68.0, 20.0,70.0, 28.0,71.0, 31.0,70.0, 28.0,66.0, 25.0,66.0, 22.0,65.0, 17.0,62.0,
        19.0,60.0, 16.0,56.0, 12.0,56.0, 11.0,59.0, 7.0,58.0),
    // Africa
    poly(-17.0,21.0, -16.0,28.0, -10.0,30.0, -6.0,36.0, 10.0,37.0, 11.0,33.0, 20.0,31.0, 30.0,31.0, 33.0,28.0, 37.0,22.0,
        43.0,12.0, 51.0,12.0, 50.0,8.0, 40.0,-3.0, 40.0,-10.0, 35.0,-20.0, 33.0,-26.0, 28.0,-33.0, 20.0,-35.0, 18.0,-32.0,
        12.0,-18.0, 13.0,-8.0, 9.0,-1.0, 9.0,4.0, 5.0,5.0, -4.0,5.0, -8.0,4.0, -13.0,8.0, -17.0,14.0),
    // Australia
    poly(114.0,-22.0, 122.0,-18.0, 130.0,-12.0, 137.0,-12.0, 136.0,-15.0, 141.0,-17.0, 142.0,-11.0, 146.0,-19.0,
        153.0,-26.0, 151.0,-34.0, 147.0,-38.0, 141.0,-38.0, 135.0,-34.0, 129.0,-32.0, 115.0,-34.0),
    // British Isles
    poly(-5.0,50.0, 1.0,51.0, 2.0,53.0, -2.0,56.0, -3.0,58.5, -6.0,58.0, -5.0,55.0, -3.0,54.0, -5.0,52.0),
    poly(-10.0,52.0, -6.0,52.0, -6.0,55.0, -9.0,54.0),
    // Iceland
    poly(-24.0,65.0, -14.0,66.0, -14.0,64.0, -22.0,63.5),
    // Japan
    poly(130.0,31.0, 135.0,34.0, 140.0,35.0, 142.0,40.0, 141.0,43.0, 145.0,44.0, 141.0,45.5, 140.0,41.0, 139.0,38.0, 136.0,36.0, 131.0,34.0),
    // Taiwan, Philippines, Sri Lanka
    poly(121.0,25.0, 122.0,23.0, 120.5,22.0),
    poly(120.0,18.0, 122.0,14.0, 126.0,7.0, 122.0,8.0, 120.0,14.0),
    poly(80.0,9.5, 82.0,7.0, 80.0,6.0, 79.8,8.0),
    // Indonesia / New Guinea
    poly(95.0,5.0, 98.0,4.0, 104.0,-2.0, 106.0,-6.0, 102.0,-4.0, 97.0,0.0),
    poly(109.0,2.0, 117.0,7.0, 119.0,1.0, 116.0,-4.0, 110.0,-3.0),
    poly(106.0,-6.0, 114.0,-8.0, 106.0,-7.5),
    poly(119.0,1.0, 125.0,1.5, 122.0,-1.0, 121.0,-5.0, 119.5,-5.0),
    poly(131.0,-1.0, 141.0,-3.0, 150.0,-10.0, 142.0,-9.0, 138.0,-8.0),
    // New Zealand, Madagascar, Cuba
    poly(172.0,-35.0, 178.0,-38.0, 175.0,-41.0, 173.0,-41.0),
    poly(172.0,-41.0, 174.0,-42.0, 170.0,-46.0, 167.0,-46.0),
    poly(49.0,-12.0, 50.0,-16.0, 47.0,-25.0, 44.0,-22.0, 44.0,-17.0),
    poly(-85.0,22.0, -77.0,20.0, -74.0,20.0, -80.0,23.0),
)

private fun wrap(d: Double): Double { var x = d; while (x > 180) x -= 360; while (x < -180) x += 360; return x }

/**
 * Stylised map. The dot stays in the centre; the map slides underneath it (zooming out a little on long trips)
 * whenever [target] changes, so the dot appears to travel from your location to the proxy location.
 */
@Composable
fun WorldMap(
    target: GeoPoint,
    dotColor: Color,
    pulseMs: Int,
    modifier: Modifier = Modifier,
    centerY: Float = 0.5f,
) {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val prog = remember { Animatable(1f) }

    LaunchedEffect(target) {
        val t = prog.value.toDouble()
        from = GeoPoint(from.lon + wrap(to.lon - from.lon) * t, from.lat + (to.lat - from.lat) * t)
        to = target
        prog.snapTo(0f)
        prog.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
    }

    val dot by animateColorAsState(dotColor, tween(600), label = "dot")
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(pulseMs, easing = LinearEasing), RepeatMode.Restart), label = "p",
    )

    Canvas(modifier) {
        val t = prog.value
        val dLon = wrap(to.lon - from.lon)
        val dLat = to.lat - from.lat
        val cLon = (from.lon + dLon * t).toFloat()
        val cLat = (from.lat + dLat * t).toFloat()
        val dist = hypot(abs(dLon), abs(dLat)).toFloat()
        val dip = (dist / 100f).coerceAtMost(0.6f)
        val k = size.width / 46f * (1f - dip * sin(PI * t).toFloat())   // px per degree
        val cx = size.width / 2f
        val cy = size.height * centerY

        val path = Path()
        val stroke = Stroke(1.dp.toPx())
        for (poly in LAND) for (off in intArrayOf(-360, 0, 360)) {
            path.reset()
            var i = 0
            while (i < poly.size) {
                val x = cx + (poly[i] + off - cLon) * k
                val y = cy - (poly[i + 1] - cLat) * k
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                i += 2
            }
            path.close()
            drawPath(path, C.Land)
            drawPath(path, C.LandLine, style = stroke)
        }

        val c = Offset(cx, cy)
        val glow = 46.dp.toPx()
        drawCircle(
            Brush.radialGradient(listOf(dot.copy(alpha = 0f), dot.copy(alpha = 0.12f), dot.copy(alpha = 0.55f)), c, glow),
            glow, c,
        )
        // blinking ring + core
        val ringR = 10.dp.toPx() + 36.dp.toPx() * pulse
        drawCircle(dot.copy(alpha = 0.6f * (1f - pulse)), ringR, c, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, 13.dp.toPx(), c)
        drawCircle(dot.copy(alpha = 0.65f + 0.35f * (1f - pulse)), 8.dp.toPx(), c)
    }
}
