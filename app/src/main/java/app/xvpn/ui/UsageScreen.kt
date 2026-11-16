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
internal fun UsagePage(onBack: () -> Unit) {
    BackHandler(true) { onBack() }
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackBar("Usage & history", onBack)

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Summary("Today", today, Modifier.weight(1f))
            Summary("7 days", week, Modifier.weight(1f))
            Summary("30 days", month, Modifier.weight(1f))
        }

        Section("Last 14 days", first = true)
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(16.dp)) {
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
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(days.first().first.substring(5), color = C.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(if (peak > 0) "peak ${fmtSize(peak)}" else "no data yet", color = C.Muted, fontSize = 12.sp)
            }
        }

        Section("Apps, last 7 days", first = true)
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card)) {
            if (!access) {
                Text(
                    "Android only shares per-app data with apps that have Usage access. Turn it on for this app to see which apps used data while the VPN was connected.",
                    color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp),
                )
                Text(
                    "Open Usage access settings", color = C.Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clickable {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }.padding(16.dp),
                )
            } else {
                val list = apps
                when {
                    list == null -> Text("Loading…", color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                    list.isEmpty() -> Text("No app data for recent sessions.", color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                    else -> list.take(15).forEachIndexed { i, a ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(a.label, color = C.Text, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(fmtSize(a.bytes), color = C.Muted, fontSize = 15.sp)
                        }
                        if (i < minOf(list.size, 15) - 1) Spacer(Modifier.fillMaxWidth().height(1.dp).background(C.CardBorder))
                    }
                }
                Text(
                    "Approximate. Android groups app data in hourly blocks, and this includes apps excluded from the VPN.",
                    color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }

        Section("Connection history", first = true)
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card)) {
            if (sessions.isEmpty()) Text("No connections recorded yet.", color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            sessions.take(30).forEachIndexed { i, s ->
                SessionRow(s)
                if (i < minOf(sessions.size, 30) - 1) Spacer(Modifier.fillMaxWidth().height(1.dp).background(C.CardBorder))
            }
        }

        if (sessions.isNotEmpty() || today.first + today.second + week.first + week.second > 0) {
            Text(
                "Clear history", color = C.Red, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).clip(RoundedCornerShape(Radius.Small))
                    .clickable { Usage.clear(ctx); version++ }.padding(12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Summary(label: String, v: Pair<Long, Long>, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(12.dp)) {
        Text(label, color = C.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text("↓ ${fmtSize(v.second)}", color = C.Green, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text("↑ ${fmtSize(v.first)}", color = C.Red, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun SessionRow(s: Session) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(Flags.clean(s.server).ifBlank { "Server" }, color = C.Text, fontSize = 16.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("${fmt.format(Date(s.start))} · ${fmtDuration(s.end - s.start)}", color = C.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("↓ ${fmtSize(s.down)}  ↑ ${fmtSize(s.up)}", color = C.Muted, fontSize = 13.sp)
        }
    }
}
