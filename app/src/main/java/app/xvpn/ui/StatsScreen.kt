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
import androidx.compose.foundation.shape.CircleShape
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
import app.xvpn.data.Level
import app.xvpn.data.LinkParser
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

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Statistics", onBack = onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter)) {
            Spacer(Modifier.height(Space.Sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlagBadge(profile?.name)
                Spacer(Modifier.width(Space.Md))
                Column {
                    Text(profile?.let { Flags.clean(it.name) } ?: "Server", style = Type.Subtitle, color = C.Text, maxLines = 1)
                    Text(
                        listOfNotNull(proxy?.city, proxy?.country).filter { it.isNotBlank() }.joinToString(", "),
                        style = Type.Small, color = C.Muted,
                    )
                }
            }

            StatsLabel("IP address")
            AppCard(Modifier.fillMaxWidth(), padding = Space.Lg) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("My IP", style = Type.Small, color = C.Muted)
                        Text(real?.ip ?: "…", style = Type.BodyMedium, color = C.Text, maxLines = 1)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = C.Muted, modifier = Modifier.size(Sizes.Icon))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VPN IP", style = Type.Small, color = C.Muted)
                        Text(proxy?.ip ?: "…", style = Type.BodyMedium, color = C.Green, maxLines = 1)
                    }
                }
            }

            StatsLabel("VPN speed")
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Download", style = Type.Body, color = C.Green); Spacer(Modifier.width(Space.Xs))
                        Icon(Icons.Filled.ArrowDownward, null, tint = C.Green, modifier = Modifier.size(Sizes.IconSm))
                    }
                    Text(fmtKb(down), style = Type.Title, color = C.Text)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Upload", style = Type.Body, color = C.Red); Spacer(Modifier.width(Space.Xs))
                        Icon(Icons.Filled.ArrowUpward, null, tint = C.Red, modifier = Modifier.size(Sizes.IconSm))
                    }
                    Text(fmtKb(up), style = Type.Title, color = C.Text)
                }
            }
            Spacer(Modifier.height(Space.Sm))
            SpeedChart(history, Modifier.fillMaxWidth().height(150.dp))

            StatsLabel("Connection details")
            DetailCard(
                listOf(
                    "Connected for" to fmtDuration(now - connectedAt),
                    "This session" to "↓ ${fmtSize(sessDown)}  ↑ ${fmtSize(sessUp)}",
                    "Today" to "↓ ${fmtSize(today.second)}  ↑ ${fmtSize(today.first)}",
                    "Country" to (proxy?.country?.ifBlank { "—" } ?: "—"),
                    "City" to (proxy?.city?.ifBlank { "—" } ?: "—"),
                    "Server" to (profile?.let { Flags.clean(it.name) } ?: "—"),
                    "Protocol" to (profile?.let { LinkParser.describe(it.link) }?.ifBlank { null } ?: "—"),
                ),
            )

            LeakSection()
            Spacer(Modifier.height(Space.Xxl))
        }
    }
}

@Composable
private fun LeakSection() {
    val ctx = LocalContext.current
    val socks by VpnState.socksPort.collectAsState()
    var results by remember { mutableStateOf<List<LeakCheck>?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    StatsLabel("Leak protection")
    AppCard(Modifier.fillMaxWidth(), padding = 0.dp) {
        val list = results
        if (list == null) {
            Text(
                if (running) "Testing…" else "Checks your IP address, routing, DNS, IPv6 and kill switch while connected.",
                style = Type.Small, color = C.Muted, modifier = Modifier.padding(Space.Lg),
            )
        } else list.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.Lg, vertical = Space.Md), verticalAlignment = Alignment.Top) {
                val tone = when (c.level) { Level.Ok -> C.Green; Level.Fail -> C.Red; Level.Warn -> C.Amber; Level.Info -> C.Muted }
                Box(Modifier.padding(top = Space.Sm - 2.dp).size(10.dp).clip(CircleShape).background(tone))
                Spacer(Modifier.width(Space.Md))
                Column(Modifier.weight(1f)) {
                    Text(c.title, style = Type.BodyMedium, color = C.Text)
                    Text(c.detail, style = Type.Small, color = C.Muted)
                }
            }
            if (i < list.size - 1) HairlineDivider()
        }
        HairlineDivider()
        CardAction(
            if (running) "Running…" else if (results == null) "Run test" else "Run again",
            enabled = !running && socks > 0,
            onClick = {
                running = true
                scope.launch {
                    val store = Store(ctx)
                    results = runCatching { LeakTest.run(ctx, socks, store.ipv6Mode, store.dnsServers()) }.getOrNull()
                    running = false
                }
            },
        )
    }
}

@Composable
private fun StatsLabel(text: String) = SectionHeader(text, horizontal = 0.dp)

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
                drawLine(C.Border, Offset(0f, y), Offset(w, y), 1.dp.toPx(), pathEffect = dash)
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
        Column(Modifier.fillMaxHeight().padding(start = Space.Sm), verticalArrangement = Arrangement.SpaceBetween) {
            Text("%.0f".format(top / 1000), style = Type.Label, color = C.Muted)
            Text("%.0f".format(top / 2000), style = Type.Label, color = C.Muted)
            Text("0", style = Type.Label, color = C.Muted)
        }
    }
}
