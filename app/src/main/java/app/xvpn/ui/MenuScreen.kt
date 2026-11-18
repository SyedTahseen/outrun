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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import app.xvpn.LogActivity
import app.xvpn.data.Choices
import app.xvpn.data.Store
import app.xvpn.widget.OutrunWidget
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.flow.filter
import android.app.Activity
import android.net.VpnService
import app.xvpn.vpn.OutrunVpnService
import app.xvpn.vpn.RootShell
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        transitionSpec = { axisTransition(forward = pageDepth(targetState) >= pageDepth(initialState)) },
        label = "settingsPage",
    ) { shown -> MenuPage(store, debugInfo, onScrolledChange, onFullScreenChange, pageState, scroll, shown) }
    if (reconnectPrompt.value) ReconnectDialog()
}

private fun pageDepth(p: String) = when (p) {
    "main" -> 0
    "dns_add" -> 3
    "apps", "ips", "lan", "dns", "mtu" -> 2
    else -> 1
}

private fun pageParent(p: String) = when (p) {
    "apps", "ips" -> "split"
    "lan", "dns", "mtu" -> "advanced"
    "dns_add" -> "dns"
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
    var customDnsOn by remember { mutableStateOf(store.customDnsEnabled) }
    var customDns by remember { mutableStateOf(store.customDnsList) }
    var testUrl by remember { mutableStateOf(store.testUrlChoice) }
    var autoUpdate by remember { mutableStateOf(store.autoUpdateSubs) }
    var ipv6 by remember { mutableStateOf(store.ipv6Mode) }
    var netShield by remember { mutableStateOf(store.netShield) }
    var blockAdult by remember { mutableStateOf(store.blockAdult) }
    var lan by remember { mutableStateOf(store.lanAccess) }
    var mtu by remember { mutableStateOf(store.mtu) }
    var rootOn by remember { mutableStateOf(store.rootMode) }
    val rootMissing by VpnState.rootUnavailable.collectAsState()
    LaunchedEffect(rootMissing) { rootOn = store.rootMode }
    fun applyRootMode(on: Boolean) {
        val live = VpnState.status.value != Status.Disconnected
        rootOn = on; store.rootMode = on
        if (live) {
            OutrunVpnService.applyMode(ctx)
            Snack.show(if (on) "Switching root mode" else "Switching VPN mode")
        }
    }
    // Root mode never asked for the system VPN consent, so switching back while connected has to ask before the root core is stopped.
    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) applyRootMode(false)
        else Snack.show("VPN permission is needed to switch to VPN mode", SnackType.Warning)
    }
    var bootOn by remember { mutableStateOf(store.autoConnectBoot) }
    val uiScope = rememberCoroutineScope()

    var themeMode by remember { mutableStateOf(store.themeMode) }
    var splitMode by remember { mutableStateOf(store.splitMode) }
    var excApps by remember { mutableStateOf(store.splitApps) }
    var incApps by remember { mutableStateOf(store.splitIncludeApps) }
    var excIps by remember { mutableStateOf(store.splitExcludeIps) }
    var incIps by remember { mutableStateOf(store.splitIncludeIps) }
    val including = splitMode == "include"

    BackHandler(enabled = shown == page && page != "main") { page = pageParent(page) }

    when (shown) {
        "split" -> {
            val pm = ctx.packageManager
            val chosen = if (including) incApps else excApps
            val names = remember(chosen) {
                chosen.mapNotNull { runCatching { pm.getApplicationInfo(it, 0).loadLabel(pm).toString() }.getOrNull() }.sortedBy { it.lowercase() }
            }
            SplitTunnelingPage(
                on = splitOn, including = including, appNames = names, ips = if (including) incIps else excIps,
                onOn = { splitOn = it; store.splitEnabled = it; notifyReconnect(ctx) },
                onMode = { inc -> splitMode = if (inc) "include" else "exclude"; store.splitMode = splitMode; notifyReconnect(ctx) },
                onApps = { page = "apps" }, onIps = { page = "ips" },
                onBack = { page = "main" }, onScrolledChange = onScrolledChange,
            )
            return
        }
        "theme" -> {
            ThemePage(themeMode, { themeMode = it; AppTheme.set(ctx, it) }, { page = "main" })
            return
        }
        "advanced" -> {
            SubPage("Advanced settings", { page = "main" }) {
                SettingsValueItem(
                    "LAN connections", if (lan) "On" else "Off",
                    "Allows you to connect to printers, speakers, and other devices in your device\u2019s local network.",
                ) { page = "lan" }
                SettingsValueItem(
                    "Custom DNS", if (customDnsOn) "On" else "Off",
                    "Connect to VPN with a self-hosted or third-party domain name system (DNS).",
                ) { page = "dns" }
                SettingsToggleItem(
                    "IPv6 support", ipv6 != "block",
                    "Tunnels IPv6 traffic through the VPN. Can enhance compatibility with IPv6 networks. When off, IPv6 is blocked so it can\u2019t bypass servers without IPv6 support.",
                ) { on -> ipv6 = if (on) "tunnel" else "block"; store.ipv6Mode = ipv6; notifyReconnect(ctx) }
                SettingsValueItem(
                    "MTU", "$mtu",
                    "The largest packet the VPN sends. Lower it if some sites or apps stall or time out only while connected.",
                ) { page = "mtu" }
                SettingsToggleItem(
                    "Auto-connect on boot", bootOn,
                    "Connects the selected server when the phone starts. VPN service mode needs the VPN permission granted once; root mode needs root access. On some phones you must also allow auto-start and unrestricted battery use for this app.",
                ) { on -> bootOn = on; store.autoConnectBoot = on }
                SettingsToggleItem(
                    "Root mode", rootOn,
                    "Needs a rooted device. Sends all traffic through the VPN using root instead of Android\u2019s VPN service, so there is no VPN permission prompt or key icon. If you are connected, the connection switches right away.",
                ) { on ->
                    if (!on) {
                        val consent = if (VpnState.status.value != Status.Disconnected) VpnService.prepare(ctx) else null
                        if (consent != null) vpnConsent.launch(consent) else applyRootMode(false)
                    } else uiScope.launch {
                        val ok = withContext(Dispatchers.IO) { RootShell.granted() }
                        if (ok) {
                            if (VpnState.status.value == Status.Disconnected) Snack.show("Root access granted", SnackType.Success)
                            applyRootMode(true)
                        } else Snack.show("Root access wasn\u2019t granted", SnackType.Error)
                    }
                }
            }
            return
        }
        "lan" -> {
            LanPage(lan, { lan = it; store.lanAccess = it; notifyReconnect(ctx) }, { page = "advanced" }, onScrolledChange)
            return
        }
        "dns" -> {
            CustomDnsPage(
                on = customDnsOn, servers = customDns, netShieldConflict = netShield,
                onOn = { customDnsOn = it; store.customDnsEnabled = it; notifyReconnect(ctx) },
                onMove = { from, to ->
                    customDns = customDns.toMutableList().apply { add(to, removeAt(from)) }
                    store.customDnsList = customDns
                    notifyReconnect(ctx)
                },
                onRemove = { s ->
                    val at = customDns.indexOf(s)
                    customDns = customDns - s; store.customDnsList = customDns
                    Snack.show("Item removed", actionLabel = "Undo", onAction = {
                        if (s !in customDns) {
                            customDns = customDns.toMutableList().apply { add(at.coerceIn(0, size), s) }
                            store.customDnsList = customDns
                        }
                    })
                },
                onAdd = { page = "dns_add" },
                onBack = { page = "advanced" }, onScrolledChange = onScrolledChange,
            )
            return
        }
        "dns_add" -> {
            AddDnsPage(
                existing = customDns,
                onAdd = { customDns = customDns + it; store.customDnsList = customDns; notifyReconnect(ctx); page = "dns" },
                onBack = { page = "dns" },
            )
            return
        }
        "usage" -> {
            UsagePage { page = "main" }
            return
        }
        "test" -> {
            SubPage(null, { page = "main" }) {
                HeroIcon(ProtonIcons.Bolt)
                PageHeading("Latency test", "Each server is tested with a real request to this address, sent through the server. The time covers connecting to the server, the secure connection and the response.")
                Spacer(Modifier.height(Space.Sm))
                ChoiceList {
                    Choices.testUrls.forEach { o -> ChoiceRow(o.name, o.url, testUrl == o.id) { testUrl = o.id; store.testUrlChoice = o.id; page = "main" } }
                }
            }
            return
        }
        "netshield" -> {
            SubPage(null, { page = "main" }) {
                HeroIcon(ProtonIcons.Shield)
                PageHeading(
                    "NetShield",
                    "Blocks ads, trackers and malicious websites while you are connected, by sending DNS lookups to a filtering resolver (AdGuard DNS). Applies the next time you connect.",
                )
                val conflict = customDnsOn && customDns.isNotEmpty()
                if (conflict) {
                    Column(
                        Modifier.padding(horizontal = Space.Gutter, vertical = Space.Md).fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.Control)).background(C.Card).padding(Space.Lg),
                    ) {
                        Text("NetShield is unavailable", style = Type.BodyMedium, color = C.Text)
                        Spacer(Modifier.height(Space.Sm))
                        Text("NetShield can not filter out ads, trackers and malware when custom DNS is used.", style = Type.Small, color = C.Muted)
                        Spacer(Modifier.height(Space.Lg))
                        AppButton("Turn off custom DNS", { customDnsOn = false; store.customDnsEnabled = false; notifyReconnect(ctx) }, Modifier.fillMaxWidth(), ButtonStyle.OutlineAccent)
                    }
                }
                SwitchCard("NetShield", netShield) { netShield = it; store.netShield = it; notifyReconnect(ctx) }
                if (netShield) {
                    Spacer(Modifier.height(Space.Sm))
                    SwitchCard("Block adult content", blockAdult) { blockAdult = it; store.blockAdult = it; notifyReconnect(ctx) }
                    Text(
                        "NetShield works on domain names, so it can't block ads served from the same domain as the content, and apps that use their own encrypted DNS bypass it.",
                        style = Type.Caption, color = C.Muted,
                        modifier = Modifier.padding(horizontal = Space.Gutter, vertical = Space.Md),
                    )
                }
            }
            return
        }
        "mtu" -> {
            SubPage(null, { page = "advanced" }) {
                HeroIcon(ProtonIcons.Sliders)
                PageHeading(
                    "MTU",
                    "The largest packet the VPN sends. Lower it if some sites or apps stall, load halfway or time out only while connected. Applies the next time you connect.",
                )
                Spacer(Modifier.height(Space.Sm))
                ChoiceList {
                    Choices.mtuChoices.forEach { (v, note) ->
                        ChoiceRow("$v", note, mtu == v) { mtu = v; store.mtu = v; notifyReconnect(ctx); page = "advanced" }
                    }
                }
            }
            return
        }
        "autoupdate" -> {
            SubPage(null, { page = "main" }) {
                HeroIcon(ProtonIcons.ArrowsRotate)
                PageHeading("Auto-update subscriptions", "Refresh your subscriptions when the app opens, following each subscription's update interval.")
                SwitchCard("Auto-update subscriptions", autoUpdate) { autoUpdate = it; store.autoUpdateSubs = it }
            }
            return
        }
        "kill" -> {
            KillSwitchPage(
                onBack = { page = "main" },
                onOpenVpnSettings = { runCatching { ctx.startActivity(Intent(Settings.ACTION_VPN_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
                onLearnMore = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KILL_SWITCH_HELP_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            )
            return
        }
        "apps" -> {
            AppPickerPage(
                title = if (including) "Included apps" else "Excluded apps",
                listTitle = if (including) "Included apps" else "Excluded apps",
                note = if (including) "Only these apps connect through the VPN." else "These apps connect directly, bypassing the VPN.",
                initial = if (including) incApps else excApps,
                onSave = { if (including) { incApps = it; store.splitIncludeApps = it } else { excApps = it; store.splitApps = it }; notifyReconnect(ctx) },
                onBack = { page = "split" },
            )
            return
        }
        "ips" -> {
            IpPage(
                title = if (including) "Included IP addresses" else "Excluded IP addresses",
                initial = if (including) incIps else excIps,
                onSave = { if (including) { incIps = it; store.splitIncludeIps = it } else { excIps = it; store.splitExcludeIps = it }; notifyReconnect(ctx) },
                onBack = { page = "split" },
            )
            return
        }
    }

    val testLabel = Choices.testUrls.firstOrNull { it.id == testUrl }?.name ?: "Google"
    val version = remember { ctx.appVersionName() ?: "-" }

    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    ReportScrolled(scrolled, onScrolledChange)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", scrolled = scrolled)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScrollbar(scroll).verticalScroll(scroll)) {
            SectionHeader("Features", first = true)
            ListRow(
                ProtonIcons.Shield, "NetShield",
                if (!netShield) "Off" else if (customDnsOn && customDns.isNotEmpty()) "Unavailable with custom DNS" else if (blockAdult) "Blocking ads, trackers, malware and adult content" else "Blocking ads, trackers and malware",
            ) { page = "netshield" }
            ListRow(
                ProtonIcons.ArrowsSwapRight, "Split tunneling",
                if (!splitOn) "Off" else "On",
            ) { page = "split" }
            ListRow(ProtonIcons.Lock, "Kill switch") { page = "kill" }
            ListRow(ProtonIcons.ChartLine, "Usage & history", "Data use, connection log and app usage") { page = "usage" }

            SectionHeader("Connection")
            ListRow(ProtonIcons.Bolt, "Latency test", testLabel) { page = "test" }
            ListRow(ProtonIcons.ArrowsRotate, "Auto-update subscriptions", if (autoUpdate) "On" else "Off") { page = "autoupdate" }
            ListRow(ProtonIcons.Sliders, "Advanced settings") { page = "advanced" }

            SectionHeader("General")
            ListRow(ProtonIcons.Bell, "Notifications", if (notifOn) "On" else "Off") {
                val asked = ctx.uiPrefs().getBoolean(KEY_NOTIF_ASKED, false)
                if (!notifOn && Build.VERSION.SDK_INT >= 33 && !asked) {
                    ctx.uiPrefs().edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else ctx.openNotificationSettings()
            }
            ListRow(
                ProtonIcons.CircleHalfFilled, "Theme",
                when (themeMode) { AppTheme.LIGHT -> "Light"; AppTheme.DARK -> "Dark"; else -> "Device theme" },
            ) { page = "theme" }
            ListRow(
                ProtonIcons.Mobile, "Widget",
                if (widgetCount > 0) "Added to home screen" else "Add to home screen",
            ) { ctx.requestPinWidget(); widgetCount = ctx.widgetCount() }

            SectionHeader("Support")
            ListRow(ProtonIcons.LifeRing, "Support contact", external = true) { ctx.emailSupport() }
            ListRow(ProtonIcons.Bug, "Report an issue", external = true) { ctx.reportIssue(debugInfo()) }
            ListRow(ProtonIcons.Code, "Debug logs") {
                ctx.startActivity(Intent(ctx, LogActivity::class.java).putExtra(LogActivity.EXTRA_INFO, debugInfo()))
            }

            Spacer(Modifier.height(Space.Xxl))
            Text(
                "App version: $version", style = Type.Small, color = C.Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            TextAction("Third party licenses", { ctx.openUrl(LICENSE_URL) }, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(Space.Xxl))
        }
    }
}

private const val REPO_URL = "https://github.com/syedtahseen/outrun"
private const val LICENSE_URL = "$REPO_URL/blob/HEAD/LICENSE"
private const val SUPPORT_EMAIL = "itxtahseen@gmail.com"
private const val KEY_NOTIF_ASKED = "notif_asked"

private const val KEY_RECONNECT_CHOICE = "reconnect_choice"
private val reconnectPrompt = mutableStateOf(false)

/**
 * Same flow as Proton VPN: when a connection setting changes while connected, ask whether to reconnect now.
 * "Don't show again" remembers the answer (reconnect automatically, or leave it for the next connection).
 */
private fun notifyReconnect(ctx: Context) {
    if (VpnState.status.value == Status.Disconnected) return
    when (ctx.uiPrefs().getString(KEY_RECONNECT_CHOICE, "ask")) {
        "reconnect" -> OutrunVpnService.reconnect(ctx)
        "ignore" -> {}
        else -> reconnectPrompt.value = true
    }
}

@Composable
private fun ReconnectDialog() {
    val ctx = LocalContext.current
    var dontShowAgain by remember { mutableStateOf(false) }
    fun close(choice: String, reconnect: Boolean) {
        if (dontShowAgain) ctx.uiPrefs().edit().putString(KEY_RECONNECT_CHOICE, choice).apply()
        reconnectPrompt.value = false
        if (reconnect && VpnState.status.value != Status.Disconnected) OutrunVpnService.reconnect(ctx)
    }
    AppDialog(
        title = null,
        onDismiss = { close("ignore", false) },
        dismissLabel = "OK",
        confirmLabel = "Reconnect now",
        onConfirm = { close("reconnect", true) },
    ) {
        Text("New settings will apply next time you connect.", style = Type.Body, color = C.Text)
        Spacer(Modifier.height(Space.Lg))
        Row(
            Modifier.fillMaxWidth().clickable { dontShowAgain = !dontShowAgain },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Checkbox(
                checked = dontShowAgain, onCheckedChange = { dontShowAgain = it },
                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = C.Accent, uncheckedColor = C.Muted, checkmarkColor = C.OnPrimary),
            )
            Text("Don\u2019t show again", style = Type.Small, color = C.Muted)
        }
    }
}

private fun Context.uiPrefs() = getSharedPreferences("ui", Context.MODE_PRIVATE)

private fun Context.toast(msg: String, type: SnackType = SnackType.Norm) = Snack.show(msg, type)

private fun Context.launch(intent: Intent, failMsg: String) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { toast(failMsg, SnackType.Error) }
}

