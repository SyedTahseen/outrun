package app.xvpn.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextOverflow
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

private enum class StatsInfo { Ip, Speed }

private fun openLearnMore(ctx: Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Snack.show("No app can open this link", SnackType.Error) }
}

private const val IP_INFO = "Your VPN IP masks your real IP address. This makes it look like you’re browsing from your VPN location, so your online activity can’t be linked to your device."
private const val SPEED_INFO = "This graph shows the speed of your current upload and download activity. Your VPN connection is unlikely to slow down normal online activity."

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
    var info by remember { mutableStateOf<StatsInfo?>(null) }
    var myIpVisible by remember { mutableStateOf(false) }

    // Proton's ConnectionDetails sits on backgroundDeep, including its top bar.
    Column(Modifier.fillMaxSize().background(C.Deep)) {
        ScreenHeader("Statistics", onBack = onBack, base = C.Deep)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter)) {
            Spacer(Modifier.height(Space.Lg))
            // Proton: headline (20 bold) title, 4dp above the subtitle, 16dp icon-to-label gap, min 42dp row.
            Row(Modifier.padding(bottom = Space.Lg).heightIn(min = Sizes.CardRowMin), verticalAlignment = Alignment.Top) {
                FlagBadge(profile?.name)
                Spacer(Modifier.width(Space.Lg))
                Column {
                    Text(profile?.let { Flags.clean(it.name) } ?: "Server", style = Type.Heading, color = C.Text, maxLines = 1)
                    Text(
                        listOfNotNull(proxy?.city, proxy?.country).filter { it.isNotBlank() }.joinToString(", "),
                        style = Type.Small, color = C.Muted, modifier = Modifier.padding(top = Space.Xs),
                    )
                }
            }

            StatsLabel("IP address") { info = StatsInfo.Ip }
            // Proton's IpView: backgroundNorm surface (8dp), 16dp padding per half, 16dp above/below the card.
            AppCard(Modifier.fillMaxWidth().padding(vertical = Space.Lg), padding = 0.dp, containerColor = C.Bg, radius = Radius.Control) {
                Row(Modifier.padding(bottom = Space.Sm), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(Space.Lg), horizontalAlignment = Alignment.CenterHorizontally) {
                        MaskedMyIp(real?.ip, myIpVisible) { myIpVisible = !myIpVisible }
                    }
                    Icon(ProtonIcons.ArrowRight, null, tint = C.Text, modifier = Modifier.size(Sizes.Icon))
                    Column(Modifier.weight(1f).padding(Space.Lg), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("VPN IP", style = Type.Caption, color = C.Muted)
                        Spacer(Modifier.height(Space.Xs))
                        // Proton's ExitIpAddressText: normal text color on a backgroundSecondary chip (4dp, 6x2dp padding).
                        Text(
                            proxy?.ip ?: "…", style = Type.Body, color = C.Text, textAlign = TextAlign.Center,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clip(RoundedCornerShape(Radius.Badge)).background(C.Card).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            StatsLabel("VPN speed") { info = StatsInfo.Speed }
            // Proton: row padded 12dp above / 4dp below; success/error notification colors; 14sp label, 24dp icon, 4dp gaps.
            Row(Modifier.fillMaxWidth().padding(top = Space.Md, bottom = Space.Xs)) {
                Column(Modifier.weight(1f).padding(horizontal = Space.Sm), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Xs)) {
                        Text("Download", style = Type.Small, color = C.SuccessSolid)
                        Icon(ProtonIcons.ArrowDown, null, tint = C.SuccessSolid, modifier = Modifier.size(Sizes.Icon))
                    }
                    Text(fmtKb(down), style = Type.Heading, color = C.Text, modifier = Modifier.padding(bottom = Space.Xs))
                }
                Column(Modifier.weight(1f).padding(horizontal = Space.Sm), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.Xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.Xs)) {
                        Text("Upload", style = Type.Small, color = C.Red)
                        Icon(ProtonIcons.ArrowUp, null, tint = C.Red, modifier = Modifier.size(Sizes.Icon))
                    }
                    Text(fmtKb(up), style = Type.Heading, color = C.Text, modifier = Modifier.padding(bottom = Space.Xs))
                }
            }
            val (speedLabel, speedFactor) = speedUnit(history.maxOfOrNull { maxOf(it.first, it.second) } ?: 0L)
            Text("Speed $speedLabel", style = Type.Label, color = C.Muted, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            SpeedChart(history, speedFactor, Modifier.fillMaxWidth().height(150.dp))

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
                containerColor = C.Bg, radius = Radius.Control, rowPadding = Space.Lg, rowAlignment = Alignment.CenterVertically,
            )

            LeakSection()
            Spacer(Modifier.height(Space.Xxl))
        }
    }

    when (info) {
        StatsInfo.Ip -> AppInfoSheet({ info = null }) { IpInfoContent(real?.ip, proxy?.ip) { openLearnMore(ctx, "https://protonvpn.com/blog/what-is-an-ip-address") } }
        StatsInfo.Speed -> AppInfoSheet({ info = null }) { InfoSheetContent("What is VPN speed?", SPEED_INFO, learnMoreLabel = "Learn more", onLearnMore = { openLearnMore(ctx, "https://protonvpn.com/support/increase-vpn-speeds/") }) }
        null -> {}
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
    AppCard(Modifier.fillMaxWidth(), padding = 0.dp, containerColor = C.Bg, radius = Radius.Control) {
        val list = results
        if (list == null) {
            Text(
                if (running) "Testing…" else "Checks your IP address, routing, DNS, IPv6 and kill switch while connected.",
                style = Type.Small, color = C.Muted, modifier = Modifier.padding(Space.Lg),
            )
        } else list.forEachIndexed { i, c ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.Lg, vertical = Space.Md), verticalAlignment = Alignment.Top) {
                val tone = when (c.level) { Level.Ok -> C.SuccessSolid; Level.Fail -> C.Red; Level.Warn -> C.Amber; Level.Info -> C.Muted }
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
private fun StatsLabel(text: String, onInfo: (() -> Unit)? = null) {
    // Proton's connection-details HeaderText: body2Regular + textWeak, 16dp above / 8dp below, with an optional
    // 20dp iconHint info icon (4dp start padding) that makes the whole header tappable.
    Row(
        Modifier
            .then(if (onInfo != null) Modifier.pressable(RoundedCornerShape(Radius.Small), pressedScale = 1f, onClick = onInfo) else Modifier)
            .padding(top = Space.Lg, bottom = Space.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Type.Small, color = C.Muted)
        if (onInfo != null) Icon(ProtonIcons.InfoOutline, "Info", tint = C.TextHint, modifier = Modifier.size(20.dp).padding(start = Space.Xs))
    }
}

/** Proton's "My IP": masked with asterisks until tapped; eye icon (iconHint) shows the current state. */
@Composable
private fun MaskedMyIp(ip: String?, visible: Boolean, onToggle: () -> Unit) {
    Column(
        Modifier.then(if (ip != null) Modifier.pressable(RoundedCornerShape(Radius.Badge), pressedScale = 1f, onClick = onToggle) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My IP", style = Type.Caption, color = C.Muted, textAlign = TextAlign.Center)
            if (ip != null) Icon(
                if (visible) ProtonIcons.EyeSlash else ProtonIcons.Eye, if (visible) "Hide IP" else "Show IP",
                tint = C.TextHint, modifier = Modifier.size(20.dp).padding(start = Space.Xs),
            )
        }
        Spacer(Modifier.height(Space.Xs))
        Text(
            when { ip == null -> "…"; visible -> ip; else -> ip.replace(Regex("[^.]"), "*") },
            style = Type.Body, color = C.Text, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Proton's IP address info sheet: My IP (hidden until tapped), VPN IP, then the explanation. */
@Composable
private fun IpInfoContent(myIp: String?, vpnIp: String?, onLearnMore: () -> Unit) {
    InfoSheetContent("IP address", null, learnMoreLabel = "What is an IP address?", onLearnMore = onLearnMore) {
        Column(Modifier.padding(vertical = Space.Md), verticalArrangement = Arrangement.spacedBy(Space.Md)) {
            var visible by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("My IP", style = Type.Small, color = C.Muted, modifier = Modifier.padding(end = 2.dp))
                if (myIp != null) {
                    Row(Modifier.pressable(RoundedCornerShape(Radius.Badge), pressedScale = 1f) { visible = !visible }.padding(2.dp)) {
                        Text(if (visible) myIp else "************", style = Type.Small, color = C.Text)
                        Icon(
                            if (visible) ProtonIcons.EyeSlash else ProtonIcons.Eye, if (visible) "Hide IP" else "Show IP",
                            tint = C.TextHint, modifier = Modifier.padding(start = Space.Xs).size(16.dp),
                        )
                    }
                } else Text("Unknown", style = Type.Small, color = C.Text, modifier = Modifier.padding(2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("VPN IP", style = Type.Small, color = C.Muted, modifier = Modifier.padding(end = 2.dp))
                Text(vpnIp ?: "Unknown", style = Type.Small, color = C.Text, modifier = Modifier.padding(2.dp))
            }
        }
        Text(IP_INFO, style = Type.Small, color = C.Text, modifier = Modifier.padding(vertical = Space.Lg))
    }
}

@Composable
private fun SpeedChart(history: List<Pair<Long, Long>>, factor: Double, modifier: Modifier) {
    val n = 60
    val pad = (n - history.size).coerceAtLeast(0)
    val ups = List(n) { i -> if (i < pad) 0L else history[i - pad].first }
    val downs = List(n) { i -> if (i < pad) 0L else history[i - pad].second }
    val peak = maxOf(ups.max(), downs.max())
    var top = 4 * factor
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
            for ((vals, color) in listOf(ups to C.Red, downs to C.SuccessSolid)) {
                drawPath(line(vals, true), Brush.verticalGradient(listOf(color.copy(alpha = 0.7f), color.copy(alpha = 0f))))
                drawPath(line(vals, false), color, style = Stroke(2.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxHeight().padding(start = Space.Sm), verticalArrangement = Arrangement.SpaceBetween) {
            Text("%.0f".format(top / factor), style = Type.Label, color = C.Muted)
            Text("%.0f".format(top / 2 / factor), style = Type.Label, color = C.Muted)
            Text("0", style = Type.Label, color = C.Muted)
        }
    }
}
