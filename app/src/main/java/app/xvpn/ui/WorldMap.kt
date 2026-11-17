package app.xvpn.ui

import android.provider.Settings
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.xvpn.R
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

data class GeoPoint(val lon: Double, val lat: Double)

enum class MapPin { Selected, Connecting, Connected }

private fun wrap(d: Double): Double { var x = d; while (x > 180) x -= 360; while (x < -180) x += 360; return x }

private const val VIEW_DEG = 30f

private val R_DEFAULT = (180.0 / Math.PI).toFloat() / VIEW_DEG
private const val ZOOM_MIN = 0.2f
private const val ZOOM_MAX = 6f

private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

private class Scratch {
    var xs = FloatArray(2048); var ys = FloatArray(2048); var n = 0
    var rr = FloatArray(1024); var uu = FloatArray(1024); var dd = FloatArray(1024)
    fun add(x: Float, y: Float) {
        if (n == xs.size) { xs = xs.copyOf(n * 2); ys = ys.copyOf(n * 2) }
        xs[n] = x; ys[n] = y; n++
    }
    fun ensure(m: Int) {
        if (rr.size < m) { rr = FloatArray(m * 2); uu = FloatArray(m * 2); dd = FloatArray(m * 2) }
    }
}

private fun addArc(sc: Scratch, from: Float, to: Float, ccw: Boolean, cx: Float, cy: Float, r: Float) {
    val tau = (2 * Math.PI).toFloat()
    var sweep = if (ccw) to - from else from - to
    sweep %= tau
    if (sweep < 0f) sweep += tau
    if (sweep > tau - 0.02f) sweep = 0f
    val signed = if (ccw) sweep else -sweep
    val steps = ceil(sweep / 0.0873f).toInt()
    for (i in 1 until steps) {
        val a = from + signed * i / steps
        sc.add(cx + r * cos(a), cy - r * sin(a))
    }
}

private fun addRing(
    path: Path, ring: GlobeRing, step: Int, cl: Float, sl: Float, cp: Float, sp: Float,
    cx: Float, cy: Float, r: Float, sc: Scratch,
): Boolean {
    val n = ring.n
    val st = if (n < 16) 1 else step
    val m = (n + st - 1) / st
    if (m < 3) return false
    sc.ensure(m)
    val rr = sc.rr; val uu = sc.uu; val dd = sc.dd
    val xyz = ring.xyz
    var first = -1
    for (j in 0 until m) {
        val i = j * st * 3
        val x = xyz[i]; val y = xyz[i + 1]; val z = xyz[i + 2]
        val a = x * cl + y * sl
        rr[j] = -x * sl + y * cl
        dd[j] = a * cp + z * sp
        uu[j] = -a * sp + z * cp
        if (first < 0 && dd[j] > 0f) first = j
    }
    if (first < 0) return false

    sc.n = 0
    sc.add(cx + r * rr[first], cy - r * uu[first])
    var exitA = 0f
    for (k in 1..m) {
        val j = (first + k) % m
        val p = (first + k - 1) % m
        val visP = dd[p] > 0f
        val visJ = dd[j] > 0f
        if (visP && visJ) {
            if (k < m) sc.add(cx + r * rr[j], cy - r * uu[j])
        } else if (visP || visJ) {
            val t = dd[p] / (dd[p] - dd[j])
            var xr = rr[p] + t * (rr[j] - rr[p])
            var xu = uu[p] + t * (uu[j] - uu[p])
            val len = kotlin.math.hypot(xr, xu)
            if (len > 1e-6f) { xr /= len; xu /= len }
            if (visP) {
                sc.add(cx + r * xr, cy - r * xu)
                exitA = atan2(xu, xr)
            } else {
                addArc(sc, exitA, atan2(xu, xr), ring.ccw, cx, cy, r)
                sc.add(cx + r * xr, cy - r * xu)
                if (k < m) sc.add(cx + r * rr[j], cy - r * uu[j])
            }
        }
    }
    if (sc.n < 3) return false
    path.moveTo(sc.xs[0], sc.ys[0])
    for (q in 1 until sc.n) path.lineTo(sc.xs[q], sc.ys[q])
    path.close()
    return true
}

private class Cam(val vLon: Double, val vLat: Double, val mLon: Double, val mLat: Double, val r: Float, val space: Float)

