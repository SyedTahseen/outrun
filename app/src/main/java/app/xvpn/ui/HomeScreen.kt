package app.xvpn.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.Profile
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import kotlinx.coroutines.delay

private fun fmtSpeed(b: Long): String = when {
    b >= 1_000_000 -> "%.1f MB/s".format(b / 1_000_000.0)
    b >= 1_000 -> "%.0f KB/s".format(b / 1_000.0)
    else -> "$b B/s"
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

@Composable
fun HomeScreen(selected: Profile?, onToggle: () -> Unit, onChangeServer: () -> Unit) {
    val status by VpnState.status.collectAsState()
    val connectedAt by VpnState.connectedAt.collectAsState()
    val up by VpnState.upBytesPerSec.collectAsState()
    val down by VpnState.downBytesPerSec.collectAsState()
    val error by VpnState.error.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val on = status == Status.Connected

    Box(Modifier.fillMaxSize()) {
        // dotted "world map" backdrop
        Canvas(Modifier.fillMaxSize()) {
            val step = 14.dp.toPx()
            var y = size.height * 0.18f
            while (y < size.height * 0.72f) {
                var x = 0f
                while (x < size.width) {
                    drawCircle(Color(0x14FFFFFF), 1.6.dp.toPx(), Offset(x, y)); x += step
                }
                y += step
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            // original wordmark glyph: three offset bars
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.width(34.dp).height(5.dp).background(Color.White, RoundedCornerShape(2.dp)))
                Box(Modifier.padding(start = 8.dp).width(34.dp).height(5.dp).background(Color.White, RoundedCornerShape(2.dp)))
                Box(Modifier.width(34.dp).height(5.dp).background(Color.White, RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(44.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (on) Icon(Icons.Filled.VerifiedUser, null, tint = C.Green, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when (status) {
                        Status.Connected -> "Connection Secure"
                        Status.Connecting -> "Connecting…"
                        Status.Disconnected -> "Not Connected"
                    },
                    color = C.Text, fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (on) fmtTime(now - connectedAt) else "00:00:00",
                color = C.Text, fontSize = 40.sp, fontWeight = FontWeight.Medium,
            )
            if (error != null) Text(error!!, color = C.Red, fontSize = 12.sp)

            Spacer(Modifier.height(36.dp))
            PowerButton(on, onToggle)
            Spacer(Modifier.weight(1f, fill = true))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeedCard("Upload Speed", fmtSpeed(up), Icons.Filled.ArrowUpward, Modifier.weight(1f))
                SpeedCard("Download Speed", fmtSpeed(down), Icons.Filled.ArrowDownward, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            ServerBar(selected, onChangeServer)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PowerButton(on: Boolean, onClick: () -> Unit) {
    val ring = if (on) C.Accent.copy(alpha = 0.35f) else C.AccentDeep
    Box(
        Modifier.size(230.dp).clip(CircleShape).background(ring.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(180.dp).clip(CircleShape).background(C.AccentMid.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(124.dp).clip(CircleShape).background(Color(0xFF0C0A1F)).clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PowerSettingsNew, "Toggle VPN",
                    tint = if (on) C.Green else C.Accent, modifier = Modifier.size(52.dp),
                )
            }
        }
    }
}

@Composable
private fun SpeedCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(C.Card).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = C.Muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = C.Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ServerBar(p: Profile?, onChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card)
            .clickable(onClick = onChange).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Badge(p?.protocol ?: "—")
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p?.name ?: "No server selected", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(p?.let { "${it.host}:${it.port}" } ?: "Add one in Servers", color = C.Muted, fontSize = 11.sp, maxLines = 1)
        }
        Text("Change  ›", color = C.Muted, fontSize = 12.sp)
    }
}

/** Protocol pill used in place of the country flag. */
@Composable
fun Badge(protocol: String) {
    Box(
        Modifier.size(width = 44.dp, height = 30.dp).clip(RoundedCornerShape(6.dp)).background(C.AccentMid),
        contentAlignment = Alignment.Center,
    ) {
        Text(protocol.uppercase(), color = C.Text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
