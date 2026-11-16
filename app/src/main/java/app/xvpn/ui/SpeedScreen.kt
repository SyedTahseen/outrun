package app.xvpn.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.SpeedPhase
import app.xvpn.data.SpeedTest
import app.xvpn.data.running
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import kotlin.math.ln

@Composable
fun SpeedScreen(serverName: String?) {
    val ui by SpeedTest.state.collectAsState()
    val status by VpnState.status.collectAsState()
    val socks by VpnState.socksPort.collectAsState()
    val viaVpn = status == Status.Connected && socks > 0
    val running = ui.phase.running
    val connecting = status == Status.Connecting

    // Number in the middle of the gauge: the live speed while measuring, otherwise the last download result.
    val shown = when (ui.phase) {
        SpeedPhase.Download, SpeedPhase.Upload -> ui.live
        else -> ui.downMbps.coerceAtLeast(0.0)
    }
    val frac by animateFloatAsState(
        (ln(1.0 + shown) / ln(1.0 + 1000.0)).toFloat().coerceIn(0f, 1f), tween(250), label = "speedGauge",
    )
    val phaseLabel = when (ui.phase) {
        SpeedPhase.Idle -> if (ui.downMbps >= 0) "Stopped" else "Ready"
        SpeedPhase.Ping -> "Measuring latency…"
        SpeedPhase.Download -> "Download"
        SpeedPhase.Upload -> "Upload"
        SpeedPhase.Done -> "Download speed"
        SpeedPhase.Failed -> "Test failed"
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        HeaderRow { Text("Speed test", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        Spacer(Modifier.height(8.dp))

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(220.dp)) {
                    val sw = 16.dp.toPx()
                    val arc = Size(size.width - sw, size.height - sw)
                    val tl = Offset(sw / 2, sw / 2)
                    drawArc(C.Card, 135f, 270f, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                    if (frac > 0.005f) drawArc(C.Accent, 135f, 270f * frac, false, tl, arc, style = Stroke(sw, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(phaseLabel, color = if (ui.phase == SpeedPhase.Failed) C.Red else C.Muted, fontSize = 14.sp)
                    Text(
                        if (ui.phase == SpeedPhase.Ping) "…" else mbps(shown), color = C.Text,
                        fontSize = 52.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                    Text("Mbps", color = C.Muted, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    ui.phase == SpeedPhase.Failed -> "Couldn't reach the test server. Check your connection and try again."
                    viaVpn -> "Testing through the VPN" + (serverName?.let { " · $it" } ?: "")
                    else -> "VPN is off. This tests your direct connection."
                },
                color = C.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                ResultCard(Icons.Filled.ArrowDownward, "Download", mbps(ui.downMbps), "Mbps", Modifier.weight(1f))
                ResultCard(Icons.Filled.ArrowUpward, "Upload", mbps(ui.upMbps), "Mbps", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                ResultCard(Icons.Outlined.Timer, "Ping", ms(ui.pingMs), "ms", Modifier.weight(1f))
                ResultCard(Icons.Outlined.SwapVert, "Jitter", ms(ui.jitterMs), "ms", Modifier.weight(1f))
            }
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

@Composable
private fun ResultCard(icon: ImageVector, label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = C.Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = C.Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (value != "-") {
                Spacer(Modifier.width(4.dp))
                Text(unit, color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

private fun mbps(v: Double): String = when {
    v < 0 -> "-"
    v < 10 -> "%.1f".format(v)
    else -> "%.0f".format(v)
}

private fun ms(v: Int): String = if (v < 0) "-" else v.toString()
