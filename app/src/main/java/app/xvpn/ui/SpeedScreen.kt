package app.xvpn.ui

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.SpeedConfig
import app.xvpn.data.SpeedPhase
import app.xvpn.data.SpeedResult
import app.xvpn.data.SpeedTest
import app.xvpn.data.SpeedUi
import app.xvpn.data.TransmitMode
import app.xvpn.data.running
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Colors sampled from the design video.
private val DownColor = Color(0xFFEE6B3B)   // orange
private val UpColor = Color(0xFF7C5CFF)     // purple
private val PinkTip = Color(0xFFC85AA0)
private val BlueTip = Color(0xFF2F7BF0)
private val AmberTip = Color(0xFFF0B04A)
private val GreenTip = Color(0xFF3DDC97)
private val Dim = Color(0xFF6C6B78)         // label of a tile that isn't being measured
private val Mono = FontFamily.Monospace

private enum class SpeedView { Start, Running, Done }

@Composable
fun SpeedScreen(serverName: String?, onPickServer: () -> Unit = {}, onFullScreenChange: (Boolean) -> Unit = {}) {
    val ui by SpeedTest.state.collectAsState()
    val cfg by SpeedTest.config.collectAsState()
    val status by VpnState.status.collectAsState()
    val socks by VpnState.socksPort.collectAsState()
    val ctx = LocalContext.current
    val vpnReady = status == Status.Connected && socks > 0
    val viaVpn = vpnReady && cfg.useVpn
    val connecting = status == Status.Connecting
    val connection = if (viaVpn) "VPN" + (serverName?.let { " · $it" } ?: "") else "Direct (VPN off)"
    var mode by remember { mutableIntStateOf(0) } // 0 = gauge / chart, 1 = log
    LaunchedEffect(Unit) { SpeedTest.attach(ctx) }

    val view = when {
        ui.phase.running -> SpeedView.Running
        ui.phase == SpeedPhase.Done -> SpeedView.Done
        else -> SpeedView.Start
    }
    // Running and finished tests take over the screen like in the design: no bottom bar.
    LaunchedEffect(view) { onFullScreenChange(view != SpeedView.Start) }
    DisposableEffect(Unit) { onDispose { onFullScreenChange(false) } }
    BackHandler(enabled = view != SpeedView.Start) { if (view == SpeedView.Running) SpeedTest.stop() else SpeedTest.reset() }

    fun share() {
        val text = "Outrun VPN speed test (${if (ui.viaVpn) "through VPN" else "direct"})\n" +
            "Download ${mbps(ui.downMbps)} Mbps, Upload ${mbps(ui.upMbps)} Mbps, Ping ${ms(ui.pingMs)} ms, Jitter ${ms(ui.jitterMs)} ms"
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { ctx.startActivity(Intent.createChooser(send, "Share result").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(ctx, "Couldn't share result", Toast.LENGTH_SHORT).show() }
    }

    // Like the video: the old screen dims and shrinks away first, then the new one fades in and its parts arrive one after another.
    AnimatedContent(
        targetState = view,
        transitionSpec = {
            ContentTransform(
                fadeIn(tween(340, delayMillis = 180, easing = LinearOutSlowInEasing)) +
                    scaleIn(tween(420, delayMillis = 180, easing = Motion.Standard), initialScale = 0.97f),
                fadeOut(tween(180, easing = FastOutLinearInEasing)) + scaleOut(tween(240, easing = Motion.Standard), targetScale = 0.92f),
                0f,
                SizeTransform(clip = false),
            )
        },
        modifier = Modifier.fillMaxSize(),
        label = "speedView",
    ) { v ->
        when (v) {
            SpeedView.Start -> StartView(
                ui = ui, cfg = cfg, connecting = connecting, vpnReady = vpnReady, serverName = serverName,
                onCfg = { SpeedTest.config.value = it }, onPickServer = onPickServer,
                onStart = { SpeedTest.start(if (viaVpn) socks else 0) },
            )
            SpeedView.Running -> RunningView(ui, mode, { mode = it }, connection)
            SpeedView.Done -> DoneView(ui, mode, { mode = it }, connection, onShare = ::share)
        }
    }
}

/** Fades a piece of a screen in and lifts it up; [index] staggers the pieces so they arrive one after another. */
@Composable
private fun Modifier.staggerIn(index: Int, rise: Dp = 18.dp): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(420, delayMillis = 220 + index * 70, easing = Motion.Standard)) }
    return this.graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * rise.toPx()
    }
}

