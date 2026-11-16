package app.xvpn.ui

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.SpeedPhase
import app.xvpn.data.SpeedTest
import app.xvpn.data.running
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

private val DownColor = C.Green
private val UpColor = C.Accent

@Composable
fun SpeedScreen(serverName: String?) {
    val ui by SpeedTest.state.collectAsState()
    val status by VpnState.status.collectAsState()
    val socks by VpnState.socksPort.collectAsState()
    val viaVpn = status == Status.Connected && socks > 0
    val running = ui.phase.running
    val connecting = status == Status.Connecting
    var mode by remember { mutableIntStateOf(0) } // 0 = gauges, 1 = log

    // While a phase is running its card shows the live speed, afterwards the measured average.
    val down = if (ui.phase == SpeedPhase.Download) ui.live else ui.downMbps
    val up = if (ui.phase == SpeedPhase.Upload) ui.live else ui.upMbps

    val statusText = when (ui.phase) {
        SpeedPhase.Idle -> if (ui.downMbps >= 0) "Stopped" else "Ready"
        SpeedPhase.Ping -> "Measuring latency…"
        SpeedPhase.Download -> "Testing download…"
        SpeedPhase.Upload -> "Testing upload…"
        SpeedPhase.Done -> "Finished"
        SpeedPhase.Failed -> "Couldn't reach the test server. Check your connection and try again."
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        HeaderRow {
            Text("Speed test", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            ModeToggle(mode) { mode = it }
        }
        Spacer(Modifier.height(8.dp))

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(
                statusText, fontSize = 14.sp,
                color = when (ui.phase) { SpeedPhase.Failed -> C.Red; SpeedPhase.Done -> C.Green; else -> C.Muted },
            )
            Spacer(Modifier.height(12.dp))

            if (mode == 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Gauge("Download", down, DownColor, Modifier.weight(1f))
                    Gauge("Upload", up, UpColor, Modifier.weight(1f))
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 170.dp).clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(14.dp),
                ) {
                    Text(
                        if (ui.log.isEmpty()) "No test run yet." else ui.log.joinToString("\n"),
                        style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                        color = if (ui.log.isEmpty()) C.Muted else C.Text,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultCard(Icons.Filled.ArrowDownward, "Download", DownColor, mbps(down), "Mbps", Modifier.weight(1f))
                ResultCard(Icons.Filled.ArrowUpward, "Upload", UpColor, mbps(up), "Mbps", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultCard(Icons.Outlined.Timer, "Ping", C.Muted, ms(ui.pingMs), "ms", Modifier.weight(1f))
                ResultCard(Icons.Outlined.SwapVert, "Jitter", C.Muted, ms(ui.jitterMs), "ms", Modifier.weight(1f))
            }

            if (ui.downSeries.size > 1 || ui.upSeries.size > 1) {
                Spacer(Modifier.height(16.dp))
                SpeedChart(ui.downSeries, ui.upSeries)
            }

            Spacer(Modifier.height(16.dp))
            InfoRow("Server", "Cloudflare")
            InfoRow(
                "Connection",
                if (viaVpn) "VPN" + (serverName?.let { " · $it" } ?: "") else "Direct (VPN off)",
            )
            InfoRow("Duration", "${SpeedTest.PHASE_SECONDS}s per test")
            InfoRow("Streams", "${SpeedTest.STREAMS} per test")
            Spacer(Modifier.height(16.dp))
        }

        val shape = RoundedCornerShape(Radius.Card)
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(56.dp).clip(shape)
                .background(if (running || connecting) C.ButtonGrey else C.Button)
                .clickable(enabled = !connecting) {
                    if (running) SpeedTest.stop() else SpeedTest.start(if (viaVpn) socks else 0)
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    running -> "Stop"
                    connecting -> "Connecting…"
                    ui.phase == SpeedPhase.Idle && ui.downMbps < 0 -> "Start test"
                    else -> "Test again"
                },
                color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Gauge / log switch in the header. */
@Composable
private fun ModeToggle(mode: Int, onMode: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(C.Card).padding(3.dp)) {
        listOf(Icons.Outlined.Speed, Icons.Outlined.Terminal).forEachIndexed { i, icon ->
            val on = i == mode
            Box(
                Modifier.size(width = 42.dp, height = 32.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (on) C.NavPill else Color.Transparent).clickable { onMode(i) },
                contentAlignment = Alignment.Center,
            ) { Icon(icon, if (i == 0) "Gauges" else "Log", tint = if (on) C.Text else C.Muted, modifier = Modifier.size(20.dp)) }
        }
    }
}

/** Position on the dial, 0..1. Logarithmic so both slow and fast connections use the whole scale (0 to 1000 Mbps). */
private fun dialFraction(mbps: Double): Float =
    (ln(1.0 + mbps.coerceAtLeast(0.0)) / ln(1001.0)).toFloat().coerceIn(0f, 1f)

@Composable
private fun Gauge(label: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState(dialFraction(value), tween(250), label = "gauge")
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val r = size.minDimension / 2 - stroke
            val c = center
            val topLeft = Offset(c.x - r, c.y - r)
            val arcSize = Size(2 * r, 2 * r)
            drawArc(
                color = C.CardBorder, startAngle = 135f, sweepAngle = 270f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (frac > 0.005f) drawArc(
                color = color, startAngle = 135f, sweepAngle = 270f * frac, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val ticks = 30
            for (i in 0..ticks) {
                val a = Math.toRadians(135.0 + 270.0 * i / ticks)
                val long = i % 5 == 0
                val r1 = r - 14.dp.toPx()
                val r2 = r1 - (if (long) 8.dp else 4.dp).toPx()
                drawLine(
                    color = C.Muted.copy(alpha = if (long) 0.8f else 0.4f),
                    start = Offset(c.x + (cos(a) * r1).toFloat(), c.y + (sin(a) * r1).toFloat()),
                    end = Offset(c.x + (cos(a) * r2).toFloat(), c.y + (sin(a) * r2).toFloat()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            val a = Math.toRadians(135.0 + 270.0 * frac)
            val len = r - 26.dp.toPx()
            drawLine(
                color = C.Text, start = c,
                end = Offset(c.x + (cos(a) * len).toFloat(), c.y + (sin(a) * len).toFloat()),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
            )
            drawCircle(color, 6.dp.toPx(), c)
            drawCircle(C.Bg, 2.5.dp.toPx(), c)
        }
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun ResultCard(icon: ImageVector, label: String, tint: Color, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = if (tint == C.Muted) C.Muted else tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = C.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (value != "-") {
                Spacer(Modifier.width(4.dp))
                Text(unit, color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun SpeedChart(down: List<Float>, up: List<Float>) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend(DownColor, "Download")
            Spacer(Modifier.width(16.dp))
            Legend(UpColor, "Upload")
        }
        Spacer(Modifier.height(12.dp))
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
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
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = C.Muted, fontSize = 13.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = C.Muted, fontSize = 14.sp)
        Spacer(Modifier.width(16.dp))
        Text(value, color = C.Text, fontSize = 14.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

private fun mbps(v: Double): String = when {
    v < 0 -> "-"
    v < 10 -> "%.1f".format(v)
    else -> "%.0f".format(v)
}

private fun ms(v: Int): String = if (v < 0) "-" else v.toString()
