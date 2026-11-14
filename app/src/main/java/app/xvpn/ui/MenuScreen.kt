package app.xvpn.ui

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.xvpn.data.Choices
import app.xvpn.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppInfo(val label: String, val pkg: String)

/**
 * Settings. Importing configs / subscriptions lives in the Servers screen; this screen holds the options that change
 * how the VPN behaves. Everything here is stored in [Store] and read by the VPN service when it connects.
 */
@Composable
fun MenuScreen(
    store: Store,
    onExport: () -> Unit,
    onRemoveAll: () -> Unit,
    onDebugInfo: () -> Unit,
) {
    val ctx = LocalContext.current
    var page by remember { mutableStateOf("main") }
    var dialog by remember { mutableStateOf<String?>(null) }

    var splitOn by remember { mutableStateOf(store.splitEnabled) }
    var splitApps by remember { mutableStateOf(store.splitApps) }
    var dns by remember { mutableStateOf(store.dnsChoice) }
    var dnsCustom by remember { mutableStateOf(store.dnsCustom) }
    var testUrl by remember { mutableStateOf(store.testUrlChoice) }
    var autoConnect by remember { mutableStateOf(store.autoConnect) }
    var autoUpdate by remember { mutableStateOf(store.autoUpdateSubs) }

    BackHandler(page != "main") { page = "main" }

    if (page == "apps") {
        AppsPage(
            on = splitOn, selected = splitApps,
            onOn = { splitOn = it; store.splitEnabled = it },
            onSelected = { splitApps = it; store.splitApps = it },
            onBack = { page = "main" },
        )
        return
    }

    val dnsLabel = if (dns == "custom") "Custom" else Choices.dns.firstOrNull { it.id == dns }?.name ?: "Cloudflare"
    val testLabel = Choices.testUrls.firstOrNull { it.id == testUrl }?.name ?: "Google"
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "-" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(20.dp))
        Text("Settings", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(4.dp))

        Section("Features")
        SettingRow(
            Icons.Outlined.AltRoute, "Split tunneling",
            if (splitOn) "On · ${splitApps.size} app${if (splitApps.size == 1) "" else "s"} bypass the VPN" else "Off",
            chevron = true,
        ) { page = "apps" }
        SettingRow(
            Icons.Outlined.Shield, "Kill switch", "Block traffic if the VPN drops (system setting)",
            trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = C.Muted, modifier = Modifier.size(22.dp)) },
        ) {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }

        Section("Connection")
        SettingRow(Icons.Outlined.Dns, "DNS", dnsLabel, chevron = true) { dialog = "dns" }
        SettingRow(Icons.Outlined.Speed, "Latency test", "$testLabel · real delay through the server", chevron = true) { dialog = "test" }
        SettingRow(
            Icons.Outlined.PowerSettingsNew, "Connect on launch", "Connect to the selected server when the app opens",
            trailing = { Toggle(autoConnect) { autoConnect = it; store.autoConnect = it } },
        ) { autoConnect = !autoConnect; store.autoConnect = autoConnect }
        SettingRow(
            Icons.Outlined.Sync, "Auto-update subscriptions", "Refresh on launch, following each subscription's interval",
            trailing = { Toggle(autoUpdate) { autoUpdate = it; store.autoUpdateSubs = it } },
        ) { autoUpdate = !autoUpdate; store.autoUpdateSubs = autoUpdate }

        Section("Servers")
        SettingRow(Icons.Outlined.ContentCopy, "Export servers", "Copy all share links to the clipboard") { onExport() }
        SettingRow(Icons.Outlined.DeleteOutline, "Remove all servers", "Delete every server and subscription link") { dialog = "remove" }

        Section("Support")
        SettingRow(Icons.Outlined.BugReport, "Copy debug info", "App version, device and the last connection error") { onDebugInfo() }
        SettingRow(Icons.Outlined.Info, "Version", version) {}
        Spacer(Modifier.height(24.dp))
    }

    when (dialog) {
        "dns" -> SheetDialog("DNS", { dialog = null }) {
            Choices.dns.forEach { o -> ChoiceRow(o.name, o.note, dns == o.id) { dns = o.id; store.dnsChoice = o.id; dialog = null } }
            ChoiceRow("Custom", if (dnsCustom.isBlank()) "Your own IPv4 DNS servers" else dnsCustom, dns == "custom") { dialog = "dns_custom" }
            Hint("Applies the next time you connect.")
        }
        "dns_custom" -> TextDialog(
            "Custom DNS", "e.g. 9.9.9.9, 149.112.112.112", dnsCustom, "Enter one or more IPv4 addresses.",
            validate = { Choices.parseDns(it).isNotEmpty() },
            onSave = { dnsCustom = it.trim(); store.dnsCustom = dnsCustom; dns = "custom"; store.dnsChoice = "custom"; dialog = null },
            onDismiss = { dialog = null },
        )
        "test" -> SheetDialog("Latency test", { dialog = null }) {
            Choices.testUrls.forEach { o -> ChoiceRow(o.name, o.url, testUrl == o.id) { testUrl = o.id; store.testUrlChoice = o.id; dialog = null } }
            Hint("The other servers are used as fallback if this one is unreachable.")
        }
        "remove" -> SheetDialog("Remove all servers?", { dialog = null }) {
            Text("This deletes every server and subscription link from the app. It can't be undone.", color = C.Muted, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                Text("Cancel", color = C.Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { dialog = null }.padding(12.dp))
                Spacer(Modifier.width(8.dp))
                Text("Remove all", color = C.Red, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { dialog = null; onRemoveAll() }.padding(12.dp))
            }
        }
    }
}

