package app.xvpn.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.IpInfo
import app.xvpn.data.Profile
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import kotlinx.coroutines.delay

private object MapMemory {
    var pos: GeoPoint? = null
    var code: String? = null
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
        when (status) { Status.Connected -> C.Green; Status.Connecting -> C.Neutral; Status.Disconnected -> C.Red },
        label = "tone",
    )

    fun place(): Pair<GeoPoint, String?>? {
        if (connecting) {
            val c = selected?.name?.let { Flags.iso(it) }
            if (c != null) WorldGeo.centerOf(c)?.let { return it to c }
            return null
        }
        val p = if (on) proxy else real
        return p?.let { GeoPoint(it.lon, it.lat) to it.code.ifBlank { null } }
    }

    val start = MapMemory.pos?.let { it to MapMemory.code }
        ?: place() ?: real?.let { GeoPoint(it.lon, it.lat) to it.code.ifBlank { null } }
    var cam by remember { mutableStateOf(start?.first ?: GeoPoint(103.8, 1.3)) }
    var camCode by remember { mutableStateOf(start?.second) }
    LaunchedEffect(on, connecting, real, proxy, selected?.name) {
        place()?.let { (g, c) -> cam = g; camCode = c; MapMemory.pos = g; MapMemory.code = c }
    }

    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(connecting) {
        slow = false
        if (connecting) { delay(6000); slow = true }
    }

    Box(Modifier.fillMaxSize()) {
        WorldMap(
            target = cam, countryCode = camCode, dotColor = tone, pulseMs = if (connecting) 700 else 1600,
            modifier = Modifier.fillMaxSize(), centerY = 0.46f, halo = !connecting,
        )

        Box(Modifier.fillMaxWidth().fillMaxHeight(0.5f).background(Brush.verticalGradient(listOf(tone.copy(alpha = 0.55f), Color.Transparent))))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.4f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, C.Bg))),
        )

        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(40.dp))
            AnimatedContent(
                targetState = status,
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
                transitionSpec = { statusTransition() },
                label = "homeHeader",
            ) { st ->
                when (st) {
                    Status.Connected -> ProtectedHeader(fmtDuration(now - connectedAt), up, down)
                    Status.Connecting -> ConnectingHeader(real)
                    Status.Disconnected -> UnprotectedHeader(real, error)
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = when { on -> "Browsing safely from"; connecting -> "Connecting to..."; else -> "Selected server" },
                transitionSpec = { fadeTransition() },
                label = "caption",
            ) { caption ->
                Text(caption, color = C.Text, fontSize = 17.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card).border(1.dp, C.CardBorder, RoundedCornerShape(Radius.Card)).padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth().then(if (on) Modifier.pressable(RoundedCornerShape(Radius.Small), 0.985f, onClick = onOpenStats) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlagBadge(selected?.name)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = selected?.let { Flags.clean(it.name) } ?: "No server selected",
                            transitionSpec = { fadeTransition() }, label = "serverName",
                        ) { name ->
                            Text(name, color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        AnimatedContent(
                            targetState = when {
                                on && proxy != null -> listOf(proxy.city, proxy.country).filter { it.isNotBlank() }.joinToString(", ")
                                selected != null -> "${selected.host}:${selected.port}"
                                else -> "Add one in Servers"
                            },
                            transitionSpec = { fadeTransition() }, label = "serverSub",
                        ) { sub -> Text(sub, color = C.Muted, fontSize = 14.sp, maxLines = 1) }
                    }
                    AnimatedVisibility(
                        visible = on,
                        enter = fadeIn(tween(Motion.Short + 50)) + expandHorizontally(tween(Motion.Medium, easing = Motion.Standard)),
                        exit = fadeOut(tween(Motion.Short - 40)) + shrinkHorizontally(tween(Motion.Short + 50, easing = Motion.Standard)),
                    ) { Icon(Icons.Filled.ChevronRight, "Statistics", tint = C.Muted) }
                }
                Spacer(Modifier.height(16.dp))
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { fadeTransition() },
                    label = "homeActions",
                ) { st ->
                    Column {
                        when (st) {
                            Status.Connecting -> {
                                BigButton("Cancel", C.ButtonGrey, onToggle)
                                AnimatedVisibility(slow) {
                                    Column { Spacer(Modifier.height(10.dp)); OutlineButton("Change server", onChangeServer) }
                                }
                            }
                            Status.Connected -> {
                                BigButton("Disconnect", C.ButtonGrey, onToggle)
                                Spacer(Modifier.height(10.dp))
                                OutlineButton("Change server", onChangeServer)
                            }
                            Status.Disconnected -> {
                                BigButton("Connect", C.Button, onToggle)
                                Spacer(Modifier.height(10.dp))
                                OutlineButton("Change server", onChangeServer)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun UnprotectedHeader(real: IpInfo?, error: String?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.LockOpen, null, tint = C.Red, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            "You are unprotected",
            color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.clip(RoundedCornerShape(Radius.Small)).background(Color(0x66000000)).padding(horizontal = 14.dp, vertical = 8.dp),
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
private fun ConnectingHeader(real: IpInfo?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp), color = Color.White, trackColor = Color.White.copy(alpha = 0.18f),
                strokeWidth = 2.5.dp, strokeCap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Protecting your digital identity",
            color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.clip(RoundedCornerShape(Radius.Small)).background(Color(0x66000000)).padding(horizontal = 14.dp, vertical = 8.dp)) {
            val label = real?.let { r -> listOf(r.city.ifBlank { r.country }, "·", r.ip).joinToString(" ") } ?: "Locating…"
            ScrambleText(label, C.Text, 16.sp)
        }
    }
}

@Composable
private fun ScrambleText(text: String, color: Color, fontSize: TextUnit) {
    fun maskable(ch: Char) = ch != ' ' && ch != '.'
    val order = remember(text) { text.indices.filter { maskable(text[it]) }.shuffled() }
    val prog = remember(text) { Animatable(0f) }
    LaunchedEffect(text) { prog.animateTo(1f, tween(900, easing = LinearEasing)) }
    val hidden = order.take((prog.value * order.size).toInt()).toHashSet()
    val shown = buildString { text.forEachIndexed { i, ch -> append(if (i in hidden) '*' else ch) } }
    Text(shown, color = color, fontSize = fontSize, maxLines = 1)
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
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card.copy(alpha = 0.92f)).padding(16.dp),
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
    val tick = rememberTick(strong = true)
    val color by animateColorAsState(bg, tween(Motion.Medium), label = "bigButton")
    Box(
        Modifier.fillMaxWidth().height(54.dp).pressable(RoundedCornerShape(Radius.Small), 0.97f) { tick(); onClick() }.background(color),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun OutlineButton(label: String, onClick: () -> Unit) {
    val tick = rememberTick()
    Box(
        Modifier.fillMaxWidth().height(54.dp).pressable(RoundedCornerShape(Radius.Small), 0.97f) { tick(); onClick() }
            .border(1.dp, Color(0xFF4F4D5C), RoundedCornerShape(Radius.Small)),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun FlagBadge(name: String?) {
    val res = name?.let { Flags.iso(it) }?.let { FLAG_RES[it] }
    Box(
        Modifier.size(width = 36.dp, height = 24.dp).clip(RoundedCornerShape(Radius.Badge)).background(C.ButtonGrey),
        contentAlignment = Alignment.Center,
    ) {
        if (res != null) Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.Public, null, tint = C.Muted, modifier = Modifier.size(16.dp))
    }
}
