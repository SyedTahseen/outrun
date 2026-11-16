package app.xvpn.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Support
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.xvpn.data.Choices
import app.xvpn.data.Store
import app.xvpn.widget.OutrunWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AppInfo(val label: String, val pkg: String)

@Composable
fun MenuScreen(
    store: Store,
    debugInfo: () -> String,
    onScrolledChange: (Boolean) -> Unit = {},
    onFullScreenChange: (Boolean) -> Unit = {},
) {
    val pageState = remember { mutableStateOf("main") }
    val scroll = rememberScrollState()

    AnimatedContent(
        targetState = pageState.value,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { axisTransition(forward = pageDepth(targetState) >= pageDepth(initialState), distance = 0.2f) },
        label = "settingsPage",
    ) { shown -> MenuPage(store, debugInfo, onScrolledChange, onFullScreenChange, pageState, scroll, shown) }
}

private fun pageDepth(p: String) = when (p) {
    "main" -> 0
    "mode", "apps", "ips", "dns_custom" -> 2
    else -> 1
}

private fun pageParent(p: String) = when (p) {
    "mode", "apps", "ips" -> "split"
    "dns_custom" -> "dns"
    else -> "main"
}

@Composable
private fun MenuPage(
    store: Store,
    debugInfo: () -> String,
    onScrolledChange: (Boolean) -> Unit,
    onFullScreenChange: (Boolean) -> Unit,
    pageState: MutableState<String>,
    scroll: ScrollState,
    shown: String,
) {
    val ctx = LocalContext.current
    var page by pageState

    // General: notification permission and home-screen widget state, re-read whenever the app resumes
    // (the user may have changed them in system settings or from the launcher).
    var notifOn by remember { mutableStateOf(ctx.notificationsEnabled()) }
    var widgetCount by remember { mutableStateOf(ctx.widgetCount()) }
    DisposableEffect(ctx) {
        val owner = ctx as? LifecycleOwner
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { notifOn = ctx.notificationsEnabled(); widgetCount = ctx.widgetCount() }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOn = ctx.notificationsEnabled()
    }

    var splitOn by remember { mutableStateOf(store.splitEnabled) }
    var dns by remember { mutableStateOf(store.dnsChoice) }
    var dnsCustom by remember { mutableStateOf(store.dnsCustom) }
    var testUrl by remember { mutableStateOf(store.testUrlChoice) }
    var autoConnect by remember { mutableStateOf(store.autoConnect) }
    var autoUpdate by remember { mutableStateOf(store.autoUpdateSubs) }
    var ipv6 by remember { mutableStateOf(store.ipv6Mode) }

    var splitMode by remember { mutableStateOf(store.splitMode) }
    var excApps by remember { mutableStateOf(store.splitApps) }
    var incApps by remember { mutableStateOf(store.splitIncludeApps) }
    var excIps by remember { mutableStateOf(store.splitExcludeIps) }
    var incIps by remember { mutableStateOf(store.splitIncludeIps) }
    val including = splitMode == "include"

    BackHandler(enabled = shown == page && page != "main") { page = pageParent(page) }

    when (shown) {
        "split" -> {
            SplitPage(
                on = splitOn, including = including,
                apps = if (including) incApps else excApps, ipCount = (if (including) incIps else excIps).size,
                onOn = { splitOn = it; store.splitEnabled = it },
                onMode = { page = "mode" },
                onApps = { page = "apps" }, onIps = { page = "ips" },
                onBack = { page = "main" },
            )
            return
        }
        "mode" -> {
            SubPage(null, { page = "split" }) {
                Hero(Icons.Outlined.Tune)
                PageHeading("Mode", "Choose how split tunneling treats the apps and IP addresses you select. Applies the next time you connect.")
                Spacer(Modifier.height(8.dp))
                ChoiceList {
                    ChoiceRow("Exclude", "Selected apps and IP addresses are excluded from the VPN connection.", !including) {
                        splitMode = "exclude"; store.splitMode = "exclude"; page = "split"
                    }
                    ChoiceRow("Include", "Only selected apps and IP addresses connect through the VPN, all other traffic is unprotected.", including) {
                        splitMode = "include"; store.splitMode = "include"; page = "split"
                    }
                }
            }
            return
        }
        "dns" -> {
            SubPage(null, { page = "main" }) {
                Hero(Icons.Outlined.Dns)
                PageHeading("DNS", "Choose the DNS servers used while connected. Applies the next time you connect.")
                Spacer(Modifier.height(8.dp))
                ChoiceList {
                    Choices.dns.forEach { o -> ChoiceRow(o.name, o.note, dns == o.id) { dns = o.id; store.dnsChoice = o.id; page = "main" } }
                    ChoiceRow("Custom", if (dnsCustom.isBlank()) "Your own IPv4 DNS servers" else dnsCustom, dns == "custom") { page = "dns_custom" }
                }
            }
            return
        }
        "dns_custom" -> {
            var text by remember { mutableStateOf(dnsCustom) }
            var error by remember { mutableStateOf(false) }
            Column(Modifier.fillMaxSize()) {
                EditorBar("Custom DNS", { page = "dns" }) {
                    if (Choices.parseDns(text).isNotEmpty()) {
                        dnsCustom = text.trim(); store.dnsCustom = dnsCustom
                        dns = "custom"; store.dnsChoice = "custom"; page = "main"
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
        "ipv6" -> {
            SubPage(null, { page = "main" }) {
                Hero(Icons.Outlined.Language)
                PageHeading("IPv6", "Choose how IPv6 traffic is handled while connected. Applies the next time you connect.")
                Spacer(Modifier.height(8.dp))
                ChoiceList {
                    ChoiceRow("Tunnel IPv6", "Send IPv6 through the VPN. If the server has no IPv6, apps fall back to IPv4.", ipv6 == "tunnel") {
                        ipv6 = "tunnel"; store.ipv6Mode = "tunnel"; page = "main"
                    }
                    ChoiceRow("Block IPv6", "Apps can only use IPv4. Stops IPv6 from bypassing servers that don't support it.", ipv6 == "block") {
                        ipv6 = "block"; store.ipv6Mode = "block"; page = "main"
                    }
                }
            }
            return
        }
        "usage" -> {
            UsagePage { page = "main" }
            return
        }
        "logs" -> {
            LogsPage(info = debugInfo(), onScrolledChange = onScrolledChange, onFullScreenChange = onFullScreenChange, onBack = { page = "main" })
            return
        }
        "test" -> {
            SubPage(null, { page = "main" }) {
                Hero(Icons.Outlined.Speed)
                PageHeading("Latency test", "Each server is tested with a real request to this address, sent through the server. The time covers connecting to the server, the secure connection and the response.")
                Spacer(Modifier.height(8.dp))
                ChoiceList {
                    Choices.testUrls.forEach { o -> ChoiceRow(o.name, o.url, testUrl == o.id) { testUrl = o.id; store.testUrlChoice = o.id; page = "main" } }
                }
            }
            return
        }
        "connect" -> {
            SubPage(null, { page = "main" }) {
                Hero(Icons.Outlined.PowerSettingsNew)
                PageHeading("Connect on launch", "Connect to the selected server automatically when the app opens.")
                ToggleCard("Connect on launch", autoConnect) { autoConnect = it; store.autoConnect = it }
            }
            return
        }
        "autoupdate" -> {
            SubPage(null, { page = "main" }) {
                Hero(Icons.Outlined.Sync)
                PageHeading("Auto-update subscriptions", "Refresh your subscriptions when the app opens, following each subscription's update interval.")
                ToggleCard("Auto-update subscriptions", autoUpdate) { autoUpdate = it; store.autoUpdateSubs = it }
            }
            return
        }
        "kill" -> {
            val bold = SpanStyle(fontWeight = FontWeight.Bold)
            SubPage(
                "Kill switch", { page = "main" },
                bottom = {
                    ActionButton("Settings", filled = true, trailing = Icons.AutoMirrored.Outlined.OpenInNew) {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    ActionButton("What is kill switch?", filled = false, trailing = Icons.AutoMirrored.Outlined.OpenInNew) {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KILL_SWITCH_HELP_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                },
            ) {
                Hero(Icons.Outlined.Shield, size = 96.dp, centered = true, tint = C.Green)
                Spacer(Modifier.height(24.dp))
                Text("How to enable kill switch", color = C.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(14.dp))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("1. Open your device settings", color = C.Text, fontSize = 17.sp)
                    Text("2. Find Outrun and tap the gear icon", color = C.Text, fontSize = 17.sp)
                    Text(
                        buildAnnotatedString {
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
                onBack = { page = "split" },
            )
            return
        }
        "ips" -> {
            IpPage(
                title = if (including) "Included IP addresses" else "Excluded IP addresses",
                initial = if (including) incIps else excIps,
                onSave = { if (including) { incIps = it; store.splitIncludeIps = it } else { excIps = it; store.splitExcludeIps = it } },
                onBack = { page = "split" },
            )
            return
        }
    }

    val dnsLabel = if (dns == "custom") "Custom" else Choices.dns.firstOrNull { it.id == dns }?.name ?: "Cloudflare"
    val testLabel = Choices.testUrls.firstOrNull { it.id == testUrl }?.name ?: "Google"
    val version = remember { ctx.appVersionName() ?: "-" }

    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    val headerColor by animateColorAsState(if (scrolled) C.Card else C.Bg, label = "settingsHeader")
    ReportScrolled(scrolled, onScrolledChange)

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(headerColor)) {
            Spacer(Modifier.height(20.dp))
            HeaderRow {
                Text("Settings", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScrollbar(scroll).verticalScroll(scroll)) {
            Section("Features", first = true)
            SettingRow(
                Icons.Outlined.AltRoute, "Split tunneling",
                if (!splitOn) "Off" else "On",
            ) { page = "split" }
            SettingRow(Icons.Outlined.Shield, "Kill switch") { page = "kill" }
            SettingRow(Icons.Outlined.BarChart, "Usage & history", "Data use, connection log and app usage") { page = "usage" }

            Section("Connection")
            SettingRow(Icons.Outlined.Dns, "DNS", dnsLabel) { page = "dns" }
            SettingRow(Icons.Outlined.Language, "IPv6", if (ipv6 == "block") "Blocked" else "Tunneled") { page = "ipv6" }
            SettingRow(Icons.Outlined.Speed, "Latency test", testLabel) { page = "test" }
            SettingRow(Icons.Outlined.PowerSettingsNew, "Connect on launch", if (autoConnect) "On" else "Off") { page = "connect" }
            SettingRow(Icons.Outlined.Sync, "Auto-update subscriptions", if (autoUpdate) "On" else "Off") { page = "autoupdate" }

            Section("General")
            SettingRow(Icons.Outlined.Notifications, "Notifications", if (notifOn) "On" else "Off") {
                val asked = ctx.uiPrefs().getBoolean(KEY_NOTIF_ASKED, false)
                if (!notifOn && Build.VERSION.SDK_INT >= 33 && !asked) {
                    ctx.uiPrefs().edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else ctx.openNotificationSettings()
            }
            SettingRow(
                Icons.Outlined.PhoneAndroid, "Widget",
                if (widgetCount > 0) "Added to home screen" else "Add to home screen",
            ) { ctx.requestPinWidget(); widgetCount = ctx.widgetCount() }

            Section("Support")
            SettingRow(Icons.Outlined.Support, "Support contact", external = true) { ctx.emailSupport() }
            SettingRow(Icons.Outlined.BugReport, "Report an issue", external = true) { ctx.reportIssue(debugInfo()) }
            SettingRow(Icons.Outlined.Code, "Debug logs") { page = "logs" }

            Spacer(Modifier.height(28.dp))
            Text(
                "App version: $version", color = C.Muted, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Third party licenses", color = C.Accent, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { ctx.openUrl(LICENSE_URL) }.padding(vertical = 10.dp),
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector, title: String, subtitle: String? = null,
    external: Boolean = false,
    onClick: () -> Unit,
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
        if (external) {
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, tint = C.Muted, modifier = Modifier.size(22.dp))
        }
    }
}

private const val REPO_URL = "https://github.com/syedtahseen/outrun"
private const val LICENSE_URL = "$REPO_URL/blob/HEAD/LICENSE"
private const val SUPPORT_EMAIL = "itxtahseen@gmail.com"
private const val KEY_NOTIF_ASKED = "notif_asked"

private fun Context.uiPrefs() = getSharedPreferences("ui", Context.MODE_PRIVATE)

private fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

private fun Context.launch(intent: Intent, failMsg: String) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { toast(failMsg) }
}

private fun Context.openUrl(url: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "No app can open this link")

private fun Context.notificationsEnabled(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    }
    launch(intent, "Couldn't open notification settings")
}

private fun Context.widgetCount(): Int =
    runCatching { AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, OutrunWidget::class.java)).size }
        .getOrDefault(0)

/** Asks the launcher to pin the widget; launchers without pinning support get manual instructions instead. */
private fun Context.requestPinWidget() {
    val manager = AppWidgetManager.getInstance(this)
    val supported = Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported
    val requested = supported && runCatching {
        manager.requestPinAppWidget(ComponentName(this, OutrunWidget::class.java), null, null)
    }.getOrDefault(false)
    if (!requested) toast("Long-press your home screen, then choose Widgets and Outrun VPN")
}

private fun Context.emailSupport() {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, "Outrun VPN support")
    launch(intent, "No email app found. Write to $SUPPORT_EMAIL")
}

/** Opens a new GitHub issue with the device details already filled in. */
private fun Context.reportIssue(info: String) {
    val body = "**What happened?**\n\n\n\n**Environment**\n```\n$info\n```\n"
    openUrl("$REPO_URL/issues/new?body=${Uri.encode(body)}")
}

private fun Context.copyText(label: String, text: String) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

/** The app's own recent log output (apps can only read their own entries). */
private suspend fun readLogs(): String = withContext(Dispatchers.IO) {
    runCatching {
        val p = ProcessBuilder("logcat", "-d", "-t", "400", "-v", "time").redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        p.destroy()
        text.trim().ifEmpty { "No log entries yet." }
    }.getOrElse { "Couldn't read logs: ${it.message}" }
}

@Composable
private fun LogsPage(
    info: String, onScrolledChange: (Boolean) -> Unit, onFullScreenChange: (Boolean) -> Unit, onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val logs by produceState<String?>(null) { value = readLogs() }
    BackHandler(true) { onBack() }
    // Full-screen page: hide the bottom bar; header and status strip keep the app background.
    ReportScrolled(false, onScrolledChange)
    DisposableEffect(Unit) { onFullScreenChange(true); onDispose { onFullScreenChange(false) } }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(C.Bg)) {
            Spacer(Modifier.height(20.dp))
            HeaderRow {
                HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Spacer(Modifier.width(6.dp))
                Text("Log", color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                HeaderIcon(Icons.Outlined.IosShare, "Share logs") {
                    logs?.let {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Outrun VPN logs").putExtra(Intent.EXTRA_TEXT, info + "\n\n" + it)
                        ctx.launch(Intent.createChooser(send, "Share logs"), "Couldn't share logs")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            logs ?: "Loading…", color = if (logs == null) C.Muted else C.Text, fontSize = 14.sp,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
        )
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
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(Motion.Short + 30)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 500f), initialScale = 0.9f),
            exit = fadeOut(tween(Motion.Short - 40)),
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(20.dp)) {
                Text(title, color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Small)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ring by animateColorAsState(if (selected) C.Accent else C.Muted, tween(Motion.Short + 50), label = "radioRing")
        Box(Modifier.size(22.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            val dot by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = if (selected) spring<Float>(dampingRatio = 0.45f, stiffness = 420f) else tween<Float>(Motion.Short - 30),
                label = "radioDot",
            )
            Box(
                Modifier.size(10.dp)
                    .graphicsLayer { scaleX = dot; scaleY = dot; alpha = dot.coerceIn(0f, 1f) }
                    .clip(CircleShape).background(C.Accent),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = C.Text, fontSize = 16.sp)
            Text(note, color = C.Muted, fontSize = 13.sp, maxLines = 4)
        }
    }
}

private val AppRowIcon = 32.dp

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
        BackBar(onBack = onBack)
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
            SettingRow(Icons.Outlined.Tune, "Mode", if (including) "Include" else "Exclude") { onMode() }
            SettingRow(Icons.Outlined.PhoneAndroid, "$word apps", appsText) { onApps() }
            SettingRow(
                Icons.Outlined.Terminal, "$word IP addresses",
                if (ipCount == 0) "None" else "$ipCount address${if (ipCount == 1) "" else "es"}",
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
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        EditorBar(title, leave) { onSave(sel); onBack() }
        val list = apps
        if (list == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Loading apps…", color = C.Muted, fontSize = 14.sp) }
        } else {
            val chosen = list.filter { it.pkg in sel }
            val others = list.filter { it.pkg !in sel }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState), state = listState) {
                item(key = "h1") { ListHeader("$listTitle (${chosen.size})", note) }
                items(chosen, key = { "s_" + it.pkg }) { a -> PickRow(a, false, Modifier.animateItem()) { sel = sel - a.pkg } }
                item(key = "h2") { ListHeader("All other regular apps (${others.size})") }
                items(others, key = { "o_" + it.pkg }) { a -> PickRow(a, true, Modifier.animateItem()) { sel = sel + a.pkg } }
                item(key = "end") { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    if (confirm) DiscardDialog(onDiscard = { confirm = false; onBack() }, onKeep = { confirm = false })
}

@Composable
private fun PickRow(app: AppInfo, add: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.pkg)
        Spacer(Modifier.width(16.dp))
        Text(app.label, color = C.Text, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
        RoundAction(add, onClick)
    }
}

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
        val ipListState = rememberLazyListState()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().verticalScrollbar(ipListState), state = ipListState) {
            item(key = "h") { ListHeader("$title (${ips.size})") }
            items(ips, key = { it }) { ip ->
                Row(
                    Modifier.animateItem().fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
    val icon by produceState<ImageBitmap?>(null, pkg) {
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

@Composable
private fun SubPage(
    title: String?, onBack: () -> Unit,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(true) { onBack() }
    Column(Modifier.fillMaxSize()) {
        BackBar(title, onBack)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { content(); Spacer(Modifier.height(16.dp)) }
        if (bottom != null) Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)) { bottom() }
    }
}

@Composable
private fun Hero(icon: ImageVector, size: Dp = 64.dp, centered: Boolean = false, tint: Color = C.Accent) {
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
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(Radius.Card)).background(C.Card)
            .clickable { onOn(!on) }.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
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
        Modifier.fillMaxWidth().height(56.dp).clip(shape)
            .then(if (filled) Modifier.background(color) else Modifier.border(1.5.dp, C.Accent, shape))
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (filled) Color.White else C.Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (trailing != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Icon(trailing, null, tint = if (filled) Color.White else C.Accent, modifier = Modifier.size(24.dp))
        }
    }
}
