package app.xvpn.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.xvpn.data.AppUsage
import app.xvpn.data.Session
import app.xvpn.data.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun UsagePage(onDeepChange: (Boolean) -> Unit = {}, onBack: () -> Unit) {
    BackHandler(true) { onBack() }
    // Like Statistics (Proton's connection details), this page sits on backgroundDeep; tell the shell so the
    // status strip and the area behind the navigation bar follow.
    DisposableEffect(Unit) { onDeepChange(true); onDispose { onDeepChange(false) } }
    val ctx = LocalContext.current
    var version by remember { mutableIntStateOf(0) }

    val activity = ctx as? ComponentActivity
    DisposableEffect(activity) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) version++ }
        activity?.lifecycle?.addObserver(obs)
        onDispose { activity?.lifecycle?.removeObserver(obs) }
    }

    val days = remember(version) { Usage.lastDays(ctx, 14) }
    val sessions = remember(version) { Usage.sessions(ctx) }
    val today = remember(version) { Usage.total(ctx, 1) }
    val week = remember(version) { Usage.total(ctx, 7) }
    val month = remember(version) { Usage.total(ctx, 30) }
    val access = remember(version) { Usage.hasUsageAccess(ctx) }

    val apps by produceState<List<AppUsage>?>(null, version, access) {
        value = if (!access) emptyList() else withContext(Dispatchers.IO) {
            val since = System.currentTimeMillis() - 7L * 86_400_000L
            Usage.perApp(ctx, Usage.sessions(ctx).filter { it.end >= since })
        }
    }

    Column(Modifier.fillMaxSize().background(C.Deep)) {
        ScreenHeader("Usage & history", onBack = onBack, base = C.Deep)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.Gutter)) {
            Spacer(Modifier.height(Space.Lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.Sm),
            ) {
                Summary("Today", today, Modifier.weight(1f))
                Summary("7 days", week, Modifier.weight(1f))
                Summary("30 days", month, Modifier.weight(1f))
            }

            StatsLabel("Last 14 days")
            AppCard(Modifier.fillMaxWidth(), containerColor = C.Bg, radius = Radius.Control) {
                val peak = days.maxOf { it.second + it.third }
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val n = days.size
                    val gap = 6.dp.toPx()
                    val bw = (size.width - gap * (n - 1)) / n
                    days.forEachIndexed { i, d ->
                        val total = d.second + d.third
                        val h = if (peak == 0L) 2.dp.toPx() else (total.toFloat() / peak * size.height).coerceAtLeast(2.dp.toPx())
                        drawRoundRect(
                            if (i == n - 1) C.Accent else C.AccentMid,
                            Offset(i * (bw + gap), size.height - h), Size(bw, h), CornerRadius(3.dp.toPx()),
                        )
                    }
                }
                Spacer(Modifier.height(Space.Sm))
                Row(Modifier.fillMaxWidth()) {
                    Text(days.first().first.substring(5), style = Type.Caption, color = C.Muted, modifier = Modifier.weight(1f))
                    Text(if (peak > 0) "peak ${fmtSize(peak)}" else "no data yet", style = Type.Caption, color = C.Muted)
                }
            }

            StatsLabel("Apps, last 7 days")
            AppCard(Modifier.fillMaxWidth(), padding = 0.dp, containerColor = C.Bg, radius = Radius.Control) {
                if (!access) {
                    Text(
                        "Android only shares per-app data with apps that have Usage access. Turn it on for this app to see which apps used data while the VPN was connected.",
                        style = Type.Small, color = C.Muted, modifier = Modifier.padding(Space.Lg),
                    )
                    HairlineDivider()
                    CardAction("Open Usage access settings", {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    })
                } else {
                    val list = apps
                    when {
                        list == null -> Text("Loading…", style = Type.Small, color = C.Muted, modifier = Modifier.padding(Space.Lg))
                        list.isEmpty() -> Text("No app data for recent sessions.", style = Type.Small, color = C.Muted, modifier = Modifier.padding(Space.Lg))
                        else -> list.take(15).forEachIndexed { i, a ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = Space.Lg, vertical = Space.Lg), verticalAlignment = Alignment.CenterVertically) {
                                Text(a.label, style = Type.Body, color = C.Text, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(fmtSize(a.bytes), style = Type.Small, color = C.Muted)
                            }
                            if (i < minOf(list.size, 15) - 1) HairlineDivider()
                        }
                    }
                    Text(
                        "Approximate. Android groups app data in hourly blocks, and this includes apps excluded from the VPN.",
                        style = Type.Caption, color = C.Muted, modifier = Modifier.padding(start = Space.Lg, end = Space.Lg, bottom = Space.Md),
                    )
                }
            }

            StatsLabel("Connection history")
            AppCard(Modifier.fillMaxWidth(), padding = 0.dp, containerColor = C.Bg, radius = Radius.Control) {
                if (sessions.isEmpty()) Text("No connections recorded yet.", style = Type.Small, color = C.Muted, modifier = Modifier.padding(Space.Lg))
                sessions.take(30).forEachIndexed { i, s ->
                    SessionRow(s)
                    if (i < minOf(sessions.size, 30) - 1) HairlineDivider()
                }
            }

            if (sessions.isNotEmpty() || today.first + today.second + week.first + week.second > 0) {
                TextAction(
                    "Clear history", { Usage.clear(ctx); version++ },
                    Modifier.padding(vertical = Space.Sm), color = C.Red,
                )
            }
            Spacer(Modifier.height(Space.Xxl))
        }
    }
}

@Composable
private fun Summary(label: String, v: Pair<Long, Long>, modifier: Modifier) {
    AppCard(modifier, padding = Space.Md, containerColor = C.Bg, radius = Radius.Control) {
        Text(label, style = Type.Small, color = C.Muted)
        Spacer(Modifier.height(Space.Xs))
        Text("↓ ${fmtSize(v.second)}", style = Type.SmallMedium, color = C.SuccessSolid, maxLines = 1)
        Text("↑ ${fmtSize(v.first)}", style = Type.SmallMedium, color = C.Red, maxLines = 1)
    }
}

@Composable
private fun SessionRow(s: Session) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.Lg, vertical = Space.Lg)) {
        Text(Flags.clean(s.server).ifBlank { "Server" }, style = Type.Body, color = C.Text, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("${fmt.format(Date(s.start))} · ${fmtDuration(s.end - s.start)}", style = Type.Caption, color = C.Muted, modifier = Modifier.weight(1f))
            Text("↓ ${fmtSize(s.down)}  ↑ ${fmtSize(s.up)}", style = Type.Caption, color = C.Muted)
        }
    }
}
