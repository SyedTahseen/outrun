package app.xvpn.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import app.xvpn.data.SpeedTest
import app.xvpn.data.SpeedUi
import app.xvpn.data.TransmitMode
import app.xvpn.data.running
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Same colors as the rest of the app: green = download, red = upload.
private val DownColor get() = C.Green
private val UpColor get() = C.Red
private val BlueTip = C.Blue
private val Dim get() = C.Muted         // label of a tile that isn't being measured

private enum class SpeedView { Start, Running, Done }

@Composable
fun SpeedScreen(serverName: String?, onPickServer: () -> Unit = {}, onFullScreenChange: (Boolean) -> Unit = {}, onScrolledChange: (Boolean) -> Unit = {}) {
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
            .onFailure { Snack.show("Couldn't share result", SnackType.Error) }
    }

    // Like the video: the old screen dims and shrinks away first, then the new one fades in and its parts arrive one after another.
    AnimatedContent(
        targetState = view,
        transitionSpec = {
            ContentTransform(
                fadeIn(tween(Motion.Medium, delayMillis = 180, easing = Motion.Enter)) +
                    scaleIn(tween(Motion.Long, delayMillis = 180, easing = Motion.Standard), initialScale = 0.97f),
                fadeOut(tween(Motion.Fast, easing = Motion.Exit)) + scaleOut(tween(Motion.Short, easing = Motion.Standard), targetScale = 0.92f),
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
                onScrolledChange = onScrolledChange,
            )
            SpeedView.Running -> RunningView(ui, mode, { mode = it }, connection)
            SpeedView.Done -> DoneView(ui, mode, { mode = it }, connection, onShare = ::share)
        }
    }
}

/** Fades a piece of a screen in and lifts it up; [index] staggers the pieces so they arrive one after another. */
@Composable
private fun Modifier.staggerIn(index: Int, rise: Dp = Space.Xl): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, tween(Motion.Long, delayMillis = 220 + index * Motion.Stagger, easing = Motion.Standard)) }
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
    onCfg: (SpeedConfig) -> Unit, onPickServer: () -> Unit, onStart: () -> Unit, onScrolledChange: (Boolean) -> Unit,
) {
    val scroll = rememberScrollState()
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    ReportScrolled(scrolled, onScrolledChange)
    var more by remember { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (more) 180f else 0f, tween(Motion.Medium, easing = Motion.Standard), label = "moreChevron")
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Speed test", scrolled = scrolled)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = Space.Gutter)) {
            Spacer(Modifier.height(Space.Xxxl)) // headroom so the ring's haze isn't cut off by the scroll area
            Box(Modifier.align(Alignment.CenterHorizontally)) { StartRing(enabled = !connecting, onClick = onStart) }
            val note = when {
                ui.phase == SpeedPhase.Failed -> ("Couldn't finish the test" + (ui.error.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: "") + ". Check your connection and try again.") to C.Red
                connecting -> "Wait for the VPN to finish connecting." to C.Muted
                else -> null
            }
            if (note != null) {
                Text(note.first, style = Type.Small, color = note.second, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = Space.Sm))
            }

            FieldLabel("Type")
            Segmented(
                options = listOf("VPN", "Direct"),
                selected = if (vpnReady && cfg.useVpn) 0 else 1,
                enabled = listOf(vpnReady, true),
            ) { onCfg(cfg.copy(useVpn = it == 0)) }

            FieldLabel("Select server")
            AppCard(Modifier.fillMaxWidth().height(Sizes.Button), padding = 0.dp, onClick = onPickServer) {
                Row(Modifier.fillMaxSize().padding(horizontal = Space.Lg), verticalAlignment = Alignment.CenterVertically) {
                    Text(serverName ?: "No server selected", style = Type.BodyMedium, color = if (serverName != null) C.Text else C.Muted, maxLines = 1, modifier = Modifier.weight(1f))
                    Icon(ProtonIcons.ChevronRight, "Choose server", tint = C.Muted, modifier = Modifier.size(Sizes.Icon))
                }
            }

            FieldLabel("Transmit Mode")
            ModePicker(cfg.mode) { onCfg(cfg.copy(mode = it)) }

            Row(
                Modifier.align(Alignment.CenterHorizontally)
                    .heightIn(min = Sizes.Control)
                    .pressable(RoundedCornerShape(Radius.Badge), Press.Control) { more = !more } // as TextAction (ProtonTextButton)
                    .padding(horizontal = Space.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("More", style = Type.ButtonSmall, color = C.Accent)
                Icon(ProtonIcons.ChevronDown, null, tint = C.Accent, modifier = Modifier.size(Sizes.Icon).graphicsLayer { rotationZ = chevron })
            }
            AnimatedVisibility(
                visible = more,
                enter = expandVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeIn(tween(Motion.Medium)),
                exit = shrinkVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeOut(tween(Motion.Short)),
            ) {
                Column {
                    FieldLabel("Test duration")
                    val secs = listOf(5, 7, 10)
                    Segmented(secs.map { "${it}s" }, secs.indexOf(cfg.seconds).coerceAtLeast(0)) { onCfg(cfg.copy(seconds = secs[it])) }
                    FieldLabel("Streams")
                    val streams = listOf(1, 2, 4, 8)
                    Segmented(streams.map { "$it" }, streams.indexOf(cfg.streams).coerceAtLeast(0)) { onCfg(cfg.copy(streams = streams[it])) }
                }
            }
            Spacer(Modifier.height(Space.Xxl))
        }
    }
}