private fun Context.openUrl(url: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "No app can open this link")

private fun Context.notificationsEnabled(): Boolean = NotificationManagerCompat.from(this).areNotificationsEnabled()

private fun Context.openNotificationSettings() {
    val intent = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    }
    launch(intent, "Couldn\u2019t open notification settings")
}

private fun Context.widgetCount(): Int =
    runCatching { AppWidgetManager.getInstance(this).getAppWidgetIds(ComponentName(this, OutrunWidget::class.java)).size }
        .getOrDefault(0)

private fun Context.requestPinWidget() {
    val manager = AppWidgetManager.getInstance(this)
    val supported = Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported
    val requested = supported && runCatching {
        manager.requestPinAppWidget(ComponentName(this, OutrunWidget::class.java), null, null)
    }.getOrDefault(false)
    if (!requested) toast("To add the widget, press and hold your home screen, then select Outrun VPN from the widget menu")
}

private fun Context.emailSupport() {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, "Outrun VPN support")
    launch(intent, "No email app found. Write to $SUPPORT_EMAIL")
}

private fun Context.reportIssue(info: String) {
    val body = "**What happened?**\n\n\n\n**Environment**\n```\n$info\n```\n"
    openUrl("$REPO_URL/issues/new?body=${Uri.encode(body)}")
}

