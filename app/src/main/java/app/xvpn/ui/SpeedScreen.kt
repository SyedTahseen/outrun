package app.xvpn.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Speed
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.SpeedPhase
import app.xvpn.data.SpeedTest
import app.xvpn.data.SpeedUi
import app.xvpn.data.running
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

private val DownColor = C.Amber
private val UpColor = C.Accent
private val Mono = FontFamily.Monospace

private enum class SpeedView { Start, Running, Done }

@Composable
fun SpeedScreen(serverName: String?, onFullScreenChange: (Boolean) -> Unit = {}) {
    val ui by SpeedTest.state.collectAsState()
    val status by VpnState.status.collectAsState()
    val socks by VpnState.socksPort.collectAsState()
    val ctx = LocalContext.current
    val viaVpn = status == Status.Connected && socks > 0
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
            .onFailure { Toast.makeText(ctx, "Couldn't share result", Toast.LENGTH_SHORT).show() }
    }

    Crossfade(view, animationSpec = tween(Motion.Medium), modifier = Modifier.fillMaxSize(), label = "speedView") { v ->
        when (v) {
            SpeedView.Start -> StartView(ui, connecting, connection) { SpeedTest.start(if (viaVpn) socks else 0) }
            SpeedView.Running -> RunningView(ui, mode, { mode = it }, connection)
            SpeedView.Done -> DoneView(ui, mode, { mode = it }, connection, onShare = ::share)
        }
    }
}

// ---------------------------------------------------------------- start