private val ModeItems = listOf(
    TransmitMode.Download to "Download",
    TransmitMode.Upload to "Upload",
    TransmitMode.Bidirectional to "Bidirectional",
    TransmitMode.Alternate to "Alternate",
)

/** Four icon chips with their names underneath: Download, Upload, Bidirectional, Alternate. */
@Composable
private fun ModePicker(selected: TransmitMode, onSelect: (TransmitMode) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
            ModeItems.forEach { (m, _) ->
                val icon = when (m) {
                    TransmitMode.Download -> ProtonIcons.ArrowDown
                    TransmitMode.Upload -> ProtonIcons.ArrowUp
                    TransmitMode.Bidirectional -> ProtonIcons.ArrowDownArrowUp
                    TransmitMode.Alternate -> ProtonIcons.ArrowsLeftRight
                }
                SelectableBox(m == selected, { onSelect(m) }, Modifier.weight(1f)) {
                    Icon(
                        icon, m.name, tint = C.OnPrimary,
                        modifier = Modifier.size(Sizes.Icon).graphicsLayer { if (m == TransmitMode.Alternate) rotationZ = 90f },
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.Xs))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
            ModeItems.forEach { (m, name) ->
                val c by animateColorAsState(if (m == selected) C.Text else C.Muted, tween(Motion.Short), label = "modeLabel")
                Text(name, style = Type.Caption, color = c, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
    }
}

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

private const val RING_BOX = 250       // dp: the size of the button area
private const val RING_R = 86.5f       // dp, middle of the ring stroke
private const val RING_W = 7.5f        // dp, stroke width

/** The thick gradient ring drawn once; the caller blurs this layer to get the glow. */
@Composable
private fun RingGlowLayer(modifier: Modifier, strokeDp: Float, alpha: Float) {
    Canvas(modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.sweepGradient(*RingStops, center = center),
            radius = RING_R.dp.toPx(), alpha = alpha, style = Stroke(strokeDp.dp.toPx()),
        )
    }
}

/**
 * The START button from the design. The glow is the ring itself, really blurred (RenderEffect, Android 12+): the colors of the ring
 * bleed into each other and into the dark, like light through smoke. A wide, faint blur sits under a tighter, brighter one.
 * Blur is not available before Android 12, so older phones get stacked vector rings instead.
 */
@Composable
private fun StartRing(enabled: Boolean, onClick: () -> Unit) {
    val on by animateFloatAsState(if (enabled) 1f else 0.35f, tween(Motion.Medium), label = "ringOn")
    val tick = rememberTick(strong = true)
    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(Modifier.size(RING_BOX.dp), contentAlignment = Alignment.Center) {
        if (blurOk) {
            RingGlowLayer(Modifier.blur(18.dp, BlurredEdgeTreatment.Unbounded), RING_W, 0.45f * on)
            RingGlowLayer(Modifier.blur(7.dp, BlurredEdgeTreatment.Unbounded), RING_W, 0.7f * on)
        } else {
            // Android 11 and older: no blur available, so stack faint, slightly wider copies of the ring. Still pure vector drawing.
            Canvas(Modifier.fillMaxSize()) {
                val brush = Brush.sweepGradient(*RingStops, center = center)
                val layers = 16
                for (i in layers downTo 1) {
                    val reach = 30.dp.toPx() * sqrt(i.toFloat() / layers)
                    drawCircle(
                        brush = brush, radius = RING_R.dp.toPx(), alpha = 0.04f * on,
                        style = Stroke(RING_W.dp.toPx() + 2f * reach),
                    )
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val r = RING_R.dp.toPx()
            val w = RING_W.dp.toPx()
            // Dark face: keeps the glow outside the ring, like the design.
            drawCircle(color = C.Bg, radius = r - w / 2f)
            val brush = Brush.sweepGradient(*RingStops, center = center)
            // One crisp, fully opaque pass: the ring edge stays sharp and only the glow around it is soft.
            drawCircle(brush = brush, radius = r, alpha = on, style = Stroke(w))
        }
        Box(
            Modifier.size(Sizes.SpeedRing).pressable(CircleShape, 0.94f, enabled = enabled, ripple = false) { tick(); onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text("START", style = Type.Subtitle, color = if (enabled) C.OnPrimary else Dim)
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
            ScreenHeader("Speed test", onBack = { SpeedTest.stop() }) { TextAction("Stop", { SpeedTest.stop() }) }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.Gutter).staggerIn(0), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("%02ds".format(cfg.seconds), style = Type.SmallMedium, color = C.Muted)
                    Spacer(Modifier.height(Space.Xs))
                    Text("%02d:%02ds".format(secs / 60, secs % 60), style = Type.SmallMedium, color = C.Text)
                }
                ModeToggle(mode, onMode)
            }
            // The gauge and tiles stay put (so the gauge's glow is never clipped); only the info rows scroll.
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.Gutter)) {
                Spacer(Modifier.height(Space.Md))
                Box(Modifier.fillMaxWidth().staggerIn(1)) {
                    Crossfade(mode, animationSpec = tween(Motion.Medium), label = "speedMode") { m ->
                        if (m == 0) SpeedDial(live, dialMax, upload = ui.phase == SpeedPhase.Upload) else LogCard(ui.log)
                    }
                }
                Spacer(Modifier.height(Space.Sm))
                StatGrid(ui, highlightDown = ui.phase == SpeedPhase.Download, highlightUp = ui.phase == SpeedPhase.Upload || (bidir && ui.phase == SpeedPhase.Download), modifier = Modifier.staggerIn(2))
                Spacer(Modifier.height(Space.Sm))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).staggerIn(3)) {
                    InfoRows(connection, cfg)
                }
            }
        }
    }
}

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
        0f to UpColor, at(0.12f) to UpColor, at(0.6f) to C.Accent, 1f to C.Accent,
    ) else arrayOf(
        0f to DownColor, at(0.12f) to DownColor, at(0.6f) to BlueTip, 1f to BlueTip,
    )
}