// ---------------------------------------------------------------- glow helpers

/** A soft round haze of [color]: strongest at the middle, fading smoothly to nothing. Several of them side by side make smoke. */
private fun DrawScope.haze(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.4f to color.copy(alpha = alpha * 0.4f),
            1f to color.copy(alpha = 0f),
            center = center, radius = radius,
        ),
        radius = radius, center = center,
    )
}

/** Color of a gradient described by [stops] (position 0..1 to color) at position [f]. */
private fun colorAt(stops: Array<Pair<Float, Color>>, f: Float): Color {
    for (i in 0 until stops.size - 1) {
        val (a, ca) = stops[i]
        val (b, cb) = stops[i + 1]
        if (f in a..b) return if (b > a) lerp(ca, cb, (f - a) / (b - a)) else cb
    }
    return stops.last().second
}

// ---------------------------------------------------------------- start

@Composable
private fun StartView(
    ui: SpeedUi, cfg: SpeedConfig, connecting: Boolean, vpnReady: Boolean, serverName: String?,
    onCfg: (SpeedConfig) -> Unit, onPickServer: () -> Unit, onStart: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (more) 180f else 0f, tween(Motion.Medium, easing = Motion.Standard), label = "moreChevron")
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        HeaderRow { Text("Speed test", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        Spacer(Modifier.height(4.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(30.dp)) // headroom so the ring's haze isn't cut off by the scroll area
            Box(Modifier.align(Alignment.CenterHorizontally)) { StartRing(enabled = !connecting, onClick = onStart) }
            val note = when {
                ui.phase == SpeedPhase.Failed -> "Couldn't reach the test server. Check your connection and try again." to C.Red
                connecting -> "Wait for the VPN to finish connecting." to C.Muted
                else -> null
            }
            if (note != null) {
                Text(note.first, color = note.second, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }

            SettingLabel("Type")
            Segmented(
                options = listOf("VPN", "Direct"),
                selected = if (vpnReady && cfg.useVpn) 0 else 1,
                enabled = listOf(vpnReady, true),
            ) { onCfg(cfg.copy(useVpn = it == 0)) }

            SettingLabel("Select server")
            Row(
                Modifier.fillMaxWidth().height(48.dp).pressable(RoundedCornerShape(12.dp), 0.98f, onClick = onPickServer).background(C.Card).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(serverName ?: "No server selected", color = C.Muted, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Choose server", tint = C.Muted, modifier = Modifier.size(22.dp))
            }

            SettingLabel("Transmit Mode")
            ModePicker(cfg.mode) { onCfg(cfg.copy(mode = it)) }

            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp).pressable(RoundedCornerShape(10.dp), 0.95f) { more = !more }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("More", color = C.Muted, fontSize = 13.sp)
                Icon(Icons.Filled.KeyboardArrowDown, null, tint = C.Muted, modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = chevron })
            }
            AnimatedVisibility(
                visible = more,
                enter = expandVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeIn(tween(Motion.Medium)),
                exit = shrinkVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeOut(tween(Motion.Short)),
            ) {
                Column {
                    SettingLabel("Test duration")
                    val secs = listOf(5, 7, 10)
                    Segmented(secs.map { "${it}s" }, secs.indexOf(cfg.seconds).coerceAtLeast(0)) { onCfg(cfg.copy(seconds = secs[it])) }
                    SettingLabel("Streams")
                    val streams = listOf(1, 2, 4, 8)
                    Segmented(streams.map { "$it" }, streams.indexOf(cfg.streams).coerceAtLeast(0)) { onCfg(cfg.copy(streams = streams[it])) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingLabel(text: String) {
    Text(text, color = C.Text, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

/** Text segments in a dark tray; the chosen one is a white pill, as in the design. */
@Composable
private fun Segmented(options: List<String>, selected: Int, enabled: List<Boolean> = options.map { true }, onSelect: (Int) -> Unit) {
    val tick = rememberTick()
    Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(C.Card)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) Color.White else Color.Transparent, tween(Motion.Short + 50), label = "segBg")
            val fg by animateColorAsState(if (on) Color.Black else if (enabled[i]) C.Text else Dim, tween(Motion.Short + 50), label = "segFg")
            Box(
                Modifier.weight(1f).fillMaxHeight().padding(2.dp)
                    .pressable(RoundedCornerShape(10.dp), 0.96f, enabled = enabled[i]) { tick(); onSelect(i) }
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = fg, fontSize = 14.sp) }
        }
    }
}

private val ModeItems = listOf(
    TransmitMode.Download to "Download",
    TransmitMode.Upload to "Upload",
    TransmitMode.Bidirectional to "Bidirectional",
    TransmitMode.Alternate to "Alternate",
)

/** Four icon tabs with their names underneath: Download, Upload, Bidirectional, Alternate. */
@Composable
private fun ModePicker(selected: TransmitMode, onSelect: (TransmitMode) -> Unit) {
    val tick = rememberTick()
    Column {
        Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(C.Card)) {
            ModeItems.forEach { (m, _) ->
                val on = m == selected
                val bg by animateColorAsState(if (on) Color.White else Color.Transparent, tween(Motion.Short + 50), label = "modeBg")
                val fg by animateColorAsState(if (on) Color.Black else C.Text, tween(Motion.Short + 50), label = "modeFg")
                val icon = when (m) {
                    TransmitMode.Download -> Icons.Outlined.ArrowDownward
                    TransmitMode.Upload -> Icons.Outlined.ArrowUpward
                    TransmitMode.Bidirectional -> Icons.Outlined.SwapVert
                    TransmitMode.Alternate -> Icons.Outlined.SyncAlt
                }
                Box(
                    Modifier.weight(1f).fillMaxHeight().padding(2.dp)
                        .pressable(RoundedCornerShape(10.dp), 0.96f) { tick(); onSelect(m) }
                        .background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon, m.name, tint = fg,
                        modifier = Modifier.size(22.dp).graphicsLayer { if (m == TransmitMode.Alternate) rotationZ = 90f },
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            ModeItems.forEach { (m, name) ->
                val c by animateColorAsState(if (m == selected) C.Text else C.Muted, tween(Motion.Short + 50), label = "modeLabel")
                Text(name, color = c, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

// Ring colors sampled every 20 degrees from the design, clockwise from 3 o'clock (green, purple, blue, teal, purple, orange, blue, tan, pink, purple, orange, olive).
private val RingHex = longArrayOf(
    0x3b9272, 0x56806c, 0x714980, 0x094faf, 0x0a57a2, 0x256b63, 0x496593, 0x8957b5, 0xf56932,
    0xdb643e, 0x0962cf, 0x0961cd, 0xaa866f, 0x975a6e, 0x9a59a7, 0xf96b33, 0xee662f, 0x7e714d,
)

private val RingStops: Array<Pair<Float, Color>> = run {
    fun boost(c: Color): Color {   // a little more saturated than the sampled pixels, which are softened by the image's compression
        val g = (c.red + c.green + c.blue) / 3f
        fun ch(v: Float) = (g + (v - g) * 1.25f).coerceIn(0f, 1f)
        return Color(ch(c.red), ch(c.green), ch(c.blue))
    }
    val cols = RingHex.map { boost(Color(0xFF000000L or it)) }
    Array(cols.size + 1) { i -> (i / cols.size.toFloat()) to cols[i % cols.size] }
}

private const val RING_BOX = 250       // dp: the size of the button area; the smoke bitmap uses 1 pixel per dp and is scaled up smoothly
private const val RING_R = 86.5f       // dp, middle of the ring stroke
private const val RING_W = 7.5f        // dp, stroke width

/**
 * The light scattered by the ring, as a soft cloud of colored smoke: for every pixel the strength falls off smoothly with the distance
 * from the ring, the color is the ring's color at that angle (blurred a little along the ring and drifting as it moves away), and the
 * strength varies gently around the circle so it reads as smoke and not as a uniform outline.
 */
private fun buildSmoke(): Bitmap {
    val n = RING_BOX
    val c = n / 2f
    val outer = RING_R + RING_W / 2f
    val inner = RING_R - RING_W / 2f
    val peak = 0.20f
    // The colors of the ring at full brightness; one lookup per degree keeps the per-pixel work small.
    val lut = Array(360) { deg ->
        val col = colorAt(RingStops, deg / 360f)
        val m = maxOf(col.red, col.green, col.blue, 0.001f)
        floatArrayOf(col.red / m, col.green / m, col.blue / m)
    }
    val rnd = java.util.Random(1)
    val px = IntArray(n * n)
    for (y in 0 until n) for (x in 0 until n) {
        val dx = x + 0.5f - c
        val dy = y + 0.5f - c
        val r = sqrt(dx * dx + dy * dy)
        val d = r - outer
        var a = when {
            d >= 0f -> peak * exp(-(d / 16f).pow(1.4f)) + 0.07f * exp(-(d / 34f).pow(1.5f))
            r >= inner -> peak
            else -> peak * 0.6f * exp(-(inner - r) / 5f)
        }
        if (a < 0.002f) continue
        var deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        if (deg < 0f) deg += 360f
        val rad = Math.toRadians(deg.toDouble())
        a *= 1f + 0.18f * sin(2 * rad + 0.6).toFloat() + 0.12f * sin(3 * rad + 2.0).toFloat()
        val drift = 10f * (maxOf(d, 0f) / 16f).coerceAtMost(2f)
        var rr = 0f; var gg = 0f; var bb = 0f
        for ((off, w) in SmokeBlur) {
            val k = (((deg + drift + off).toInt() % 360) + 360) % 360
            rr += lut[k][0] * w; gg += lut[k][1] * w; bb += lut[k][2] * w
        }
        val alpha = (a.coerceIn(0f, 1f) * 255f + rnd.nextFloat() - 0.5f).roundToInt().coerceIn(0, 255)   // tiny dither avoids banding
        px[y * n + x] = (alpha shl 24) or ((rr * 255f).roundToInt().coerceIn(0, 255) shl 16) or
            ((gg * 255f).roundToInt().coerceIn(0, 255) shl 8) or (bb * 255f).roundToInt().coerceIn(0, 255)
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
}

private val SmokeBlur = listOf(-14f to 0.1f, -7f to 0.2f, 0f to 0.4f, 7f to 0.2f, 14f to 0.1f)

/** The START button from the design: a multicolor ring with light scattering through colored smoke around it, drawn entirely in code. */
@Composable
private fun StartRing(enabled: Boolean, onClick: () -> Unit) {
    val on by animateFloatAsState(if (enabled) 1f else 0.35f, tween(Motion.Medium), label = "ringOn")
    val tick = rememberTick(strong = true)
    val smoke = remember { buildSmoke().asImageBitmap() }
    Box(Modifier.size(RING_BOX.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawImage(
                smoke, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                alpha = on, filterQuality = FilterQuality.High,
            )
            val brush = Brush.sweepGradient(*RingStops, center = center)
            val r = RING_R.dp.toPx()
            // A faint wider pass softens the ring's edges like in the design.
            drawCircle(brush = brush, radius = r, alpha = 0.25f * on, style = Stroke((RING_W + 3f).dp.toPx()))
            drawCircle(brush = brush, radius = r, alpha = on, style = Stroke(RING_W.dp.toPx()))
        }
        Box(
            Modifier.size(170.dp).pressable(CircleShape, 0.94f, enabled = enabled, ripple = false) { tick(); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text("START", color = if (enabled) Color.White else Dim, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- running

@Composable
private fun RunningView(ui: SpeedUi, mode: Int, onMode: (Int) -> Unit, connection: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val cfg = ui.cfg
    val bidir = cfg.mode == TransmitMode.Bidirectional
    // Like the design: the phase length ("07s") and how far into the current phase we are ("00:03s").
    val secs = if (ui.phase == SpeedPhase.Ping) 0L
    else ((now - ui.phaseStartedAt) / 1000).coerceIn(0, cfg.seconds.toLong())
    val live = if (ui.phase == SpeedPhase.Download || ui.phase == SpeedPhase.Upload) ui.live else 0.0
    val series = if (ui.phase == SpeedPhase.Upload) ui.upSeries else ui.downSeries
    val dialMax = dialMax(maxOf(live, series.maxOrNull()?.toDouble() ?: 0.0))

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(20.dp))
            TitleBar(
                left = { HeaderIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back") { SpeedTest.stop() } },
                right = {
                    Text(
                        "Stop", color = C.Muted, fontSize = 17.sp,
                        modifier = Modifier.clip(RoundedCornerShape(Radius.Small)).clickable { SpeedTest.stop() }.padding(horizontal = 8.dp, vertical = 10.dp),
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).staggerIn(0), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("%02ds".format(cfg.seconds), color = C.Muted, fontSize = 13.sp, fontFamily = Mono)
                    Spacer(Modifier.height(4.dp))
                    Text("%02d:%02ds".format(secs / 60, secs % 60), color = C.Text, fontSize = 13.sp, fontFamily = Mono)
                }
                ModeToggle(mode, onMode)
            }
            // The gauge and tiles stay put (so the gauge's glow is never clipped); only the info rows scroll.
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().staggerIn(1)) {
                    Crossfade(mode, animationSpec = tween(Motion.Medium), label = "speedMode") { m ->
                        if (m == 0) SpeedDial(live, dialMax, upload = ui.phase == SpeedPhase.Upload) else LogCard(ui.log)
                    }
                }
                Spacer(Modifier.height(8.dp))
                StatGrid(ui, highlightDown = ui.phase == SpeedPhase.Download, highlightUp = ui.phase == SpeedPhase.Upload || (bidir && ui.phase == SpeedPhase.Download), modifier = Modifier.staggerIn(2))
                Spacer(Modifier.height(8.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).staggerIn(3)) {
                    InfoRows(connection, cfg)
                    Spacer(Modifier.height(SheetClearance))
                }
            }
        }
        HistorySheet(Modifier.align(Alignment.BottomCenter).staggerIn(4, rise = 90.dp))
    }
}

private val SheetClearance = 76.dp

// The needle sits at DIAL_START degrees at 0 Mbps and sweeps DIAL_SWEEP degrees to the full scale (clockwise from 3 o'clock),
// so the first label (1/10 of the scale) lands exactly on the left horizontal and the last one on the right, as in the design.
private const val DIAL_START = 160f
private const val DIAL_SWEEP = 200f
private val DialSteps = doubleArrayOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0)

/** Full-scale value of the dial: the smallest step that still leaves headroom above [peak]. 1000 gives the 100..1000 scale of the design. */
private fun dialMax(peak: Double): Double = DialSteps.firstOrNull { peak <= it * 0.92 } ?: DialSteps.last()

/** Gradient colors are tied to the position on the dial, so the arc keeps its colors as it grows. */
private fun dialStops(upload: Boolean): Array<Pair<Float, Color>> {
    fun at(f: Float) = (DIAL_START + DIAL_SWEEP * f) / 360f
    return if (upload) arrayOf(
        0f to UpColor, at(0.28f) to UpColor, at(0.48f) to AmberTip, at(0.68f) to GreenTip, 1f to GreenTip,
    ) else arrayOf(
        0f to DownColor, at(0.12f) to DownColor, at(0.33f) to PinkTip, at(0.58f) to BlueTip, 1f to BlueTip,
    )
}

/** Half-circle gauge like the design: a gradient arc inside a cloud of colored haze, ten numbers, a thin needle. No tick marks. */
@Composable
private fun SpeedDial(value: Double, max: Double, upload: Boolean, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState((value / max).toFloat().coerceIn(0f, 1f), tween(320, easing = Motion.Standard), label = "dial")
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { TextStyle(color = Color(0xFFC4C3CB), fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp) }
    Canvas(modifier.fillMaxWidth().aspectRatio(2f)) {
        val hub = Offset(size.width / 2, size.height - 30.dp.toPx())
        val r = size.width * 0.375f
        val topLeft = Offset(hub.x - r, hub.y - r)
        val arc = Size(2 * r, 2 * r)
        val stops = dialStops(upload)
        val brush = Brush.sweepGradient(*stops, center = hub)
        fun onArc(deg: Float, rad: Float): Offset {
            val a = Math.toRadians(deg.toDouble())
            return Offset(hub.x + (cos(a) * rad).toFloat(), hub.y + (sin(a) * rad).toFloat())
        }

        val sweep = DIAL_START + DIAL_SWEEP * frac - 180f
        if (sweep > 0.5f) {
            // Smoke: soft hazes along the filled arc in the arc's own colors, with a bigger bloom at the tip.
            val n = (sweep / 7f).toInt() + 1
            for (k in 0..n) {
                val deg = 180f + minOf(sweep, k * 7f)
                val tip = k == n
                haze(onArc(deg, r), (if (tip) 100 else 60).dp.toPx(), colorAt(stops, deg / 360f), if (tip) 0.28f else 0.05f)
            }
        }

        drawArc(color = Color(0xFF2A2933), startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(1.dp.toPx()))
        if (sweep > 0.5f) {
            drawArc(brush = brush, startAngle = 180f, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(3.5f.dp.toPx(), cap = StrokeCap.Round))
        }

        val lr = r * 0.76f
        for (i in 1..10) {
            val layout = measurer.measure((max * i / 10).toInt().toString(), labelStyle)
            val p = onArc(DIAL_START + DIAL_SWEEP * i / 10f, lr)
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
        }

        val tipPoint = onArc(DIAL_START + DIAL_SWEEP * frac, r * 0.6f)
        drawLine(color = Color.White, start = hub, end = tipPoint, strokeWidth = 1.5f.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.White, 3.5f.dp.toPx(), hub)
    }
}

// ---------------------------------------------------------------- done

@Composable
private fun rememberCountUp(target: Double): Double {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { if (target >= 0) a.animateTo(target.toFloat(), tween(1000, delayMillis = 320, easing = Motion.Standard)) }
    return if (target < 0) target else a.value.toDouble()
}

@Composable
private fun DoneView(ui: SpeedUi, mode: Int, onMode: (Int) -> Unit, connection: String, onShare: () -> Unit) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(1200, delayMillis = 380, easing = Motion.Standard)) }
    val downShown = rememberCountUp(ui.downMbps)
    val upShown = rememberCountUp(ui.upMbps)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(20.dp))
            TitleBar(
                left = { HeaderIcon(Icons.Outlined.IosShare, "Share result", onClick = onShare) },
                right = { HeaderIcon(Icons.Filled.Close, "Close") { SpeedTest.reset() } },
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) { ModeToggle(mode, onMode) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(12.dp))
                if (mode == 0) {
                    Column(Modifier.fillMaxWidth().staggerIn(0).clip(RoundedCornerShape(Radius.Card)).background(C.Card)) {
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                            BigStat("Download", DownColor, mbps(downShown), "Mbps", Modifier.weight(1f))
                            BigStat("Upload", UpColor, mbps(upShown), "Mbps", Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp)) {
                            BigStat("Ping", Dim, ms(ui.pingMs), "ms", Modifier.weight(1f))
                            BigStat("Jitter", Dim, ms(ui.jitterMs), "ms", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(16.dp))
                        SpeedChart(ui.downSeries, ui.upSeries, reveal.value, Modifier.fillMaxWidth().height(96.dp))
                    }
                } else {
                    LogCard(ui.log)
                    Spacer(Modifier.height(10.dp))
                    StatGrid(ui, highlightDown = true, highlightUp = true)
                }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.staggerIn(1)) { InfoRows(connection, ui.cfg) }
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.align(Alignment.CenterHorizontally).staggerIn(2).width(176.dp)
                        .pressable(RoundedCornerShape(26.dp), 0.96f) { SpeedTest.start(if (ui.viaVpn) VpnState.socksPort.value else 0) }
                        .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFD8D8DE))))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Test Again", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.height(SheetClearance + 8.dp))
            }
        }
        HistorySheet(Modifier.align(Alignment.BottomCenter).staggerIn(3, rise = 90.dp))
    }
}

