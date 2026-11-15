package app.xvpn.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
/** Settings sub-pages form a small stack: main (0) -> feature pages (1) -> pages opened from those (2). */
private fun pageDepth(page: String) = when (page) {
    "main" -> 0
    "mode", "apps", "ips", "dns_custom" -> 2
    else -> 1
}

@Composable
fun MenuScreen(
    store: Store,
    onExport: () -> Unit,
    onDebugInfo: () -> Unit,
    onScrolledChange: (Boolean) -> Unit = {},
) {
    var page by remember { mutableStateOf("main") }
    val scroll = rememberScrollState()   // hoisted so the position survives opening a sub page
    // Opening a page slides it in from the right, going back slides it out to the right, always with a short fade.
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            // opening a page and going back are mirror images (scale + fade, see openCloseTransition)
            openCloseTransition(forward = pageDepth(targetState) >= pageDepth(initialState))
                .using(SizeTransform(clip = false))
        },
        label = "settingsPage",
    ) { p ->
        MenuPage(p, { page = it }, scroll, store, onExport, onDebugInfo, onScrolledChange)
    }
}

@Composable
private fun MenuPage(
    page: String,
    setPage: (String) -> Unit,
    scroll: androidx.compose.foundation.ScrollState,
    store: Store,
    onExport: () -> Unit,
    onDebugInfo: () -> Unit,
    onScrolledChange: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    var splitOn by remember { mutableStateOf(store.splitEnabled) }
    var splitApps by remember { mutableStateOf(store.splitApps) }
    var dns by remember { mutableStateOf(store.dnsChoice) }
    var dnsCustom by remember { mutableStateOf(store.dnsCustom) }
    var testUrl by remember { mutableStateOf(store.testUrlChoice) }
    var autoConnect by remember { mutableStateOf(store.autoConnect) }
    var autoUpdate by remember { mutableStateOf(store.autoUpdateSubs) }

    var splitMode by remember { mutableStateOf(store.splitMode) }
    var excApps by remember { mutableStateOf(store.splitApps) }
    var incApps by remember { mutableStateOf(store.splitIncludeApps) }
    var excIps by remember { mutableStateOf(store.splitExcludeIps) }
    var incIps by remember { mutableStateOf(store.splitIncludeIps) }
    val including = splitMode == "include"

    BackHandler(page == "split") { setPage("main") }

    when (page) {
        "split" -> {
            SplitPage(
                on = splitOn, including = including,
                apps = if (including) incApps else excApps, ipCount = (if (including) incIps else excIps).size,
                onOn = { splitOn = it; store.splitEnabled = it },
                onMode = { setPage("mode") },
                onApps = { setPage("apps") }, onIps = { setPage("ips") },
                onBack = { setPage("main") },
            )
            return
        }
        "mode" -> {
            SubPage(null, { setPage("split") }) {
                Hero(Icons.Outlined.Tune)
                PageHeading("Mode", "Choose how split tunneling treats the apps and IP addresses you select. Applies the next time you connect.")
                Spacer(Modifier.height(8.dp))
                ChoiceList {
                    ChoiceRow("Exclude", "Selected apps and IP addresses are excluded from the VPN connection.", !including) {
                        splitMode = "exclude"; store.splitMode = "exclude"; setPage("split")
                    }
                    ChoiceRow("Include", "Only selected apps and IP addresses connect through the VPN, all other traffic is unprotected.", including) {
                        splitMode = "include"; store.splitMode = "include"; setPage("split")
                    }
                }
            }
            return
        }
        "dns" -> {
            SubPage(null, { setPage("main") }) {
                Hero(Icons.Outlined.Dns)
                PageHeading("DNS", "Choose the DNS servers used while connected. Applies the next time you connect.")
                Spacer(Modifier.height(8.dp))
                ChoiceList {
                    Choices.dns.forEach { o -> ChoiceRow(o.name, o.note, dns == o.id) { dns = o.id; store.dnsChoice = o.id; setPage("main") } }
                    ChoiceRow("Custom", if (dnsCustom.isBlank()) "Your own IPv4 DNS servers" else dnsCustom, dns == "custom") { setPage("dns_custom") }
                }
            }
            return
        }
        "dns_custom" -> {
            var text by remember { mutableStateOf(dnsCustom) }
            var error by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize()) {
                EditorBar("Custom DNS", { setPage("dns") }) {
                    if (Choices.parseDns(text).isNotEmpty()) {
                        dnsCustom = text.trim(); store.dnsCustom = dnsCustom
                        dns = "custom"; store.dnsChoice = "custom"; setPage("main")
                    } else error = true
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("DNS servers", color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Small)).background(C.Card).padding(14.dp)) {
                        BasicTextField(
                            value = text, onValueChange = { text = it; error = false }, singleLine = true,
                            textStyle = TextStyle(color = C.Text, fontSize = 16.sp), cursorBrush = SolidColor(C.Accent),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner -> if (text.isEmpty()) Text("e.g. 9.9.9.9, 149.112.112.112", color = C.Muted, fontSize = 16.sp); inner() },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (error) "Not a valid IPv4 address list." else "Enter one or more IPv4 addresses, separated by commas.",
                        color = if (error) C.Red else C.Muted, fontSize = 12.sp,
                    )
                }
            }
            return
        }
        "test" -> {
            SubPage(null, { setPage("main") }) {
                Hero(Icons.Outlined.Speed)
                PageHeading("Latency test", "Each server is tested with a real request to this address, sent through the server. The time covers connecting to the server, the secure connection and the response.")
                Spacer(Modifier.height(8.dp))
                ChoiceList {
                    Choices.testUrls.forEach { o -> ChoiceRow(o.name, o.url, testUrl == o.id) { testUrl = o.id; store.testUrlChoice = o.id; setPage("main") } }
                }
            }
            return
        }
        "connect" -> {
            SubPage(null, { setPage("main") }) {
                Hero(Icons.Outlined.PowerSettingsNew)
                PageHeading("Connect on launch", "Connect to the selected server automatically when the app opens.")
                ToggleCard("Connect on launch", autoConnect) { autoConnect = it; store.autoConnect = it }
            }
            return
        }
        "autoupdate" -> {
            SubPage(null, { setPage("main") }) {
                Hero(Icons.Outlined.Sync)
                PageHeading("Auto-update subscriptions", "Refresh your subscriptions when the app opens, following each subscription's update interval.")
                ToggleCard("Auto-update subscriptions", autoUpdate) { autoUpdate = it; store.autoUpdateSubs = it }
            }
            return
        }
        "kill" -> {
            val bold = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
            SubPage(
                "Kill switch", { setPage("main") },
                bottom = {
                    ActionButton("Settings", filled = true, trailing = Icons.AutoMirrored.Outlined.OpenInNew) {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    ActionButton("What is kill switch?", filled = false, trailing = Icons.AutoMirrored.Outlined.OpenInNew) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(KILL_SWITCH_HELP_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                },
            ) {
                Hero(Icons.Outlined.Shield, size = 96.dp, centered = true, tint = C.Green)
                Spacer(Modifier.height(24.dp))
                Text("How to enable kill switch", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(14.dp))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("1. Open your device settings", color = C.Text, fontSize = 17.sp)
                    Text("2. Find SingBox VPN and tap the gear icon", color = C.Text, fontSize = 17.sp)
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("3. Enable \""); pushStyle(bold); append("Always-on VPN"); pop(); append("\" and \"")
                            pushStyle(bold); append("Block connections without VPN"); pop(); append("\"")
                        },
                        color = C.Text, fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, null, tint = C.Muted, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("When kill switch is on, you won't be able to connect to:", color = C.Muted, fontSize = 15.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("•  Websites and apps excluded from your VPN connection through split tunneling", color = C.Muted, fontSize = 15.sp)
                            Text("•  Devices on your local area network", color = C.Muted, fontSize = 15.sp)
                        }
                    }
                }
            }
            return
        }
        "apps" -> {
            AppPickerPage(
                title = if (including) "Included apps" else "Excluded apps",
                listTitle = if (including) "Included apps" else "Excluded apps",
                note = if (including) "Only these apps connect through the VPN." else "These apps connect directly, bypassing the VPN.",
                initial = if (including) incApps else excApps,
                onSave = { if (including) { incApps = it; store.splitIncludeApps = it } else { excApps = it; store.splitApps = it } },
                onBack = { setPage("split") },
            )
            return
        }
        "ips" -> {
            IpPage(
                title = if (including) "Included IP addresses" else "Excluded IP addresses",
                initial = if (including) incIps else excIps,
                onSave = { if (including) { incIps = it; store.splitIncludeIps = it } else { excIps = it; store.splitExcludeIps = it } },
                onBack = { setPage("split") },
            )
            return
        }
    }

    val dnsLabel = if (dns == "custom") "Custom" else Choices.dns.firstOrNull { it.id == dns }?.name ?: "Cloudflare"
    val testLabel = Choices.testUrls.firstOrNull { it.id == testUrl }?.name ?: "Google"
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "-" }

    // The title stays pinned; its bar switches from the background colour to the bottom-bar colour once the list scrolls
    // (same behaviour as the Servers header, and the status bar strip follows it).
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    val headerColor by animateColorAsState(if (scrolled) C.Card else C.Bg, label = "settingsHeader")
    LaunchedEffect(scrolled) { onScrolledChange(scrolled) }
    DisposableEffect(Unit) { onDispose { onScrolledChange(false) } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(headerColor)) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Section("Features", first = true)
            SettingRow(
                Icons.Outlined.AltRoute, "Split tunneling",
                if (!splitOn) "Off" else "On",
            ) { setPage("split") }
            SettingRow(Icons.Outlined.Shield, "Kill switch") { setPage("kill") }

            Section("Connection")
            SettingRow(Icons.Outlined.Dns, "DNS", dnsLabel) { setPage("dns") }
            SettingRow(Icons.Outlined.Speed, "Latency test", testLabel) { setPage("test") }
            SettingRow(Icons.Outlined.PowerSettingsNew, "Connect on launch", if (autoConnect) "On" else "Off") { setPage("connect") }
            SettingRow(Icons.Outlined.Sync, "Auto-update subscriptions", if (autoUpdate) "On" else "Off") { setPage("autoupdate") }

            Section("Servers")
            SettingRow(Icons.Outlined.ContentCopy, "Export servers") { onExport() }

            Section("Support")
            SettingRow(Icons.Outlined.BugReport, "Copy debug info") { onDebugInfo() }

            Spacer(Modifier.height(28.dp))
            Text(
                "App version: $version", color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Section(label: String, first: Boolean = false) {
    Text(
        label, color = C.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = if (first) 12.dp else 28.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector, title: String, subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null, chevron: Boolean = false, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.Text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Text, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = C.Muted, fontSize = 16.sp, maxLines = 2)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
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
        val ring by animateColorAsState(if (selected) C.Accent else C.Muted, tween(Motion.Medium), label = "radioRing")
        val dot by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "radioDot")
        Box(Modifier.size(22.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(10.dp).graphicsLayer { scaleX = dot; scaleY = dot }.clip(CircleShape).background(C.Accent))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = C.Text, fontSize = 16.sp)
            Text(note, color = C.Muted, fontSize = 13.sp, maxLines = 4)
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

private val AppRowIcon = 32.dp

/** Split tunneling landing page: toggle, mode, and the apps / IP lists for the current mode. */
@Composable
private fun SplitPage(
    on: Boolean, including: Boolean, apps: Set<String>, ipCount: Int,
    onOn: (Boolean) -> Unit, onMode: () -> Unit, onApps: () -> Unit, onIps: () -> Unit, onBack: () -> Unit,
) {
    val pm = LocalContext.current.packageManager
    val names = remember(apps) {
        apps.mapNotNull { runCatching { pm.getApplicationInfo(it, 0).loadLabel(pm).toString() }.getOrNull() }.sortedBy { it.lowercase() }
    }
    val appsText = when {
        names.isEmpty() -> "None"
        names.size <= 2 -> names.joinToString(", ")
        else -> "${names.take(2).joinToString(", ")} +${names.size - 2}"
    }
    val word = if (including) "Included" else "Excluded"
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(Radius.Card))
                    .background(Brush.linearGradient(listOf(C.AccentMid, C.AccentDeep))),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.AltRoute, null, tint = C.Accent, modifier = Modifier.size(36.dp)) }
            Spacer(Modifier.height(20.dp))
            Text("Split tunneling", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Customize your connection by deciding which apps and IP addresses are protected by the VPN.", color = C.Muted, fontSize = 15.sp)
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card)
                    .clickable { onOn(!on) }.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Split tunneling", color = C.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Toggle(on, onOn)
            }
        }
        if (on) {
            Spacer(Modifier.height(8.dp))
            SettingRow(Icons.Outlined.Tune, "Mode", if (including) "Include" else "Exclude", chevron = true) { onMode() }
            SettingRow(Icons.Outlined.PhoneAndroid, "$word apps", appsText, chevron = true) { onApps() }
            SettingRow(
                Icons.Outlined.Terminal, "$word IP addresses",
                if (ipCount == 0) "None" else "$ipCount address${if (ipCount == 1) "" else "es"}", chevron = true,
            ) { onIps() }
            Spacer(Modifier.height(8.dp))
            Text(
                "Applies the next time you connect." + if (!including) " Excluding IP addresses needs Android 13 or newer." else "",
                color = C.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Top bar shared by the two editor pages: back arrow, title, Save. */
@Composable
private fun EditorBar(title: String, onBack: () -> Unit, onSave: () -> Unit) {
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
        Spacer(Modifier.width(6.dp))
        Text(title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        Text("Save", color = C.Accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(Radius.Small)).clickable(onClick = onSave).padding(12.dp))
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    SheetDialog("Discard unsaved changes?", onKeep) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text("Discard changes", color = C.Accent, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onDiscard).padding(12.dp))
            Text("Keep editing", color = C.Accent, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onKeep).padding(12.dp))
        }
    }
}