/** Where the gauge's arc sits inside a canvas of [size]: hub (center of the half circle) and radius. */
private fun DrawScope.dialHub() = Offset(size.width / 2, size.height - 30.dp.toPx())
private fun DrawScope.dialRadius() = size.width * 0.375f

/** The filled part of the gauge arc, drawn on its own so the caller can blur it into a glow. Same geometry as [SpeedDial]. */
@Composable
private fun DialGlowLayer(modifier: Modifier, frac: Float, upload: Boolean, strokeDp: Float, alpha: Float) {
    Canvas(modifier.fillMaxSize()) {
        val sweep = DIAL_START + DIAL_SWEEP * frac - 180f
        if (sweep <= 0.5f) return@Canvas
        val hub = dialHub()
        val r = dialRadius()
        drawArc(
            brush = Brush.sweepGradient(*dialStops(upload), center = hub), startAngle = 180f, sweepAngle = sweep, useCenter = false,
            topLeft = Offset(hub.x - r, hub.y - r), size = Size(2 * r, 2 * r), alpha = alpha,
            style = Stroke(strokeDp.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * Half-circle gauge like the design: a gradient arc with a real blurred copy of itself as the glow (same technique as the START ring),
 * ten numbers, a thin needle. No tick marks. Blur needs Android 12; older phones keep the soft round hazes.
 */
@Composable
private fun SpeedDial(value: Double, max: Double, upload: Boolean, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState((value / max).toFloat().coerceIn(0f, 1f), tween(Motion.Medium, easing = Motion.Standard), label = "dial")
    val measurer = rememberTextMeasurer()
    val labelStyle = remember { Type.Label.copy(color = C.TextDim, letterSpacing = 0.4.sp) }
    val blurOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(modifier.fillMaxWidth().aspectRatio(2f)) {
        if (blurOk) {
            DialGlowLayer(Modifier.blur(26.dp, BlurredEdgeTreatment.Unbounded), frac, upload, 9f, 0.55f)
            DialGlowLayer(Modifier.blur(10.dp, BlurredEdgeTreatment.Unbounded), frac, upload, 6f, 0.9f)
        }
        Canvas(Modifier.fillMaxSize()) {
            val hub = dialHub()
            val r = dialRadius()
            val topLeft = Offset(hub.x - r, hub.y - r)
            val arc = Size(2 * r, 2 * r)
            val stops = dialStops(upload)
            val brush = Brush.sweepGradient(*stops, center = hub)
            fun onArc(deg: Float, rad: Float): Offset {
                val a = Math.toRadians(deg.toDouble())
                return Offset(hub.x + (cos(a) * rad).toFloat(), hub.y + (sin(a) * rad).toFloat())
            }

            val sweep = DIAL_START + DIAL_SWEEP * frac - 180f
            if (!blurOk && sweep > 0.5f) {
                // Fallback smoke for Android 11 and older: soft hazes along the filled arc, with a bigger bloom at the tip.
                val n = (sweep / 7f).toInt() + 1
                for (k in 0..n) {
                    val deg = 180f + minOf(sweep, k * 7f)
                    val tip = k == n
                    haze(onArc(deg, r), (if (tip) 100 else 60).dp.toPx(), colorAt(stops, deg / 360f), if (tip) 0.28f else 0.05f)
                }
            }

            drawArc(color = C.Track, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(1.dp.toPx()))
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
            drawLine(color = C.OnPrimary, start = hub, end = tipPoint, strokeWidth = 1.5f.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(C.OnPrimary, 3.5f.dp.toPx(), hub)
        }
    }
}

// ---------------------------------------------------------------- done

@Composable
private fun rememberCountUp(target: Double): Double {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { if (target >= 0) a.animateTo(target.toFloat(), tween(Motion.Long * 2, delayMillis = Motion.Medium, easing = Motion.Standard)) }
    return if (target < 0) target else a.value.toDouble()
}

@Composable
private fun DoneView(ui: SpeedUi, mode: Int, onMode: (Int) -> Unit, connection: String, onShare: () -> Unit) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(Motion.Long * 2, delayMillis = Motion.Medium + Motion.Stagger, easing = Motion.Standard)) }
    val downShown = rememberCountUp(ui.downMbps)
    val upShown = rememberCountUp(ui.upMbps)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Speed test", onBack = { SpeedTest.reset() }) { IconAction(ProtonIcons.ArrowUpFromSquare, "Share result", onClick = onShare) }
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.Gutter), horizontalArrangement = Arrangement.End) { ModeToggle(mode, onMode) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter)) {
                Spacer(Modifier.height(Space.Md))
                if (mode == 0) {
                    AppCard(Modifier.fillMaxWidth().staggerIn(0), padding = Space.Lg) {
                        Row(Modifier.fillMaxWidth()) {
                            BigStat("Download", DownColor, mbps(downShown), "Mbps", Modifier.weight(1f))
                            BigStat("Upload", UpColor, mbps(upShown), "Mbps", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(Space.Lg))
                        Row(Modifier.fillMaxWidth()) {
                            BigStat("Ping", Dim, ms(ui.pingMs), "ms", Modifier.weight(1f))
                            BigStat("Jitter", Dim, ms(ui.jitterMs), "ms", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(Space.Lg))
                        SpeedChart(ui.downSeries, ui.upSeries, reveal.value, Modifier.fillMaxWidth().height(Sizes.SpeedChart))
                    }
                } else {
                    LogCard(ui.log)
                    Spacer(Modifier.height(Space.Sm))
                    StatGrid(ui, highlightDown = true, highlightUp = true)
                }
                Spacer(Modifier.height(Space.Xl))
                Column(Modifier.staggerIn(1)) { InfoRows(connection, ui.cfg) }
                Spacer(Modifier.height(Space.Xl))
                AppButton(
                    "Test Again", { SpeedTest.start(if (ui.viaVpn) VpnState.socksPort.value else 0) },
                    Modifier.fillMaxWidth().staggerIn(2),
                )
                Spacer(Modifier.height(Space.Sm))
            }
        }
    }
}

