package app.xvpn

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import app.xvpn.ui.AppIndication
import app.xvpn.ui.Motion
import app.xvpn.ui.axisTransition
import app.xvpn.ui.rememberTick
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import app.xvpn.data.RealDelay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import app.xvpn.data.GeoIp
import app.xvpn.data.NetInfo
import app.xvpn.ui.StatsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.LinkParser
import app.xvpn.data.Profile
import app.xvpn.data.Store
import app.xvpn.ui.C
import app.xvpn.ui.HomeScreen
import app.xvpn.ui.MenuScreen
import app.xvpn.ui.Radius
import app.xvpn.ui.ServersScreen
import app.xvpn.ui.SortMode
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import app.xvpn.vpn.SingBoxVpnService
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: the app draws behind the status bar (so the map reaches the top of the screen) and behind
        // the navigation bar, which gets the same colour as our bottom dock instead of system black.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),   // light icons over the map
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(C.Bg.toArgb()))
        setContent { DesignScale { AppIndication { App() } } }
    }
}

/**
 * The design is drawn on a 360dp-wide phone. Phones differ in "display size" / font size settings, which
 * made every dp/sp value render larger or smaller than the mockup. Re-base the density so the screen is always
 * 360dp wide (portrait) and ignore the system font scale: layout proportions then match the design everywhere.
 */
@Composable
internal fun DesignScale(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val cfg = LocalConfiguration.current
    val scale = minOf(cfg.screenWidthDp, cfg.screenHeightDp) / 360f
    CompositionLocalProvider(
        LocalDensity provides Density(d.density * scale, fontScale = 1f),
        content = content,
    )
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Servers("Servers", Icons.Filled.Public),
    Menu("Settings", Icons.Filled.Settings),
}

private enum class Screen { Home, Servers, Menu, Stats }

private fun Tab.screen() = when (this) { Tab.Home -> Screen.Home; Tab.Servers -> Screen.Servers; Tab.Menu -> Screen.Menu }

