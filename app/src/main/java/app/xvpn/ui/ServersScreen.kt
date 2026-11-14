package app.xvpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.Profile

private fun protoLabel(p: String) = when (p) {
    "vless" -> "VLESS"; "trojan" -> "Trojan"; "ss" -> "Shadowsocks"; "vmess" -> "VMess"; else -> p.uppercase()
}

@Composable
fun ServersScreen(
    profiles: List<Profile>,
    selectedLink: String?,
    onConnect: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onPasteClipboard: () -> Unit,
    onScanQr: () -> Unit,
    onImportFile: () -> Unit,
    onAddManual: (String) -> Boolean,   // returns true if the link was valid and added
    onRefreshSubscriptions: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    val protos = profiles.map { it.protocol }.distinct()
    val f = filter?.takeIf { it in protos }
    val shown = profiles.filter {
        (f == null || it.protocol == f) &&
            (query.isBlank() || it.name.contains(query, true) || it.host.contains(query, true))
    }

    Column(Modifier.fillMaxSize()) {
        // header
        Column(Modifier.fillMaxWidth().background(C.Card)) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (searching) {
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = C.Text, fontSize = 18.sp),
                        cursorBrush = SolidColor(C.Accent),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search servers", color = C.Muted, fontSize = 18.sp)
                            inner()
                        },
                    )
                    HeaderIcon(Icons.Filled.Close, "Close search") { searching = false; query = "" }
                } else {
                    Text("Servers", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    HeaderIcon(Icons.Filled.Search, "Search") { searching = true }
                    Box {
                        HeaderIcon(Icons.Filled.Add, "Add") { addMenu = true }
                        DropdownMenu(addMenu, { addMenu = false }, shape = RoundedCornerShape(20.dp), containerColor = C.Card) {
                            MenuRow(Icons.Outlined.QrCodeScanner, "Scan QR code") { addMenu = false; onScanQr() }
                            MenuRow(Icons.Outlined.ContentPaste, "Paste from clipboard") { addMenu = false; onPasteClipboard() }
                            MenuRow(Icons.Outlined.InsertDriveFile, "Import from file") { addMenu = false; onImportFile() }
                            MenuRow(Icons.Outlined.Edit, "Add manually") { addMenu = false; manual = true }
                        }
                    }
                    Box {
                        HeaderIcon(Icons.Filled.MoreVert, "More") { moreMenu = true }
                        DropdownMenu(moreMenu, { moreMenu = false }, shape = RoundedCornerShape(20.dp), containerColor = C.Card) {
                            MenuRow(Icons.Outlined.Refresh, "Update subscriptions") { moreMenu = false; onRefreshSubscriptions() }
                            MenuRow(Icons.Outlined.DeleteOutline, "Delete all servers") { moreMenu = false; onDeleteAll() }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            // protocol filter chips
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Chip("All", f == null) { filter = null }
                protos.forEach { p -> Chip(protoLabel(p), f == p) { filter = p } }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (profiles.isEmpty()) "No servers yet.\nTap + to scan a QR code, paste a link,\nimport a file or add one manually."
                    else "No servers match.",
                    color = C.Muted, fontSize = 14.sp,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.link }) { p ->
                    ServerRow(p, p.link == selectedLink, { onConnect(p) }, { onDelete(p) })
                }
            }
        }
    }

    if (manual) ManualDialog(onDismiss = { manual = false }, onAdd = { onAddManual(it).also { ok -> if (ok) manual = false } })
}

@Composable
private fun HeaderIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = C.Text, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = C.Text, fontSize = 17.sp) },
        leadingIcon = { Icon(icon, null, tint = C.Text, modifier = Modifier.size(22.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) C.Button else C.ButtonGrey).clickable(onClick = onClick).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun ServerRow(p: Profile, selected: Boolean, onConnect: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(C.Bg)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onConnect).padding(start = 20.dp, end = 8.dp).height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Badge(p.protocol)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, color = if (selected) C.Green else C.Text, fontSize = 17.sp, maxLines = 1)
                Text(if (selected) "Selected · ${p.host}:${p.port}" else "${p.host}:${p.port}", color = C.Muted, fontSize = 13.sp, maxLines = 1)
            }
            Box {
                HeaderIcon(Icons.Filled.MoreVert, "Options") { menu = true }
                DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(20.dp), containerColor = C.Card) {
                    MenuRow(Icons.Outlined.PlayArrow, "Connect") { menu = false; onConnect() }
                    MenuRow(Icons.Outlined.DeleteOutline, "Delete") { menu = false; onDelete() }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.CardBorder))
    }
}

@Composable
private fun ManualDialog(onDismiss: () -> Unit, onAdd: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Add manually", color = C.Text, fontSize = 20.sp) },
        text = {
            Column {
                Text("Paste a vless://, trojan://, ss:// or vmess:// link (one per line).", color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = text, onValueChange = { text = it; bad = false },
                    textStyle = TextStyle(color = C.Text, fontSize = 13.sp),
                    cursorBrush = SolidColor(C.Accent),
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp)).background(C.Bg).padding(12.dp),
                )
                if (bad) Text("No valid link found", color = C.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton({ if (!onAdd(text)) bad = true }) { Text("Add", color = C.Accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}
