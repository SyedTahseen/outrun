package app.xvpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.IpInfo
import app.xvpn.data.Profile
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import kotlinx.coroutines.delay

internal fun fmtKb(b: Long): String = "%.2f KB/s".format(b / 1000.0)

internal fun fmtDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return when {
        h > 0 -> "$h h %02d min".format(m)
        m > 0 -> "$m min $sec sec"
        else -> "$sec sec"
    }
}

@Composable
fun HomeScreen(
    selected: Profile?,
    real: IpInfo?,
    proxy: IpInfo?,
    onToggle: () -> Unit,
    onChangeServer: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val status by VpnState.status.collectAsState()
    val connectedAt by VpnState.connectedAt.collectAsState()
    val up by VpnState.upBytesPerSec.collectAsState()
    val down by VpnState.downBytesPerSec.collectAsState()
    val error by VpnState.error.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val on = status == Status.Connected
    val connecting = status == Status.Connecting
    val tone by animateColorAsState(
        when (status) { Status.Connected -> C.Green; Status.Connecting -> C.Amber; Status.Disconnected -> C.Red },
        label = "tone",
    )

    // where the dot should be: your real location, or the proxy's once connected
    var cam by remember { mutableStateOf(GeoPoint(103.8, 1.3)) }
    LaunchedEffect(on, real, proxy) {
        val p = if (on) proxy else real
        if (p != null) cam = GeoPoint(p.lon, p.lat)
    }

    Box(Modifier.fillMaxSize()) {
        WorldMap(
            target = cam, dotColor = tone, pulseMs = if (connecting) 700 else 1600,
            modifier = Modifier.fillMaxSize(), centerY = 0.46f,
        )
        // colour wash at the top (red = unprotected, green = protected) and a fade into the background below
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).background(Brush.verticalGradient(listOf(tone.copy(alpha = 0.55f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.4f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, C.Bg))),
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(40.dp))
            if (on) ProtectedHeader(fmtDuration(now - connectedAt), up, down)
            else UnprotectedHeader(connecting, real, error)

            Spacer(Modifier.weight(1f))

            Text(
                when { on -> "Browsing safely from"; connecting -> "Connecting to"; else -> "Selected server" },
                color = C.Text, fontSize = 17.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.Card).border(1.dp, C.CardBorder, RoundedCornerShape(20.dp)).padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().then(if (on) Modifier.clickable(onClick = onOpenStats) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Badge(selected?.protocol ?: "—")
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selected?.name ?: "No server selected", color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            when {
                                on && proxy != null -> listOf(proxy.city, proxy.country).filter { it.isNotBlank() }.joinToString(", ")
                                selected != null -> "${selected.host}:${selected.port}"
                                else -> "Add one in Servers"
                            },
                            color = C.Muted, fontSize = 14.sp, maxLines = 1,
                        )
                    }
                    if (on) Icon(Icons.Filled.ChevronRight, "Statistics", tint = C.Muted)
                }
                Spacer(Modifier.height(16.dp))
                if (on || connecting) {
                    BigButton(if (on) "Disconnect" else "Cancel", C.ButtonGrey, onToggle)
                    Spacer(Modifier.height(10.dp))
                    OutlineButton("Change server", onChangeServer)
                } else {
                    BigButton("Connect", C.Button, onToggle)
                    Spacer(Modifier.height(10.dp))
                    OutlineButton("Change server", onChangeServer)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UnprotectedHeader(connecting: Boolean, real: IpInfo?, error: String?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            if (connecting) Icons.Filled.Lock else Icons.Filled.LockOpen, null,
            tint = if (connecting) C.Amber else C.Red, modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (connecting) "Connecting…" else "You are unprotected",
            color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)).padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(real?.let { r -> r.city.ifBlank { r.country } } ?: "Locating…", color = C.Text, fontSize = 16.sp)
            if (real != null) Text("  ·  ${real.ip}", color = C.Muted, fontSize = 16.sp)
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = C.Red, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ProtectedHeader(time: String, up: Long, down: Long) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = C.Green, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(12.dp))
            Text("Protected", color = C.Green, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card.copy(alpha = 0.92f)).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Connected for", color = C.Muted, fontSize = 13.sp)
                Text(time, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Speed(Icons.Filled.ArrowDownward, C.Green, fmtKb(down))
            Spacer(Modifier.width(16.dp))
            Speed(Icons.Filled.ArrowUpward, C.Red, fmtKb(up))
        }
    }
}

@Composable
private fun Speed(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, color = C.Text, fontSize = 13.sp)
    }
}

@Composable
private fun BigButton(label: String, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun OutlineButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF4F4D5C), RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium) }
}

/** Protocol pill used in place of the country flag. */
@Composable
fun Badge(protocol: String) {
    Box(
        Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(8.dp)).background(C.Green),
        contentAlignment = Alignment.Center,
    ) {
        Text(protocol.uppercase(), color = Color(0xFF0B2E25), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