@Composable
private fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { Store(ctx) }
    // show the last known location straight away (before the first lookup finishes) instead of a default
    remember { if (NetInfo.real.value == null) NetInfo.real.value = store.lastReal }
    var tab by remember { mutableStateOf(Tab.Home) }
    var profiles by remember { mutableStateOf(store.profiles()) }
    var selectedLink by remember { mutableStateOf(store.selected ?: profiles.firstOrNull()?.link) }
    // Servers-screen choices live here so they survive switching tabs (the screen itself leaves composition)
    var sort by remember { mutableStateOf(SortMode.values().firstOrNull { it.name == store.sortName } ?: SortMode.Default) }
    var serverFilter by remember { mutableStateOf<String?>(null) }
    var subs by remember { mutableStateOf(store.subscriptions) }
    var favs by remember { mutableStateOf(store.favorites) }
    var sources by remember { mutableStateOf(store.sources) }
    var subInfo by remember { mutableStateOf(store.subInfo) }
    var message by remember { mutableStateOf<String?>(null) }
    val status by VpnState.status.collectAsState()
    val socks by VpnState.socksPort.collectAsState()
    val real by NetInfo.real.collectAsState()
    val proxy by NetInfo.proxy.collectAsState()
    var showStats by remember { mutableStateOf(false) }
    LaunchedEffect(real) { real?.let { store.lastReal = it } }

    // Look up our real location when the VPN is off, and the proxy's exit location (through sing-box's
    // local SOCKS port, because this app is excluded from the tunnel) when it is on.
    LaunchedEffect(status, socks) {
        if (status != Status.Connected) showStats = false
        when {
            status == Status.Disconnected -> {
                NetInfo.proxy.value = null
                for (i in 0 until 4) {
                    val r = withContext(Dispatchers.IO) { GeoIp.lookup(0) }
                    if (r != null) { NetInfo.real.value = r; break }
                    delay(1500)
                }
            }
            // switching servers: forget the old exit location so the map doesn't bounce back to it once connected
            status == Status.Connecting -> NetInfo.proxy.value = null
            status == Status.Connected && socks > 0 -> {
                for (i in 0 until 5) {
                    val r = withContext(Dispatchers.IO) { GeoIp.lookup(socks) }
                    if (r != null) { NetInfo.proxy.value = r; break }
                    delay(1500)
                }
            }
        }
    }
    BackHandler(enabled = showStats) { showStats = false }

    fun reload() {
        profiles = store.profiles(); subs = store.subscriptions; sources = store.sources; favs = store.favorites; subInfo = store.subInfo
        if (selectedLink == null || profiles.none { it.link == selectedLink }) selectedLink = profiles.firstOrNull()?.link
    }

    val scope = rememberCoroutineScope()
    var pings by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var testing by remember { mutableStateOf(false) }
    fun testLatency() {
        if (testing || profiles.isEmpty()) return
        testing = true; pings = emptyMap()
        val list = profiles.map { it.link }
        scope.launch {
            // real delay: an HTTPS request through each server (client -> server -> internet), same method as v2rayNG
            runCatching { RealDelay.test(ctx, list, store.testUrl()) { link, ms -> pings = pings + (link to ms) } }
            testing = false
        }
    }

    fun clipboardText(): String =
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()

    fun toast(m: String) = android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_SHORT).show()

    /** Parses share links / a subscription body from any text. Returns true if at least one valid link was found. */
    fun importText(text: String): Boolean {
        val found = LinkParser.parseMany(text)
        val n = store.add(found)
        message = if (found.isEmpty()) "No valid links found" else "Added $n server(s)"
        toast(message!!)
        reload()
        return found.isNotEmpty()
    }

    fun importClipboard() { importText(clipboardText()) }

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { text ->
        text?.let { importText(it) }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (text == null) toast("Couldn't read file") else importText(text)
        }
    }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) selectedLink?.let { SingBoxVpnService.start(ctx, it) }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun connect(link: String) {
        selectedLink = link; store.selected = link
        val intent = VpnService.prepare(ctx)
        if (intent != null) vpnPermission.launch(intent) else SingBoxVpnService.start(ctx, link)
    }

    fun toggle() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        when (status) {
            Status.Disconnected -> selectedLink?.let { connect(it) } ?: run { tab = Tab.Servers }
            else -> SingBoxVpnService.stop(ctx)
        }
    }

    // "Connect on launch": once, when the app opens with the VPN off and a server selected
    LaunchedEffect(Unit) {
        if (store.autoConnect && status == Status.Disconnected && selectedLink != null) toggle()
    }

    fun refreshSubs(list: List<String>, silent: Boolean = false) {
        if (!silent) message = "Updating…"
        thread {
            var added = 0; var removed = 0; var failed = 0; var err = ""; var total = 0
            val skipped = HashMap<String, Int>()
            list.forEach {
                runCatching { store.importSubscription(it) }
                    .onSuccess { r -> added += r.added; removed += r.removed; total += r.total; r.skipped.forEach { (k, v) -> skipped[k] = (skipped[k] ?: 0) + v } }
                    .onFailure { e -> failed++; err = e.message ?: e.javaClass.simpleName }
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                reload()
                if (!silent) {
                    message = when {
                        failed > 0 && failed == list.size -> "Couldn't update: $err"
                        else -> "Updated: $added new, $removed removed ($total in subscription)" +
                            (if (failed > 0) ", $failed failed" else "") +
                            (if (skipped.isNotEmpty()) ". Skipped unsupported: " + skipped.entries.joinToString { "${it.key} ×${it.value}" } else "")
                    }
                    toast(message!!)
                }
            }
        }
    }

    // Respect each subscription's profile-update-interval (default 24h): refresh stale ones when the app opens.
    LaunchedEffect(Unit) {
        if (!store.autoUpdateSubs) return@LaunchedEffect
        val info = store.subInfo
        val stale = store.subscriptions.filter {
            val i = info[it]
            i == null || System.currentTimeMillis() - i.fetchedAt > (i.intervalHours.takeIf { h -> h > 0 } ?: 24) * 3_600_000L
        }
        if (stale.isNotEmpty()) refreshSubs(stale, silent = true)
    }

    // Home draws its own map behind the status bar; every other screen sits below it.
    val statsOn = showStats && status == Status.Connected
    val screen = if (statsOn) Screen.Stats else tab.screen()
    val homeVisible = screen == Screen.Home
    val dockHidden = statsOn
    // Servers header colour is reported by ServersScreen: Bg at rest, Card once the list is scrolled.
    var serversScrolled by remember { mutableStateOf(false) }
    val statusStripColor by animateColorAsState(
        if ((tab == Tab.Servers || tab == Tab.Menu) && serversScrolled) C.Card else C.Bg,
        tween(Motion.Medium), label = "statusStrip",
    )
    // the strip fades in/out instead of popping when you enter or leave Home (which draws behind the status bar)
    val stripAlpha by animateFloatAsState(if (homeVisible) 0f else 1f, tween(Motion.Medium), label = "stripAlpha")
    // The system nav bar is transparent (set once in onCreate); the dock / stats screen paint behind it themselves.
    // Re-applying enableEdgeToEdge on every animation frame made the window insets jump while stats opened / closed.
    Column(Modifier.fillMaxSize().background(C.Bg).imePadding()) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { axisTransition(forward = targetState.ordinal > initialState.ordinal) },
                modifier = Modifier.fillMaxSize(),
                label = "screen",
            ) { s ->
                // Home draws its map behind the status bar; every other screen sits below it.
                Box(
                    Modifier.fillMaxSize()
                        .then(if (s == Screen.Home) Modifier else Modifier.statusBarsPadding())
                        .then(if (s == Screen.Stats) Modifier.navigationBarsPadding() else Modifier),
                ) {
                    when (s) {
                        Screen.Stats -> StatsScreen(
                            profile = profiles.firstOrNull { it.link == selectedLink },
                            real = real, proxy = proxy, onBack = { showStats = false },
                        )
                        Screen.Home -> HomeScreen(
                            selected = profiles.firstOrNull { it.link == selectedLink },
                            real = real, proxy = proxy,
                            onToggle = ::toggle,
                            onChangeServer = { tab = Tab.Servers },
                            onOpenStats = { showStats = true },
                        )
                        Screen.Servers -> ServersScreen(
                            profiles = profiles,
                            selectedLink = selectedLink,
                            onConnect = { p -> connect(p.link); tab = Tab.Home },
                            onDelete = { p -> store.remove(p.link); store.favorites = store.favorites - p.link; favs = store.favorites; reload() },
                            onPasteClipboard = ::importClipboard,
                            onScanQr = {
                                qrLauncher.launch(Unit)
                            },
                            onImportFile = { fileLauncher.launch(arrayOf("*/*")) },
                            onAddManual = { importText(it) },
                            onRefreshSubscriptions = { if (subs.isEmpty()) toast("No subscriptions added") else refreshSubs(subs) },
                            favorites = favs,
                            pings = pings, testing = testing, onTestLatency = ::testLatency,
                            subscriptions = subs, sources = sources,
                            onDeleteSubscription = { url ->
                                val n = store.removeSubscription(url)
                                reload(); toast("Deleted subscription and $n server(s)")
                            },
                            onToggleFavorite = { p ->
                                store.favorites = if (p.link in favs) favs - p.link else favs + p.link
                                favs = store.favorites
                            },
                            onCopy = { p ->
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("proxy", p.link))
                                toast("Copied ${p.name}")
                            },
                            onAddSubscription = { refreshSubs(listOf(it)) },
                            onDeleteAll = { store.clear(); favs = emptySet(); reload(); toast("All servers deleted") },
                            sort = sort, onSortChange = { sort = it; store.sortName = it.name },
                            filter = serverFilter, onFilterChange = { serverFilter = it },
                            onScrolledChange = { serversScrolled = it },
                            subInfo = subInfo,
                            onRefreshSubscription = { refreshSubs(listOf(it)) },
                            onCopySubscription = { url ->
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("subscription", url))
                                toast("Subscription link copied")
                            },
                        )
                        Screen.Menu -> MenuScreen(
                            store = store,
                            onExport = {
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("servers", profiles.joinToString("\n") { it.link }))
                                toast("Copied ${profiles.size} server link${if (profiles.size == 1) "" else "s"}")
                            },
                            onDebugInfo = {
                                val info = "App ${runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()}\n" +
                                    "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n" +
                                    "Device ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                                    "Status $status\nServers ${profiles.size}\nSubscriptions ${subs.size}\n" +
                                    "Last error ${VpnState.error.value ?: "none"}"
                                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                    .setPrimaryClip(android.content.ClipData.newPlainText("debug", info))
                                toast("Debug info copied")
                            },
                            onScrolledChange = { serversScrolled = it },
                        )
                    }
                }
            }
            // Solid strip behind the status icons on every screen except Home (which draws its map there).
            Spacer(
                Modifier.align(Alignment.TopStart).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                    .graphicsLayer { alpha = stripAlpha }.background(statusStripColor),
            )
        }
        AnimatedVisibility(
            visible = !dockHidden,
            enter = expandVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeIn(tween(Motion.Medium)),
            exit = shrinkVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeOut(tween(Motion.Short)),
        ) {
            Row(
                // fixed content height so the dock is exactly the same on every tab and device
                Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding().height(72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab.values().forEach { t -> DockItem(t, selected = t == tab) { tab = t } }
            }
        }
    }
}

/** One dock tab: the pill grows in behind the icon, the icon springs, label + icon colour fade, ripple stays inside the pill. */
@Composable
private fun RowScope.DockItem(t: Tab, selected: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val tick = rememberTick()
    val tint by animateColorAsState(if (selected) C.Accent else C.Muted, tween(Motion.Medium), label = "dockTint")
    val pillAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(Motion.Medium), label = "pillAlpha")
    val pillScale by animateFloatAsState(
        if (selected) 1f else 0.5f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow), label = "pillScale",
    )
    val iconScale by animateFloatAsState(
        if (selected) 1.1f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "iconScale",
    )
    val pill = RoundedCornerShape(Radius.Pill)
    Column(
        Modifier.weight(1f).clickable(interactionSource = source, indication = null) {
            if (!selected) tick()
            onClick()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.width(56.dp).height(32.dp).clip(pill).indication(source, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.matchParentSize().graphicsLayer { scaleX = pillScale; alpha = pillAlpha }.background(C.NavPill, pill),
            )
            Icon(
                t.icon, t.label, tint = tint,
                modifier = Modifier.size(22.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
            )
        }
        Text(t.label, color = tint, fontSize = 13.sp)
    }
}