private fun Context.copyText(label: String, text: String) {
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
}

@Composable
private fun EditorBar(title: String, onBack: () -> Unit, onSave: () -> Unit) {
    ScreenHeader(title, onBack = onBack) { TextAction("Save", onSave) }
}

@Composable
private fun DiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AppDialog(
        title = "Discard unsaved changes?", onDismiss = onKeep,
        confirmLabel = "Discard", onConfirm = onDiscard, confirmColor = C.Red, dismissLabel = "Keep editing",
    )
}

@Composable
private fun ListHeader(title: String, note: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = Space.Gutter, end = Space.Gutter, top = Space.Lg, bottom = Space.Sm)) {
        Text(title, style = Type.Section, color = C.Accent)
        if (note != null) Text(note, style = Type.Small, color = C.Muted)
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
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("Loading apps…", style = Type.Small, color = C.Muted) }
        } else {
            val chosen = list.filter { it.pkg in sel }
            val others = list.filter { it.pkg !in sel }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState), state = listState) {
                item(key = "h1") { ListHeader("$listTitle (${chosen.size})", note) }
                items(chosen, key = { "s_" + it.pkg }) { a -> PickRow(a, false, Modifier.animateItem()) { sel = sel - a.pkg } }
                item(key = "h2") { ListHeader("All other regular apps (${others.size})") }
                items(others, key = { "o_" + it.pkg }) { a -> PickRow(a, true, Modifier.animateItem()) { sel = sel + a.pkg } }
                item(key = "end") { Spacer(Modifier.height(Space.Xxl)) }
            }
        }
    }
    if (confirm) DiscardDialog(onDiscard = { confirm = false; onBack() }, onKeep = { confirm = false })
}

