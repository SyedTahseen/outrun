package app.xvpn.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.Profile

@Composable
fun ServersScreen(
    profiles: List<Profile>,
    selectedLink: String?,
    onConnect: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onPasteLink: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("All Servers", color = C.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                "+ Paste link", color = C.Accent, fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onPasteLink).padding(8.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No servers yet.\nCopy a vless://, trojan://, ss:// or vmess:// link\nand tap “Paste link”, or add a subscription in Menu.",
                    color = C.Muted, fontSize = 13.sp,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles, key = { it.link }) { p ->
                    val sel = p.link == selectedLink
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Card)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Badge(p.protocol)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = C.Text, fontSize = 14.sp, maxLines = 1)
                            Text("${p.host}:${p.port}", color = C.Muted, fontSize = 11.sp, maxLines = 1)
                        }
                        Text(
                            "Delete", color = C.Red, fontSize = 12.sp,
                            modifier = Modifier.clickable { onDelete(p) }.padding(8.dp),
                        )
                        Text(
                            if (sel) "Selected" else "Connect", color = C.Accent, fontSize = 12.sp,
                            modifier = Modifier.clickable { onConnect(p) }.padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
