package app.xvpn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.IpInfo
import app.xvpn.data.LeakCheck
import app.xvpn.data.LeakTest
import app.xvpn.data.LinkParser
import app.xvpn.data.Level
import app.xvpn.data.Profile
import app.xvpn.data.Store
import app.xvpn.data.Usage
import app.xvpn.vpn.VpnState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StatsScreen(profile: Profile?, real: IpInfo?, proxy: IpInfo?, onBack: () -> Unit) {
    val up by VpnState.upBytesPerSec.collectAsState()
    val down by VpnState.downBytesPerSec.collectAsState()
    val history by VpnState.speedHistory.collectAsState()
    val connectedAt by VpnState.connectedAt.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val ctx = LocalContext.current
    val sessUp by VpnState.sessionUp.collectAsState()
    val sessDown by VpnState.sessionDown.collectAsState()
    val today = remember(now / 5000) { Usage.total(ctx, 1) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlagBadge(profile?.name)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(profile?.let { Flags.clean(it.name) } ?: "Server", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(
                        listOfNotNull(proxy?.city, proxy?.country).filter { it.isNotBlank() }.joinToString(", "),
                        color = C.Muted, fontSize = 14.sp,
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth()) {
            Label("IP address")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("My IP", color = C.Muted, fontSize = 13.sp)
                    Text(real?.ip ?: "…", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = C.Muted, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("VPN IP", color = C.Muted, fontSize = 13.sp)
                    Text(proxy?.ip ?: "…", color = C.Green, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }

        Column(Modifier.fillMaxWidth()) {
            Label("VPN speed")
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Download", color = C.Green, fontSize = 15.sp); Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowDownward, null, tint = C.Green, modifier = Modifier.size(16.dp))
                    }
                    Text(fmtKb(down), color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Upload", color = C.Red, fontSize = 15.sp); Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.ArrowUpward, null, tint = C.Red, modifier = Modifier.size(16.dp))
                    }
                    Text(fmtKb(up), color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(8.dp))
            SpeedChart(history, Modifier.fillMaxWidth().height(150.dp))
        }

        Column(Modifier.fillMaxWidth()) {
            Label("Connection details")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card)) {
                DetailRow("Connected for", fmtDuration(now - connectedAt))
                DetailRow("This session", "↓ ${fmtSize(sessDown)}  ↑ ${fmtSize(sessUp)}")
                DetailRow("Today", "↓ ${fmtSize(today.second)}  ↑ ${fmtSize(today.first)}")
                DetailRow("Country", proxy?.country?.ifBlank { "—" } ?: "—")
                DetailRow("City", proxy?.city?.ifBlank { "—" } ?: "—")
                DetailRow("Server", profile?.let { Flags.clean(it.name) } ?: "—")
                DetailRow("Protocol", profile?.let { LinkParser.describe(it.link) }?.ifBlank { null } ?: "—", last = true)
            }
        }

        LeakSection()
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LeakSection() {
    val ctx = LocalContext.current
    val socks by VpnState.socksPort.collectAsState()
    var results by remember { mutableStateOf<List<LeakCheck>?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Label("Leak protection")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card)) {
        val list = results
        if (list == null) {
            Text(
                if (running) "Testing…" else "Checks your IP address, routing, DNS, IPv6 and kill switch while connected.",
                color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(18.dp),
            )
        } else list.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.Top) {
                val tone = when (c.level) { Level.Ok -> C.Green; Level.Fail -> C.Red; Level.Warn -> C.Amber; Level.Info -> C.Muted }
                Box(Modifier.padding(top = 6.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(tone))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(c.detail, color = C.Muted, fontSize = 14.sp)
                }
            }
            if (i < list.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(C.CardBorder))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.CardBorder))
        Text(
            if (running) "Running…" else if (results == null) "Run test" else "Run again",
            color = if (running) C.Muted else C.Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(enabled = !running && socks > 0) {
                running = true
                scope.launch {
                    val store = Store(ctx)
                    results = runCatching { LeakTest.run(ctx, socks, store.ipv6Mode, store.dnsServers()) }.getOrNull()
                    running = false
                }
            }.padding(16.dp),
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = C.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
}

@Composable
private fun DetailRow(k: String, v: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = C.Muted, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(v, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(C.CardBorder))
}

@Composable
private fun SpeedChart(history: List<Pair<Long, Long>>, modifier: Modifier) {
    val n = 60
    val pad = (n - history.size).coerceAtLeast(0)
    val ups = List(n) { i -> if (i < pad) 0L else history[i - pad].first }
    val downs = List(n) { i -> if (i < pad) 0L else history[i - pad].second }
    val peak = maxOf(ups.max(), downs.max())
    var top = 4000.0
    while (top < peak) top *= 2

    Row(modifier) {
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val w = size.width; val h = size.height
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            for (f in listOf(0f, 0.5f, 1f)) {
                val y = h - f * (h - 2.dp.toPx()) - 1.dp.toPx()
                drawLine(Color(0xFF3A3944), Offset(0f, y), Offset(w, y), 1.dp.toPx(), pathEffect = dash)
            }
            fun line(vals: List<Long>, close: Boolean): Path {
                val p = Path()
                val xs = FloatArray(n) { it * w / (n - 1) }
                val ys = FloatArray(n) { h - 2.dp.toPx() - (vals[it] / top).toFloat() * (h - 6.dp.toPx()) }
                p.moveTo(xs[0], ys[0])
                for (i in 1 until n) { val xm = (xs[i - 1] + xs[i]) / 2; p.cubicTo(xm, ys[i - 1], xm, ys[i], xs[i], ys[i]) }
                if (close) { p.lineTo(w, h); p.lineTo(0f, h); p.close() }
                return p
            }
            for ((vals, color) in listOf(downs to C.Green, ups to C.Red)) {
                drawPath(line(vals, true), Brush.verticalGradient(listOf(color.copy(alpha = 0.30f), Color.Transparent)))
                drawPath(line(vals, false), color, style = Stroke(2.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxHeight().padding(start = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("%.0f".format(top / 1000), color = C.Muted, fontSize = 12.sp)
            Text("%.0f".format(top / 2000), color = C.Muted, fontSize = 12.sp)
            Text("0", color = C.Muted, fontSize = 12.sp)
        }
    }
}