// ---------------------------------------------------------------- shared pieces

/** Gauge / log switch. */
@Composable
private fun ModeToggle(mode: Int, onMode: (Int) -> Unit) {
    val tick = rememberTick()
    Row(Modifier.height(Sizes.Control).clip(RoundedCornerShape(Radius.Control)).background(C.Card).padding(Space.Xs / 2)) {
        listOf(ProtonIcons.Bolt, ProtonIcons.WindowTerminal).forEachIndexed { i, icon ->
            val on = i == mode
            val bg by animateColorAsState(if (on) C.Primary else Color.Transparent, tween(Motion.Short), label = "toggleBg")
            val tint by animateColorAsState(if (on) C.OnPrimary else C.Muted, tween(Motion.Short), label = "toggleTint")
            Box(
                Modifier.width(Sizes.Control).fillMaxHeight().clip(RoundedCornerShape(Radius.Small)).background(bg)
                    .clickable { if (!on) tick(); onMode(i) },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, if (i == 0) "Gauge" else "Log", tint = tint, modifier = Modifier.size(Sizes.Icon)) }
        }
    }
}

/** Download / Upload on top, Ping / Jitter below. The tile being measured gets a colored label, the others are muted. */
@Composable
private fun StatGrid(ui: SpeedUi, highlightDown: Boolean, highlightUp: Boolean, modifier: Modifier = Modifier) {
    val bidirLive = ui.cfg.mode == TransmitMode.Bidirectional && ui.phase == SpeedPhase.Download
    val down = if (ui.phase == SpeedPhase.Download) ui.live else ui.downMbps
    val up = if (ui.phase == SpeedPhase.Upload) ui.live else if (bidirLive) ui.liveUp else ui.upMbps
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.Sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
            StatTile("Download", if (highlightDown) DownColor else Dim, mbps(down.coerceAtLeast(0.0)), "Mbps", Modifier.weight(1f))
            StatTile("Upload", if (highlightUp) UpColor else Dim, mbps(up.coerceAtLeast(0.0)), "Mbps", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
            StatTile("Ping", Dim, ms(ui.pingMs.coerceAtLeast(0)), "ms", Modifier.weight(1f))
            StatTile("Jitter", Dim, ms(ui.jitterMs.coerceAtLeast(0)), "ms", Modifier.weight(1f))
        }
    }
}

