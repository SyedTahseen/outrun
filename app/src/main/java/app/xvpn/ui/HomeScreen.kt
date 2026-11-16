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
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
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

        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = Space.Gutter)) {
            Spacer(Modifier.height(Space.Huge))
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
                Text(caption, style = Type.Body, color = C.Text, modifier = Modifier.padding(start = Space.Xs, bottom = Space.Sm))
            }
            AppCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().then(if (on) Modifier.pressable(RoundedCornerShape(Radius.Small), Press.Card, onClick = onOpenStats) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlagBadge(selected?.name)
                    Spacer(Modifier.width(Space.Md))
                    Column(Modifier.weight(1f)) {
                        AnimatedContent(
                            targetState = selected?.let { Flags.clean(it.name) } ?: "No server selected",
                            transitionSpec = { fadeTransition() }, label = "serverName",
                        ) { name ->
                            Text(name, style = Type.Subtitle, color = C.Text, maxLines = 1)
                        }
                        AnimatedContent(
                            targetState = when {
                                on && proxy != null -> listOf(proxy.city, proxy.country).filter { it.isNotBlank() }.joinToString(", ")
                                selected != null -> "${selected.host}:${selected.port}"
                                else -> "Add one in Servers"
                            },
                            transitionSpec = { fadeTransition() }, label = "serverSub",
                        ) { sub -> Text(sub, style = Type.Small, color = C.Muted, maxLines = 1) }
                    }
                    AnimatedVisibility(
                        visible = on,
                        enter = fadeIn(tween(Motion.Short)) + expandHorizontally(tween(Motion.Medium, easing = Motion.Standard)),
                        exit = fadeOut(tween(Motion.Fast)) + shrinkHorizontally(tween(Motion.Short, easing = Motion.Standard)),
                    ) { Icon(Icons.Filled.ChevronRight, "Statistics", tint = C.Muted, modifier = Modifier.size(Sizes.Icon)) }
                }
                Spacer(Modifier.height(Space.Lg))
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { fadeTransition() },
                    label = "homeActions",
                ) { st ->
                    Column {
                        when (st) {
                            Status.Connecting -> {
                                AppButton("Cancel", onToggle, Modifier.fillMaxWidth(), ButtonStyle.Secondary)
                                AnimatedVisibility(slow) {
                                    Column {
                                        Spacer(Modifier.height(Space.Md))
                                        AppButton("Change server", onChangeServer, Modifier.fillMaxWidth(), ButtonStyle.Outline)
                                    }
                                }
                            }
                            Status.Connected -> {
                                AppButton("Disconnect", onToggle, Modifier.fillMaxWidth(), ButtonStyle.Secondary)
                                Spacer(Modifier.height(Space.Md))
                                AppButton("Change server", onChangeServer, Modifier.fillMaxWidth(), ButtonStyle.Outline)
                            }
                            Status.Disconnected -> {
                                AppButton("Connect", onToggle, Modifier.fillMaxWidth(), ButtonStyle.Primary)
                                Spacer(Modifier.height(Space.Md))
                                AppButton("Change server", onChangeServer, Modifier.fillMaxWidth(), ButtonStyle.Outline)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.Lg))
        }
    }
}

@Composable
private fun StatusChip(content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(Radius.Control)).background(C.Scrim).padding(horizontal = Space.Lg, vertical = Space.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun UnprotectedHeader(real: IpInfo?, error: String?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.LockOpen, null, tint = C.Red, modifier = Modifier.size(Sizes.IconHero))
        Spacer(Modifier.height(Space.Lg))
        Text("You are unprotected", style = Type.Headline, color = C.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.Md))
        StatusChip {
            Text(real?.let { r -> r.city.ifBlank { r.country } } ?: "Locating…", style = Type.Body, color = C.Text)
            if (real != null) Text("  ·  ${real.ip}", style = Type.Body, color = C.Muted)
        }
        if (error != null) {
            Spacer(Modifier.height(Space.Sm))
            Text(error, style = Type.Caption, color = C.Red, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ConnectingHeader(real: IpInfo?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(Sizes.IconHero), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp), color = C.OnPrimary, trackColor = C.OnPrimary.copy(alpha = 0.18f),
                strokeWidth = 2.5.dp, strokeCap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.height(Space.Lg))
        Text("Protecting your digital identity", style = Type.Title, color = C.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.Md))
        StatusChip {
            val label = real?.let { r -> listOf(r.city.ifBlank { r.country }, "·", r.ip).joinToString(" ") } ?: "Locating…"
            ScrambleText(label, C.Text, Type.Body)
        }
    }
}

@Composable
private fun ScrambleText(text: String, color: Color, style: TextStyle) {
    fun maskable(ch: Char) = ch != ' ' && ch != '.'
    val order = remember(text) { text.indices.filter { maskable(text[it]) }.shuffled() }
    val prog = remember(text) { Animatable(0f) }
    LaunchedEffect(text) { prog.animateTo(1f, tween(900, easing = LinearEasing)) }
    val hidden = order.take((prog.value * order.size).toInt()).toHashSet()
    val shown = buildString { text.forEachIndexed { i, ch -> append(if (i in hidden) '*' else ch) } }
    Text(shown, style = style, color = color, maxLines = 1)
}

@Composable
private fun ProtectedHeader(time: String, up: Long, down: Long) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, null, tint = C.Green, modifier = Modifier.size(Sizes.IconLg))
            Spacer(Modifier.width(Space.Md))
            Text("Protected", style = Type.Display, color = C.Green)
        }
        Spacer(Modifier.height(Space.Lg))
        AppCard(Modifier.fillMaxWidth(), containerColor = C.CardOverlay) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Connected for", style = Type.Small, color = C.Muted)
                    Text(time, style = Type.Subtitle, color = C.Text)
                }
                Speed(Icons.Filled.ArrowDownward, C.Green, fmtKb(down))
                Spacer(Modifier.width(Space.Lg))
                Speed(Icons.Filled.ArrowUpward, C.Red, fmtKb(up))
            }
        }
    }
}

@Composable
private fun Speed(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(Sizes.IconSm))
        Spacer(Modifier.width(Space.Xs))
        Text(text, style = Type.Small, color = C.Text)
    }
}