@Composable
private fun PickRow(app: AppInfo, add: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = Space.Gutter, end = Space.Sm, top = Space.Xs, bottom = Space.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.pkg)
        Spacer(Modifier.width(Space.Lg))
        Text(app.label, style = Type.Body, color = C.Text, maxLines = 1, modifier = Modifier.weight(1f))
        IconAction(
            if (add) ProtonIcons.PlusCircle else ProtonIcons.MinusCircle,
            if (add) "Add" else "Remove", tint = if (add) C.Text else C.Muted, onClick = onClick,
        )
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
        Column(Modifier.padding(horizontal = Space.Gutter)) {
            FieldLabel("IP address")
            AppTextField(
                value = text, onValueChange = { text = it; error = false }, placeholder = "192.0.2.0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailing = { IconAction(ProtonIcons.PlusCircle, "Add") { add() } },
            )
            Spacer(Modifier.height(Space.Sm))
            Text(
                if (error) "Not a valid IPv4 or IPv6 address." else "Enter an IPv4 or IPv6 address, optionally with a prefix like 10.0.0.0/8.",
                style = Type.Caption, color = if (error) C.Red else C.Muted,
            )
        }
        val ipListState = rememberLazyListState()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().verticalScrollbar(ipListState), state = ipListState) {
            item(key = "h") { ListHeader("$title (${ips.size})") }
            items(ips, key = { it }) { ip ->
                Row(
                    Modifier.animateItem().fillMaxWidth().padding(start = Space.Gutter, end = Space.Sm, top = Space.Xs, bottom = Space.Xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(ip, style = Type.Body, color = C.Text, maxLines = 1, modifier = Modifier.weight(1f))
                    IconAction(ProtonIcons.MinusCircle, "Remove", tint = C.Muted) { ips = ips - ip }
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
    val m = Modifier.size(Sizes.IconLg).clip(RoundedCornerShape(Radius.Small))
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
        ScreenHeader(title, onBack)
        Spacer(Modifier.height(Space.Sm))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { content(); Spacer(Modifier.height(Space.Lg)) }
        if (bottom != null) Column(Modifier.fillMaxWidth().padding(start = Space.Gutter, end = Space.Gutter, top = Space.Sm, bottom = Space.Xl)) { bottom() }
    }
}

@Composable
private fun ChoiceList(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.Lg), content = content)
}