/** The "Client history" sheet that peeks in from the bottom; tap it to open the list of earlier tests. */
@Composable
private fun HistorySheet(modifier: Modifier = Modifier) {
    val items by SpeedTest.history.collectAsState()
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(C.Card).border(1.dp, C.CardBorder.copy(alpha = 0.5f), shape)
            .animateContentSize(tween(Motion.Medium, easing = Motion.Standard)),
    ) {
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(top = 8.dp, bottom = 16.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(C.ButtonGrey))
            Spacer(Modifier.height(10.dp))
            Text("Client history", color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (open) {
            val fmt = remember { SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()) }
            Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
                if (items.isEmpty()) {
                    Text("No tests yet.", color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    items.forEach { HistoryRow(it, fmt.format(Date(it.at))) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(r: SpeedResult, whenText: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(whenText, color = C.Text, fontSize = 14.sp)
            Text(if (r.viaVpn) "VPN" else "Direct", color = C.Muted, fontSize = 12.sp)
        }
        Text(
            "${mbps(r.downMbps)} \u2193  ${mbps(r.upMbps)} \u2191  ${ms(r.pingMs)} ms",
            color = C.Text, fontSize = 13.sp, fontFamily = Mono, textAlign = TextAlign.End,
        )
    }
}

// ---------------------------------------------------------------- shared pieces

/** Header with a centered title, an action on each side and no tab-style left alignment. */
@Composable
private fun TitleBar(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { left() }
        Text("Speed test", color = C.Text, fontSize = 17.sp, modifier = Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.CenterEnd)) { right() }
    }
}

/** Gauge / log switch. */
@Composable
private fun ModeToggle(mode: Int, onMode: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Card).padding(2.dp)) {
        listOf(Icons.Outlined.Speed, Icons.Outlined.Terminal).forEachIndexed { i, icon ->
            val on = i == mode
            val bg by animateColorAsState(if (on) C.ButtonGrey else Color.Transparent, tween(Motion.Short + 50), label = "toggleBg")
            val tint by animateColorAsState(if (on) C.Text else C.Muted, tween(Motion.Short + 50), label = "toggleTint")
            Box(
                Modifier.size(width = 44.dp, height = 32.dp).clip(RoundedCornerShape(8.dp)).background(bg).clickable { onMode(i) },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, if (i == 0) "Gauge" else "Log", tint = tint, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Download / Upload on top, Ping / Jitter below, as tightly packed tiles. The tile being measured gets a colored label, the others are dimmed. */
@Composable
private fun StatGrid(ui: SpeedUi, highlightDown: Boolean, highlightUp: Boolean, modifier: Modifier = Modifier) {
    val bidirLive = ui.cfg.mode == TransmitMode.Bidirectional && ui.phase == SpeedPhase.Download
    val down = if (ui.phase == SpeedPhase.Download) ui.live else ui.downMbps
    val up = if (ui.phase == SpeedPhase.Upload) ui.live else if (bidirLive) ui.liveUp else ui.upMbps
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            StatTile("Download", if (highlightDown) DownColor else Dim, mbps(down.coerceAtLeast(0.0)), "Mbps", true, Modifier.weight(1f))
            StatTile("Upload", if (highlightUp) UpColor else Dim, mbps(up.coerceAtLeast(0.0)), "Mbps", true, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            StatTile("Ping", Dim, ms(ui.pingMs.coerceAtLeast(0)), "ms", false, Modifier.weight(1f))
            StatTile("Jitter", Dim, ms(ui.jitterMs.coerceAtLeast(0)), "ms", false, Modifier.weight(1f))
        }
    }
}

/** [bigUnit]: the unit is as large as the value ("560 Mbps"); otherwise it is a small suffix ("10 %"), like in the design. */
@Composable
private fun StatTile(label: String, labelColor: Color, value: String, unit: String, bigUnit: Boolean, modifier: Modifier = Modifier) {
    val labelShown by animateColorAsState(labelColor, tween(Motion.Medium), label = "tileLabel")
    Column(modifier.height(72.dp).clip(RoundedCornerShape(14.dp)).background(C.Card).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(label, color = labelShown, fontSize = 12.sp)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = C.Text, fontSize = 17.sp, fontFamily = Mono, maxLines = 1)
                Spacer(Modifier.width(if (bigUnit) 6.dp else 4.dp))
                Text(
                    unit, color = C.Text, fontSize = if (bigUnit) 17.sp else 10.sp, fontFamily = Mono,
                    modifier = if (bigUnit) Modifier else Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun BigStat(label: String, labelColor: Color, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = labelColor, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = C.Text, fontSize = 20.sp, fontFamily = Mono, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = C.Text, fontSize = 11.sp, fontFamily = Mono, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
private fun LogCard(log: List<String>) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 170.dp, max = 260.dp).clip(RoundedCornerShape(Radius.Card)).background(C.Card)
            .verticalScroll(rememberScrollState()).padding(14.dp),
    ) {
        Text(
            if (log.isEmpty()) "No test run yet." else log.joinToString("\n"),
            style = TextStyle(fontSize = 12.sp, fontFamily = Mono, lineHeight = 18.sp),
            color = if (log.isEmpty()) C.Muted else C.Text,
        )
    }
}

/** Smooth area chart of the live speed samples of both phases. [progress] reveals it from left to right. */
@Composable
private fun SpeedChart(down: List<Float>, up: List<Float>, progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val peak = maxOf(1f, down.maxOrNull() ?: 0f, up.maxOrNull() ?: 0f) * 1.15f
        fun line(series: List<Float>, color: Color) {
            if (series.size < 2) return
            val dx = size.width / (series.size - 1)
            val pts = series.mapIndexed { i, v -> Offset(i * dx, size.height - (v / peak) * size.height) }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    val p0 = pts[i - 1]; val p1 = pts[i]
                    val mx = (p0.x + p1.x) / 2
                    cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                }
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(pts.last().x, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
        clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
            line(down, DownColor)
            line(up, UpColor)
        }
    }
}

@Composable
private fun InfoRows(connection: String, cfg: SpeedConfig) {
    InfoRow("Server", "Cloudflare")
    InfoRow("Connection", connection)
    InfoRow("Transmit Mode", cfg.mode.name)
    InfoRow("Test duration", "${cfg.seconds}s")
    InfoRow("Streams", "${cfg.streams}")
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = C.Muted, fontSize = 13.sp)
        Spacer(Modifier.width(16.dp))
        Text(value, color = C.Text, fontSize = 13.sp, fontFamily = Mono, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private fun mbps(v: Double): String = when {
    v < 0 -> "-"
    v < 10 -> "%.1f".format(v)
    else -> "%.0f".format(v)
}

private fun ms(v: Int): String = if (v < 0) "-" else v.toString()