@Composable
private fun StartView(ui: SpeedUi, connecting: Boolean, connection: String, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        HeaderRow { Text("Speed test", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            StartRing(enabled = !connecting, onClick = onStart)
            Spacer(Modifier.height(12.dp))
            val note = when {
                ui.phase == SpeedPhase.Failed -> "Couldn't reach the test server. Check your connection and try again." to C.Red
                connecting -> "Wait for the VPN to finish connecting." to C.Muted
                else -> null
            }
            if (note != null) Text(note.first, color = note.second, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            InfoRows(connection)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StartRing(enabled: Boolean, onClick: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "ring")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "ringAngle")
    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val brush = Brush.sweepGradient(listOf(C.Accent, C.Green, C.Amber, C.Red, C.Accent), center)
            val r = 105.dp.toPx()
            rotate(angle, pivot = center) {
                drawCircle(brush = brush, radius = r, alpha = if (enabled) 0.2f else 0.08f, style = Stroke(24.dp.toPx()))
                drawCircle(brush = brush, radius = r, alpha = if (enabled) 1f else 0.4f, style = Stroke(6.dp.toPx()))
            }
        }
        Box(
            Modifier.size(200.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("START", color = if (enabled) Color.White else C.Muted, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- running

@Composable
private fun RunningView(ui: SpeedUi, mode: Int, onMode: (Int) -> Unit, connection: String) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    val secs = ((now - ui.startedAt) / 1000).coerceAtLeast(0)
    val phaseName = when (ui.phase) {
        SpeedPhase.Download -> "Download"
        SpeedPhase.Upload -> "Upload"
        else -> "Latency"
    }
    val live = if (ui.phase == SpeedPhase.Download || ui.phase == SpeedPhase.Upload) ui.live else 0.0

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        TitleBar(
            left = { HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { SpeedTest.stop() } },
            right = {
                Text(
                    "Stop", color = C.Muted, fontSize = 17.sp,
                    modifier = Modifier.clip(RoundedCornerShape(Radius.Small)).clickable { SpeedTest.stop() }.padding(horizontal = 8.dp, vertical = 10.dp),
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(phaseName, color = C.Muted, fontSize = 14.sp, fontFamily = Mono)
                Spacer(Modifier.height(4.dp))
                Text("%02d:%02ds".format(secs / 60, secs % 60), color = C.Text, fontSize = 14.sp, fontFamily = Mono)
            }
            ModeToggle(mode, onMode)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            if (mode == 0) SpeedDial(live, upload = ui.phase == SpeedPhase.Upload) else LogCard(ui.log)
            Spacer(Modifier.height(12.dp))
            StatGrid(ui, highlightDown = ui.phase == SpeedPhase.Download, highlightUp = ui.phase == SpeedPhase.Upload)
            Spacer(Modifier.height(16.dp))
            InfoRows(connection)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 0..1 position on the dial. Logarithmic, so slow and fast connections both use the whole 0 to 1000 Mbps scale. */
private fun dialFraction(mbps: Double): Float =
    (ln(1.0 + mbps.coerceAtLeast(0.0)) / ln(1001.0)).toFloat().coerceIn(0f, 1f)

private val DialMarks = listOf(0, 10, 25, 50, 100, 250, 500, 1000)

/** Half-circle gauge with a gradient arc, scale numbers and a needle. */
@Composable
private fun SpeedDial(value: Double, upload: Boolean, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState(dialFraction(value), tween(250), label = "dial")
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = C.Text, fontSize = 10.sp, fontFamily = Mono)
    Canvas(modifier.fillMaxWidth().aspectRatio(2f)) {
        val hub = Offset(size.width / 2, size.height - 10.dp.toPx())
        val r = size.width / 2 - 22.dp.toPx()
        val topLeft = Offset(hub.x - r, hub.y - r)
        val arc = Size(2 * r, 2 * r)
        val colors = if (upload) listOf(C.Accent, C.Red, C.Amber) else listOf(C.Amber, C.Red, C.Accent)
        val brush = Brush.sweepGradient(0.5f to colors[0], 0.75f to colors[1], 1f to colors[2], center = hub)

        drawArc(color = C.CardBorder, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(1.dp.toPx()))
        if (frac > 0.005f) {
            drawArc(brush = brush, startAngle = 180f, sweepAngle = 180f * frac, useCenter = false, topLeft = topLeft, size = arc, alpha = 0.2f, style = Stroke(18.dp.toPx(), cap = StrokeCap.Round))
            drawArc(brush = brush, startAngle = 180f, sweepAngle = 180f * frac, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }

        val labelRadius = r - 24.dp.toPx()
        for (mark in DialMarks) {
            val a = Math.PI * (1 + dialFraction(mark.toDouble()))
            val layout = measurer.measure(mark.toString(), labelStyle)
            drawText(
                layout,
                topLeft = Offset(
                    hub.x + (cos(a) * labelRadius).toFloat() - layout.size.width / 2f,
                    hub.y + (sin(a) * labelRadius).toFloat() - layout.size.height / 2f,
                ),
            )
        }

        val a = Math.PI * (1 + frac)
        val len = r * 0.62f
        drawLine(
            color = Color.White, start = hub,
            end = Offset(hub.x + (cos(a) * len).toFloat(), hub.y + (sin(a) * len).toFloat()),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
        )
        drawCircle(Color.White, 4.dp.toPx(), hub)
    }
}

// ---------------------------------------------------------------- done

@Composable
private fun DoneView(ui: SpeedUi, mode: Int, onMode: (Int) -> Unit, connection: String, onShare: () -> Unit) {
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
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card)) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
                        BigStat("Download", DownColor, mbps(ui.downMbps), "Mbps", Modifier.weight(1f))
                        BigStat("Upload", UpColor, mbps(ui.upMbps), "Mbps", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                        BigStat("Ping", C.Muted, ms(ui.pingMs), "ms", Modifier.weight(1f))
                        BigStat("Jitter", C.Muted, ms(ui.jitterMs), "ms", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(16.dp))
                    SpeedChart(ui.downSeries, ui.upSeries, Modifier.fillMaxWidth().height(110.dp))
                }
            } else {
                LogCard(ui.log)
                Spacer(Modifier.height(12.dp))
                StatGrid(ui, highlightDown = true, highlightUp = true)
            }
            Spacer(Modifier.height(20.dp))
            InfoRows(connection)
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(Radius.Pill + 8.dp)).background(Color.White)
                    .clickable { SpeedTest.start(if (ui.viaVpn) VpnState.socksPort.value else 0) }
                    .padding(horizontal = 40.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Test again", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------------------------------------------------------- shared pieces

/** Header with a centered title, an action on each side and no tab-style left alignment. */
@Composable
private fun TitleBar(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) { left() }
        Text("Speed test", color = C.Text, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
        Box(Modifier.align(Alignment.CenterEnd)) { right() }
    }
}

/** Gauge / log switch. */
@Composable
private fun ModeToggle(mode: Int, onMode: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(C.Card).padding(2.dp)) {
        listOf(Icons.Outlined.Speed, Icons.Outlined.Terminal).forEachIndexed { i, icon ->
            val on = i == mode
            Box(
                Modifier.size(width = 44.dp, height = 32.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (on) C.ButtonGrey else Color.Transparent).clickable { onMode(i) },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, if (i == 0) "Gauge" else "Log", tint = if (on) C.Text else C.Muted, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Download / Upload on top, Ping / Jitter below, as tightly packed tiles. The active phase gets a colored label. */
@Composable
private fun StatGrid(ui: SpeedUi, highlightDown: Boolean, highlightUp: Boolean) {
    val down = if (ui.phase == SpeedPhase.Download) ui.live else ui.downMbps
    val up = if (ui.phase == SpeedPhase.Upload) ui.live else ui.upMbps
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            StatTile("Download", if (highlightDown) DownColor else C.Muted, mbps(down.coerceAtLeast(0.0)), "Mbps", Modifier.weight(1f))
            StatTile("Upload", if (highlightUp) UpColor else C.Muted, mbps(up.coerceAtLeast(0.0)), "Mbps", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            StatTile("Ping", C.Muted, ms(ui.pingMs.coerceAtLeast(0)), "ms", Modifier.weight(1f))
            StatTile("Jitter", C.Muted, ms(ui.jitterMs.coerceAtLeast(0)), "ms", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, labelColor: Color, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier.height(84.dp).clip(RoundedCornerShape(14.dp)).background(C.Card).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label, color = labelColor, fontSize = 13.sp)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = C.Text, fontSize = 20.sp, fontFamily = Mono, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(unit, color = C.Muted, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

@Composable
private fun BigStat(label: String, labelColor: Color, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = labelColor, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = C.Text, fontSize = 22.sp, fontFamily = Mono, maxLines = 1)
            Spacer(Modifier.width(5.dp))
            Text(unit, color = C.Muted, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.padding(bottom = 3.dp))
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

/** Smooth area chart of the live speed samples of both phases. */
@Composable
private fun SpeedChart(down: List<Float>, up: List<Float>, modifier: Modifier = Modifier) {
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
        line(down, DownColor)
        line(up, UpColor)
    }
}

@Composable
private fun InfoRows(connection: String) {
    InfoRow("Server", "Cloudflare")
    InfoRow("Connection", connection)
    InfoRow("Test duration", "${SpeedTest.PHASE_SECONDS}s")
    InfoRow("Streams", "${SpeedTest.STREAMS}")
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = C.Muted, fontSize = 14.sp)
        Spacer(Modifier.width(16.dp))
        Text(value, color = C.Text, fontSize = 14.sp, fontFamily = Mono, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private fun mbps(v: Double): String = when {
    v < 0 -> "-"
    v < 10 -> "%.1f".format(v)
    else -> "%.0f".format(v)
}

private fun ms(v: Int): String = if (v < 0) "-" else v.toString()