@Composable
private fun Section(label: String) {
    Text(label, color = C.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 4.dp))
}

@Composable
private fun SettingRow(
    icon: ImageVector, title: String, subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null, chevron: Boolean = false, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.Text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = C.Muted, fontSize = 14.sp, maxLines = 2)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
        else if (chevron) Icon(Icons.Filled.ChevronRight, null, tint = C.Muted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked, onCheckedChange = onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = C.Accent, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = C.Muted, uncheckedTrackColor = C.ButtonGrey, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SheetDialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(20.dp)) {
            Text(title, color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ChoiceRow(label: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Small)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).border(2.dp, if (selected) C.Accent else C.Muted, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(C.Accent))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = C.Text, fontSize = 16.sp)
            Text(note, color = C.Muted, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = C.Muted, fontSize = 12.sp)
}

@Composable
private fun TextDialog(
    title: String, placeholder: String, initial: String, help: String,
    validate: (String) -> Boolean, onSave: (String) -> Unit, onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    SheetDialog(title, onDismiss) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Small)).background(C.Bg).padding(14.dp)) {
            BasicTextField(
                value = text, onValueChange = { text = it; error = false }, singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 15.sp), cursorBrush = SolidColor(C.Accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> if (text.isEmpty()) Text(placeholder, color = C.Muted, fontSize = 15.sp); inner() },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(if (error) "Not a valid IPv4 address list." else help, color = if (error) C.Red else C.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            Text("Cancel", color = C.Muted, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Save", color = C.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { if (validate(text)) onSave(text) else error = true }.padding(12.dp),
            )
        }
    }
}

/** Split tunneling: pick the apps that bypass the VPN. */
@Composable
private fun AppsPage(
    on: Boolean, selected: Set<String>,
    onOn: (Boolean) -> Unit, onSelected: (Set<String>) -> Unit, onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    var query by remember { mutableStateOf("") }
    val initial = remember { selected }      // selected apps are listed first, in the order the page was opened
    val apps by produceState<List<AppInfo>?>(null) {
        value = withContext(Dispatchers.Default) {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, 0)
                .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .distinctBy { it.pkg }
                .filter { it.pkg != ctx.packageName }
                .sortedWith(compareByDescending<AppInfo> { it.pkg in initial }.thenBy { it.label.lowercase() })
        }
    }
    val shown = apps?.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
            Spacer(Modifier.width(6.dp))
            Text("Split tunneling", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        SettingRow(
            Icons.Outlined.AltRoute, "Bypass the VPN for selected apps", "Selected apps connect directly. Applies the next time you connect.",
            trailing = { Toggle(on, onOn) },
        ) { onOn(!on) }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).clip(RoundedCornerShape(Radius.Small)).background(C.Card).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = C.Muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = C.Text, fontSize = 15.sp), cursorBrush = SolidColor(C.Accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> if (query.isEmpty()) Text("Search apps", color = C.Muted, fontSize = 15.sp); inner() },
            )
        }
        if (shown == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Loading apps…", color = C.Muted, fontSize = 14.sp) }
        } else if (shown.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("No apps found", color = C.Muted, fontSize = 14.sp) }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(shown, key = { it.pkg }) { app ->
                    val checked = app.pkg in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelected(if (checked) selected - app.pkg else selected + app.pkg) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app.pkg)
                        Spacer(Modifier.width(16.dp))
                        Text(app.label, color = C.Text, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Toggle(checked) { onSelected(if (it) selected + app.pkg else selected - app.pkg) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val pm = LocalContext.current.packageManager
    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val d = pm.getApplicationIcon(pkg)
                val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                d.setBounds(0, 0, 96, 96)
                d.draw(canvas)
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
    val m = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))
    val i = icon
    if (i != null) Image(i, null, m) else Box(m.background(C.Card))
}
