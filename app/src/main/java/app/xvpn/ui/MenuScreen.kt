package app.xvpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MenuScreen(
    subscriptions: List<String>,
    message: String?,
    onAddSubscription: (String) -> Unit,
    onRefreshSubscriptions: () -> Unit,
    onImportClipboard: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Menu", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))

        // gradient card (replaces the "Premium" card in the mockup)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF14121F), Color(0xFF3A2A7A), Color(0xFF8B6CF6))))
                .padding(16.dp),
        ) {
            Text("Import Configs", color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("• vless, trojan, shadowsocks, vmess", color = C.Text, fontSize = 12.sp)
            Text("• Reality, TLS, WS, gRPC, xhttp", color = C.Text, fontSize = 12.sp)
            Text("• Subscription URLs", color = C.Text, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Small)).background(Color(0x33FFFFFF))
                    .clickable(onClick = onImportClipboard).padding(12.dp),
            ) {
                Text("Import from clipboard", color = C.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("›", color = C.Text)
            }
        }

        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(16.dp)) {
            Text("Subscription URL", color = C.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Small)).background(C.Bg).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    textStyle = TextStyle(color = C.Text, fontSize = 13.sp),
                    cursorBrush = SolidColor(C.Accent),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (url.isEmpty()) Text("https://…", color = C.Muted, fontSize = 13.sp)
                        inner()
                    },
                )
                Text(
                    "Add", color = C.Accent, fontSize = 13.sp,
                    modifier = Modifier.clickable { if (url.isNotBlank()) { onAddSubscription(url.trim()); url = "" } }.padding(start = 12.dp),
                )
            }
            if (subscriptions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                subscriptions.forEach { Text(it, color = C.Muted, fontSize = 11.sp, maxLines = 1) }
                Spacer(Modifier.height(10.dp))
                Text("Refresh all", color = C.Accent, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onRefreshSubscriptions))
            }
        }
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message, color = C.Muted, fontSize = 12.sp)
        }
    }
}