@Composable
private fun ListHeader(title: String, note: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)) {
        Text(title, color = C.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (note != null) Text(note, color = C.Muted, fontSize = 14.sp)
    }
}

@Composable
private fun RoundAction(add: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(
            if (add) Icons.Outlined.AddCircleOutline else Icons.Outlined.RemoveCircleOutline,
            if (add) "Add" else "Remove", tint = if (add) C.Text else C.Muted, modifier = Modifier.size(26.dp),
        )
    }
}

/** Pick apps for split tunneling. Edits are kept locally until Save; leaving with changes asks first. */
@Composable
private fun AppPickerPage(
    title: String, listTitle: String, note: String, initial: Set<String>,
    onSave: (Set<String>) -> Unit, onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    var sel by remember { mutableStateOf(initial) }
    var confirm by remember { mutableStateOf(false) }
    val dirty = sel != initial
    val leave: () -> Unit = { if (dirty) confirm = true else onBack() }
    BackHandler(true) { leave() }

    val apps by produceState<List<AppInfo>?>(null) {
        value = withContext(Dispatchers.Default) {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, 0)
                .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .distinctBy { it.pkg }
                .filter { it.pkg != ctx.packageName }
                .sortedBy { it.label.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        EditorBar(title, leave) { onSave(sel); onBack() }
        val list = apps
        if (list == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Loading apps…", color = C.Muted, fontSize = 14.sp) }
        } else {
            val chosen = list.filter { it.pkg in sel }
            val others = list.filter { it.pkg !in sel }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item(key = "h1") { ListHeader("$listTitle (${chosen.size})", note) }
                items(chosen, key = { "s_" + it.pkg }) { a -> PickRow(a, false) { sel = sel - a.pkg } }
                item(key = "h2") { ListHeader("All other regular apps (${others.size})") }
                items(others, key = { "o_" + it.pkg }) { a -> PickRow(a, true) { sel = sel + a.pkg } }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    if (confirm) DiscardDialog(onDiscard = { confirm = false; onBack() }, onKeep = { confirm = false })
}

@Composable
private fun PickRow(app: AppInfo, add: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.pkg)
        Spacer(Modifier.width(16.dp))
        Text(app.label, color = C.Text, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
        RoundAction(add, onClick)
    }
}

/** Add / remove IP addresses (optionally with a /prefix) for split tunneling. */
@Composable
private fun IpPage(title: String, initial: List<String>, onSave: (List<String>) -> Unit, onBack: () -> Unit) {
    var ips by remember { mutableStateOf(initial) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val dirty = ips != initial
    val leave: () -> Unit = { if (dirty) confirm = true else onBack() }
    BackHandler(true) { leave() }
    val add: () -> Unit = {
        val ip = Choices.parseIp(text)
        if (ip == null) error = true
        else { if (ip !in ips) ips = ips + ip; text = ""; error = false }
    }
    Column(Modifier.fillMaxSize()) {
        EditorBar(title, leave) { onSave(ips); onBack() }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("IP address", color = C.Text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Small)).background(C.Card).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = text, onValueChange = { text = it; error = false }, singleLine = true,
                    textStyle = TextStyle(color = C.Text, fontSize = 16.sp), cursorBrush = SolidColor(C.Accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner -> if (text.isEmpty()) Text("192.0.2.0", color = C.Muted, fontSize = 16.sp); inner() },
                )
                RoundAction(true) { add() }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (error) "Not a valid IPv4 or IPv6 address." else "Enter an IPv4 or IPv6 address, optionally with a prefix like 10.0.0.0/8.",
                color = if (error) C.Red else C.Muted, fontSize = 12.sp,
            )
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "h") { ListHeader("$title (${ips.size})") }
            items(ips, key = { it }) { ip ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ip, color = C.Text, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    RoundAction(false) { ips = ips - ip }
                }
            }
        }
    }
    if (confirm) DiscardDialog(onDiscard = { confirm = false; onBack() }, onKeep = { confirm = false })
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
    val m = Modifier.size(AppRowIcon).clip(RoundedCornerShape(Radius.Small))
    val i = icon
    if (i != null) Image(i, null, m) else Box(m.background(C.Card))
}