@Composable
fun WorldMap(
    target: GeoPoint,
    countryCode: String?,
    dotColor: Color,
    modifier: Modifier = Modifier,
    pin: MapPin = MapPin.Selected,
    centerY: Float = 0.5f,
) {
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val prog = remember { Animatable(1f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offLon by remember { mutableFloatStateOf(0f) }
    var offLat by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val job = remember { arrayOfNulls<Job>(1) }

    val ctx = LocalContext.current
    val geo by produceState<List<Country>?>(WorldGeo.cached, ctx) { value = WorldGeo.load(ctx) }
    val globe by produceState<List<GlobeCountry>?>(GlobeGeo.cached, ctx) { value = GlobeGeo.load(ctx) }
    val activeNow = remember(geo, countryCode, target) {
        countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }
            ?: geo?.let { WorldGeo.codeAt(it, target.lon, target.lat) }
    }
    var oldCode by remember { mutableStateOf<String?>(null) }
    var newCode by remember { mutableStateOf(countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 }) }

    LaunchedEffect(target, activeNow) {
        if (target == to && activeNow == newCode) return@LaunchedEffect
        oldCode = newCode
        newCode = activeNow
        val t = prog.value.toDouble()
        from = GeoPoint(from.lon + wrap(to.lon - from.lon) * t, from.lat + (to.lat - from.lat) * t)
        to = target

        val l0 = offLon; val a0 = offLat
        if (l0 != 0f || a0 != 0f) {
            job[0]?.cancel()
            job[0] = scope.launch {
                animate(0f, 1f, animationSpec = tween(2000, easing = FastOutSlowInEasing)) { v, _ ->
                    offLon = l0 * (1f - v); offLat = a0 * (1f - v)
                }
            }
        }
        prog.snapTo(0f)
        prog.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
    }

    val dot by animateColorAsState(dotColor, tween(600), label = "dot")

    val outerDisconnected = ImageBitmap.imageResource(R.drawable.map_pin_outer_disconnected)
    val outerProtected = ImageBitmap.imageResource(R.drawable.map_pin_outer_protected)
    val animateOn = remember {
        runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }.getOrDefault(true)
    }
    var elapsedS by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pin, to, animateOn) {
        if (!animateOn) return@LaunchedEffect
        val t0 = withFrameNanos { it }
        elapsedS = 0f
        while (true) withFrameNanos { elapsedS = (it - t0) / 1_000_000_000f }
    }

    fun camera(w: Float): Cam {
        val t = prog.value.toDouble()
        val dLon = wrap(to.lon - from.lon)
        val dLat = to.lat - from.lat
        val mLon = from.lon + dLon * t
        val mLat = from.lat + dLat * t
        val dist = hypot(abs(dLon), abs(dLat)).toFloat()

        val dip = (dist / 100f).coerceAtMost(0.3f)
        val z = zoom * (1f - dip * sin(PI * t).toFloat())
        val r = w * R_DEFAULT * z
        val space = smooth((1.15f * w - r) / (0.6f * w))
        return Cam(mLon + offLon, (mLat + offLat).coerceIn(-85.0, 85.0), mLon, mLat, r, space)
    }

    val stars = remember {
        val rnd = Random(11)
        List(70) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.5f + rnd.nextFloat() * 1.1f, 0.25f + rnd.nextFloat() * 0.55f) }
    }
    val pool = remember { ArrayList<Path>() }
    val drawn = remember { ArrayList<Path>() }
    val scratch = remember { Scratch() }

    Box(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gz, _ ->
                    job[0]?.cancel()
                    val r = (size.width * R_DEFAULT * zoom).toDouble()
                    val lat = (to.lat + offLat).coerceIn(-85.0, 85.0)
                    val cosLat = max(0.15, cos(Math.toRadians(lat)))

                    offLon = wrap(offLon - Math.toDegrees(pan.x / (r * cosLat))).toFloat()
                    offLat = (offLat + Math.toDegrees(pan.y / r)).toFloat()
                        .coerceIn((-85.0 - to.lat).toFloat(), (85.0 - to.lat).toFloat())
                    zoom = (zoom * gz).coerceIn(ZOOM_MIN, ZOOM_MAX)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    job[0]?.cancel()
                    job[0] = scope.launch {
                        val z0 = zoom; val l0 = offLon; val a0 = offLat
                        animate(0f, 1f, animationSpec = tween(600, easing = FastOutSlowInEasing)) { v, _ ->
                            zoom = z0 + (1f - z0) * v; offLon = l0 * (1f - v); offLat = a0 * (1f - v)
                        }
                    }
                })
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cam = camera(size.width)
            val cx = size.width / 2f
            val cy = size.height * centerY
            val r = cam.r
            val space = cam.space
            val centre = Offset(cx, cy)

            if (space > 0f) {
                for (s in stars) drawCircle(Color.White.copy(alpha = s[3] * space), s[2].dp.toPx(), Offset(s[0] * size.width, s[1] * size.height))

                val outer = r * 1.2f
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Transparent, (r / outer) to dot.copy(alpha = 0.32f * space), 1f to Color.Transparent,
                        center = centre, radius = outer,
                    ),
                    outer, centre,
                )
                drawCircle(lerp(C.Bg, C.Card, 0.9f).copy(alpha = space), r, centre)
            }

            val t = prog.value
            val fadeOut = (1f - t * 2f).coerceIn(0f, 1f)
            val fadeIn = ((t - 0.5f) * 2f).coerceIn(0f, 1f)
            fun lit(code: String): Float = when {
                code == "--" -> 0f
                code == newCode && code == oldCode -> 1f
                code == newCode -> fadeIn
                code == oldCode -> fadeOut
                else -> 0f
            }

            val list = globe
            if (list != null) {
                val lonR = Math.toRadians(cam.vLon); val latR = Math.toRadians(cam.vLat)
                val cl = cos(lonR).toFloat(); val sl = sin(lonR).toFloat()
                val cp = cos(latR).toFloat(); val sp = sin(latR).toFloat()
                val vx = cp * cl; val vy = cp * sl; val vz = sp

                val maxD = hypot(max(cx, size.width - cx), max(cy, size.height - cy))
                val visAng = if (maxD >= r) (Math.PI / 2).toFloat() else asin(maxD / r)
                val ppd = r * (Math.PI / 180.0).toFloat()
                val step = when { ppd < 3f -> 4; ppd < 6f -> 3; ppd < 12f -> 2; else -> 1 }

                drawn.clear()
                var used = 0
                for (gc in list) {
                    val path = if (used < pool.size) pool[used] else Path().also { pool += it }
                    path.rewind()
                    path.fillType = PathFillType.EvenOdd
                    val l = lit(gc.code)
                    var any = false
                    for (ring in gc.rings) {
                        if (ring.radius * r < 0.35f && l == 0f) continue
                        val cosT = (ring.cx * vx + ring.cy * vy + ring.cz * vz).coerceIn(-1f, 1f)
                        if (acos(cosT) - ring.radius > visAng) continue
                        if (addRing(path, ring, step, cl, sl, cp, sp, cx, cy, r, scratch)) any = true
                    }
                    if (any) {
                        used++
                        drawn += path
                        drawPath(path, lerp(C.Land, C.LandActive, l))
                    }
                }

                val line = Stroke(0.8.dp.toPx(), join = StrokeJoin.Round)
                for (p in drawn) drawPath(p, C.LandLine, style = line)
            }

            if (space > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Transparent, 0.65f to Color.Transparent, 1f to C.Bg.copy(alpha = 0.55f * space),
                        center = centre, radius = r,
                    ),
                    r, centre,
                )
                drawCircle(dot.copy(alpha = 0.45f * space), r, centre, style = Stroke(1.5.dp.toPx()))
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val cam = camera(size.width)
            val lonR = Math.toRadians(cam.vLon); val latR = Math.toRadians(cam.vLat)
            val mLon = Math.toRadians(cam.mLon); val mLat = Math.toRadians(cam.mLat.coerceIn(-85.0, 85.0))
            val x = cos(mLat) * cos(mLon); val y = cos(mLat) * sin(mLon); val z = sin(mLat)
            val a = x * cos(lonR) + y * sin(lonR)
            val east = -x * sin(lonR) + y * cos(lonR)
            val depth = a * cos(latR) + z * sin(latR)
            val north = -a * sin(latR) + z * cos(latR)
            if (depth <= 0.02) return@Canvas
            val c = Offset(size.width / 2f + cam.r * east.toFloat(), size.height * centerY - cam.r * north.toFloat())
            val animS = elapsedS
            val showAnim = animateOn
            val fullR = 48.dp.toPx(); val smallR = 32.dp.toPx(); val innerR = 12.dp.toPx()
            fun decel(x: Float): Float { val t = x.coerceIn(0f, 1f); return 1f - (1f - t) * (1f - t) * (1f - t) }

            val outerBmp = when (pin) { MapPin.Selected -> outerDisconnected; MapPin.Connected -> outerProtected; MapPin.Connecting -> null }
            if (outerBmp != null) {
                val outerR: Float? = if (!showAnim) fullR else {
                    val delay = if (pin == MapPin.Connected) 0f else 0.7f
                    if (animS < delay) null else {
                        val st = animS - delay
                        if (st < 1f) fullR * decel(st)
                        else {
                            val ps = (st - 1f) % 3f
                            val half = 1.5f
                            val f = if (ps > half) decel((ps - half) / half) else 1f - decel(ps / half)
                            smallR + (fullR - smallR) * f
                        }
                    }
                }
                if (outerR != null && outerR > 0f) {
                    drawImage(
                        outerBmp,
                        srcSize = IntSize(outerBmp.width, outerBmp.height),
                        dstOffset = IntOffset((c.x - outerR).roundToInt(), (c.y - outerR).roundToInt()),
                        dstSize = IntSize((outerR * 2).roundToInt(), (outerR * 2).roundToInt()),
                    )
                }
            }
            val r0: Float? = if (!showAnim || pin == MapPin.Connected) innerR else {
                if (animS < 0.2f) null else {
                    val st = animS - 0.2f
                    if (st < 0.4f) innerR * decel(st / 0.4f) else innerR
                }
            }
            if (r0 != null && r0 > 0f) {
                drawCircle(Color.White, r0, c)
                drawCircle(dot, r0 / 2f, c)
            }
        }
    }
}