/** Muted label, big value, small unit, on a card. */
@Composable
private fun StatTile(label: String, labelColor: Color, value: String, unit: String, modifier: Modifier = Modifier) {
    val labelShown by animateColorAsState(labelColor, tween(Motion.Medium), label = "tileLabel")
    AppCard(modifier, padding = Space.Md) {
        Text(label, style = Type.Body, color = labelShown)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Type.Title, color = C.Text, maxLines = 1)
            Spacer(Modifier.width(Space.Xs))
            Text(unit, style = Type.Caption, color = C.Muted, modifier = Modifier.padding(bottom = Space.Xs))
        }
    }
}

/** Same figures, without a card of their own (they sit inside one). */
@Composable
private fun BigStat(label: String, labelColor: Color, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = Type.Body, color = labelColor)
        Spacer(Modifier.height(Space.Xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = Type.Title, color = C.Text, maxLines = 1)
            Spacer(Modifier.width(Space.Xs))
            Text(unit, style = Type.Caption, color = C.Muted, modifier = Modifier.padding(bottom = Space.Xs))
        }
    }
}

@Composable
private fun LogCard(log: List<String>) {
    AppCard(Modifier.fillMaxWidth().heightIn(min = Sizes.LogMin, max = Sizes.LogMax), padding = Space.Md) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                if (log.isEmpty()) "No test run yet." else log.joinToString("\n"),
                style = Type.Mono, color = if (log.isEmpty()) C.Muted else C.Text,
            )
        }
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
        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        for (f in listOf(0f, 0.5f, 1f)) {
            val y = size.height - f * (size.height - 2.dp.toPx()) - 1.dp.toPx()
            drawLine(C.Border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
        }
        clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
            line(down, DownColor)
            line(up, UpColor)
        }
    }
}

/** The connection details as one card with divider lines. */
@Composable
private fun InfoRows(connection: String, cfg: SpeedConfig) {
    DetailCard(
        listOf(
            "Test server" to SpeedTest.SERVER_NAME,
            "Connection" to connection,
            "Transmit Mode" to cfg.mode.name,
            "Test duration" to "${cfg.seconds}s",
            "Streams" to "${cfg.streams}",
        ),
    )
}

private fun mbps(v: Double): String = when {
    v < 0 -> "-"
    v < 10 -> "%.1f".format(v)
    else -> "%.0f".format(v)
}

private fun ms(v: Int): String = if (v < 0) "-" else v.toString()