private const val KILL_SWITCH_HELP_URL = "https://developer.android.com/develop/connectivity/vpn"

/** Full-screen sub page: back arrow (+ optional title), scrolling content, optional pinned buttons at the bottom. */
@Composable
private fun SubPage(
    title: String?, onBack: () -> Unit,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(true) { onBack() }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
            if (title != null) {
                Spacer(Modifier.width(6.dp))
                Text(title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { content(); Spacer(Modifier.height(16.dp)) }
        if (bottom != null) Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)) { bottom() }
    }
}

@Composable
private fun Hero(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 64.dp, centered: Boolean = false, tint: Color = C.Accent) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(Radius.Card)).background(Brush.linearGradient(listOf(C.AccentMid, C.AccentDeep))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.56f)) }
    }
}

@Composable
private fun PageHeading(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
        Text(title, color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(text, color = C.Muted, fontSize = 15.sp)
    }
}

@Composable
private fun ToggleCard(label: String, on: Boolean, onOn: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .bounceClick(RoundedCornerShape(Radius.Card), scaleTo = 0.985f, haptic = HapticFeedbackType.TextHandleMove) { onOn(!on) }
            .background(C.Card).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = C.Text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Toggle(on, onOn)
    }
}

@Composable
private fun ChoiceList(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), content = content)
}

@Composable
private fun ActionButton(
    label: String, filled: Boolean, color: Color = C.Button, trailing: ImageVector? = null, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.Card)
    Box(
        Modifier.fillMaxWidth().height(56.dp)
            .bounceClick(shape, scaleTo = 0.97f, haptic = HapticFeedbackType.TextHandleMove, onClick = onClick)
            .then(if (filled) Modifier.background(color) else Modifier.border(1.5.dp, C.Accent, shape))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) Color.White else C.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (trailing != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Icon(trailing, null, tint = if (filled) Color.White else C.Accent, modifier = Modifier.size(24.dp))
        }
    }
}
