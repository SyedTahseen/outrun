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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

data class GeoPoint(val lon: Double, val lat: Double)

private fun wrap(d: Double): Double { var x = d; while (x > 180) x -= 360; while (x < -180) x += 360; return x }

/** Degrees of longitude visible across the screen width (the design shows roughly one mid-sized country). */
private const val VIEW_DEG = 30f

/**
 * Country map (real borders, Mercator). The dot stays in the centre; the map slides underneath it (zooming out a
 * little on long trips) whenever [target] changes, so the dot appears to travel from your location to the proxy.
 * The country the dot is in ([countryCode], ISO alpha-2; falls back to a lookup from [target]) is drawn lighter.
 */
@Composable
fun WorldMap(
    target: GeoPoint,
    countryCode: String?,
    dotColor: Color,
    pulseMs: Int,
    modifier: Modifier = Modifier,
    centerY: Float = 0.5f,
) {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val prog = remember { Animatable(1f) }

    val ctx = LocalContext.current
    val geo by produceState<List<Country>?>(WorldGeo.cached, ctx) { value = WorldGeo.load(ctx) }
    val activeNow = remember(geo, countryCode, target) {
        countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }
            ?: geo?.let { WorldGeo.codeAt(it, target.lon, target.lat) }
    }
    var oldCode by remember { mutableStateOf<String?>(null) }   // highlighted country fading out
    var newCode by remember { mutableStateOf(countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }) }   // fading in

    LaunchedEffect(target, activeNow) {
        if (target == to && activeNow == newCode) return@LaunchedEffect   // nothing changed (e.g. screen re-entered): no animation
        oldCode = newCode
        newCode = activeNow
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

    Box(modifier) {
    // map layer: only redraws while the camera moves, not on every pulse frame
    Canvas(Modifier.fillMaxSize()) {
        val t = prog.value
        val dLon = wrap(to.lon - from.lon)
        val dLat = to.lat - from.lat
        val cLon = (from.lon + dLon * t).toFloat()
        val cLat = (from.lat + dLat * t).toFloat()
        val dist = hypot(abs(dLon), abs(dLat)).toFloat()
        val dip = (dist / 100f).coerceAtMost(0.6f)
        val k = size.width / VIEW_DEG * (1f - dip * sin(PI * t).toFloat())   // px per degree
        val cx = size.width / 2f
        val cy = size.height * centerY
        val cMy = WorldGeo.mercY(cLat.toDouble())

        // visible window in map units (x = lon, y = Mercator northing) - used to skip off-screen countries
        val halfW = cx / k
        val above = cy / k
        val below = (size.height - cy) / k
        val vy0 = cMy - below
        val vy1 = cMy + above

        // highlight fades: the old country dims during the first half of the trip, the new one lights up in the second
        val fadeOut = (1f - t * 2f).coerceIn(0f, 1f)
        val fadeIn = ((t - 0.5f) * 2f).coerceIn(0f, 1f)
        fun lit(code: String): Float = when {
            code == "--" -> 0f
            code == newCode && code == oldCode -> 1f
            code == newCode -> fadeIn
            code == oldCode -> fadeOut
            else -> 0f
        }

        val list = geo
        if (list != null) {
            val line = Stroke(0.8.dp.toPx() / k, join = StrokeJoin.Round)   // drawn in map units, so divide by scale
            for (off in intArrayOf(-360, 0, 360)) {
                val vx0 = cLon - halfW - off
                val vx1 = cLon + halfW - off
                withTransform({
                    translate(cx, cy)
                    scale(k, -k, Offset.Zero)               // north is up
                    translate(off - cLon, -cMy)
                }) {
                    // fills first, then borders on top, so a neighbour never paints over a shared border
                    for (c in list) {
                        if (c.maxX < vx0 || c.minX > vx1 || c.maxY < vy0 || c.minY > vy1) continue
                        drawPath(c.path, lerp(C.Land, C.LandActive, lit(c.code)))
                    }
                    for (c in list) {
                        if (c.maxX < vx0 || c.minX > vx1 || c.maxY < vy0 || c.minY > vy1) continue
                        drawPath(c.path, C.LandLine, style = line)
                    }
                }
            }
        }

    }

    // dot layer: pulses continuously
    Canvas(Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2f, size.height * centerY)
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
}
