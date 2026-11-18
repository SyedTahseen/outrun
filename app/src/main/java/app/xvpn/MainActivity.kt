package app.xvpn

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBar
import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.AppLog
import app.xvpn.data.GeoIp
import app.xvpn.data.LinkParser
import app.xvpn.data.NetInfo
import app.xvpn.data.RealDelay
import app.xvpn.data.Store
import app.xvpn.ui.C
import app.xvpn.ui.OutrunApp
import app.xvpn.ui.HomeScreen
import app.xvpn.ui.MenuScreen
import app.xvpn.ui.Motion
import app.xvpn.ui.Radius
import app.xvpn.ui.Sizes
import app.xvpn.ui.Type
import app.xvpn.ui.ServersScreen
import app.xvpn.ui.SortMode
import app.xvpn.ui.SpeedScreen
import app.xvpn.ui.StatsScreen
import app.xvpn.ui.appVersionName
import app.xvpn.ui.ProtonIcons
import app.xvpn.ui.axisTransition
import app.xvpn.ui.panelTransition
import app.xvpn.ui.rememberTick
import app.xvpn.ui.setUpEdgeToEdge
import app.xvpn.ui.AppSnackbarHost
import app.xvpn.ui.Snack
import app.xvpn.ui.SnackType
import app.xvpn.ui.largeScreenContentPadding
import app.xvpn.vpn.OutrunVpnService
import app.xvpn.vpn.RootCore
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import app.xvpn.widget.OutrunWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUpEdgeToEdge()
        setContent { OutrunApp { App() } }
    }

    override fun onResume() {
        super.onResume()
        OutrunWidget.refresh(this)
    }

    override fun onStop() {
        super.onStop()
        OutrunWidget.refresh(this)
    }
}

private enum class Tab(val label: String) {
    Home("Home"),
    Servers("Servers"),
    Speed("Speed"),
    Menu("Settings"),
}

@Composable
private fun Tab.icon(selected: Boolean): ImageVector = when (this) {
    Tab.Home -> if (selected) ProtonIcons.HouseFilled else ProtonIcons.House
    Tab.Servers -> if (selected) ProtonIcons.EarthFilled else ProtonIcons.Earth
    Tab.Speed -> ProtonIcons.Bolt
    Tab.Menu -> if (selected) ProtonIcons.CogWheelFilled else ProtonIcons.CogWheel
}

private enum class Screen { Home, Servers, Speed, Menu, Stats }

private fun Tab.screen() = when (this) { Tab.Home -> Screen.Home; Tab.Servers -> Screen.Servers; Tab.Speed -> Screen.Speed; Tab.Menu -> Screen.Menu }

@Composable
private fun App() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }

    remember { if (NetInfo.real.value == null) NetInfo.real.value = store.lastReal }
    var tab by remember { mutableStateOf(Tab.Home) }
    var profiles by remember { mutableStateOf(store.profiles()) }
    var selectedLink by remember { mutableStateOf(store.selected ?: profiles.firstOrNull()?.link) }

    var sort by remember { mutableStateOf(SortMode.entries.firstOrNull { it.name == store.sortName } ?: SortMode.Default) }
    var serverFilter by remember { mutableStateOf<String?>(null) }
    var subs by remember { mutableStateOf(store.subscriptions) }
    var favs by remember { mutableStateOf(store.favorites) }
    var sources by remember { mutableStateOf(store.sources) }
    var subInfo by remember { mutableStateOf(store.subInfo) }
    var pendingSub by remember { mutableStateOf<String?>(null) }
    val status by VpnState.status.collectAsState()
    val socks by VpnState.socksPort.collectAsState()
    LaunchedEffect(Unit) {
        if (VpnState.status.value == Status.Disconnected &&
            withContext(Dispatchers.IO) { RootCore.isRunning(ctx) }
        ) {
            if (VpnState.status.value == Status.Disconnected) OutrunVpnService.adopt(ctx)
        }
    }
    val real by NetInfo.real.collectAsState()
    val proxy by NetInfo.proxy.collectAsState()
    var showStats by remember { mutableStateOf(false) }
    val active by VpnState.activeLink.collectAsState()
    LaunchedEffect(active) {
        active?.let { a -> if (a != selectedLink && profiles.any { it.link == a }) selectedLink = a }
    }
    LaunchedEffect(real) { real?.let { store.lastReal = it } }

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

            status == Status.Connecting || status == Status.WaitingForNetwork -> NetInfo.proxy.value = null
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
    var pingsAt by remember { mutableStateOf(0L) }
    suspend fun runLatency() {
        testing = true; pings = emptyMap()
        runCatching { RealDelay.test(ctx, profiles.map { it.link }, store.testUrl()) { link, ms -> pings = pings + (link to ms) } }
        pingsAt = System.currentTimeMillis()
        testing = false
    }
    fun testLatency() {
        if (testing || profiles.isEmpty()) return
        scope.launch { runLatency() }
    }

    fun clipboardText(): String =
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()

    fun toast(m: String, type: SnackType = SnackType.Norm) = Snack.show(m, type)

    fun copyToClipboard(label: String, text: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun importText(text: String): Boolean {
        val t = text.trim()
        if (Regex("^https?://\\S+$").matches(t) && LinkParser.parseMany(t).isEmpty()) {
            pendingSub = t
            return true
        }
        val found = LinkParser.parseMany(text)
        val n = store.add(found)
        if (found.isEmpty()) toast("No valid links found", SnackType.Error) else toast(if (n == 1) "Server added" else "$n servers added", SnackType.Success)
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
            if (text == null) toast("Couldn\u2019t read file", SnackType.Error) else importText(text)
        }
    }

    val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) selectedLink?.let { OutrunVpnService.start(ctx, it) }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun connect(link: String) {
        selectedLink = link; store.selected = link
        val intent = if (store.rootMode) null else VpnService.prepare(ctx)
        if (intent != null) vpnPermission.launch(intent) else OutrunVpnService.start(ctx, link)
    }

    val rootMissing by VpnState.rootUnavailable.collectAsState()
    LaunchedEffect(rootMissing) {
        if (rootMissing != 0) {
            Snack.show("Root mode is unavailable. Using VPN mode", SnackType.Warning)
            VpnState.rootUnavailable.value = 0
            // 2: the service could not switch by itself (VPN permission not granted yet), so connect from here.
            if (rootMissing == 2) selectedLink?.let { connect(it) }
        }
    }

    fun connectFastest() {
        if (profiles.isEmpty()) { tab = Tab.Servers; return }
        scope.launch {
            if (testing) {
                while (testing) delay(300)
            } else if (System.currentTimeMillis() - pingsAt > 10 * 60_000L || pings.values.none { it > 0 }) {
                runLatency()
            }
            val best = profiles.mapNotNull { p -> pings[p.link]?.takeIf { it > 0 }?.let { p to it } }.minByOrNull { it.second }?.first
            if (best == null) toast("No server responded to the latency test", SnackType.Error)
            else { connect(best.link); tab = Tab.Home }
        }
    }

    fun connectRandom() {
        val current = VpnState.activeLink.value ?: selectedLink
        val pool = profiles.filter { it.link != current }
        if (pool.isEmpty()) { toast("No other server to switch to", SnackType.Warning); return }
        connect(pool.random().link)
        tab = Tab.Home
    }

    fun toggle() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        when (status) {
            Status.Disconnected -> selectedLink?.let { connect(it) } ?: run { tab = Tab.Servers }
            else -> OutrunVpnService.stop(ctx)
        }
    }

    fun refreshSubs(list: List<String>, silent: Boolean = false) {
        thread {
            var added = 0; var removed = 0; var failed = 0; var err = ""; var total = 0
            val skipped = HashMap<String, Int>()
            list.forEach {
                runCatching { store.importSubscription(it) }
                    .onSuccess { r -> added += r.added; removed += r.removed; total += r.total; r.skipped.forEach { (k, v) -> skipped[k] = (skipped[k] ?: 0) + v } }
                    .onFailure { e -> failed++; err = e.message ?: e.javaClass.simpleName }
            }
            Handler(Looper.getMainLooper()).post {
                reload()
                if (!silent) {
                    // Details go to the log; the snackbar stays one short line, like Proton VPN's.
                    AppLog.i(ctx, "subscription", "update: $added new, $removed removed, $total total, $failed failed" +
                        (if (err.isNotEmpty()) " ($err)" else "") +
                        (if (skipped.isNotEmpty()) ", skipped unsupported: " + skipped.entries.joinToString { "${it.key} ×${it.value}" } else ""))
                    val many = list.size > 1
                    when {
                        failed > 0 && failed == list.size -> toast(if (many) "Couldn\u2019t update subscriptions" else "Couldn\u2019t update subscription", SnackType.Error)
                        failed > 0 -> toast("Some subscriptions couldn\u2019t be updated", SnackType.Warning)
                        else -> toast(if (many) "Subscriptions updated" else "Subscription updated", SnackType.Success)
                    }
                }
            }
        }
    }

    LaunchedEffect(pendingSub) {
        pendingSub?.let { pendingSub = null; refreshSubs(listOf(it)) }
    }

    LaunchedEffect(Unit) {
        if (!store.autoUpdateSubs) return@LaunchedEffect
        val info = store.subInfo
        val stale = store.subscriptions.filter {
            val i = info[it]
            i == null || System.currentTimeMillis() - i.fetchedAt > (i.intervalHours.takeIf { h -> h > 0 } ?: 24) * 3_600_000L
        }
        if (stale.isNotEmpty()) refreshSubs(stale, silent = true)
    }

    val recents = remember(status, profiles) {
        store.recentLinks().mapNotNull { l -> profiles.firstOrNull { it.link == l } }.take(3)
    }

    val statsOn = showStats && status == Status.Connected
    val screen = if (statsOn) Screen.Stats else tab.screen()
    val homeVisible = screen == Screen.Home
    var menuFullScreen by remember { mutableStateOf(false) }
    val dockHidden = statsOn || menuFullScreen

    var serversScrolled by remember { mutableStateOf(false) }
    val statusStripColor by animateColorAsState(
        if (statsOn) C.Deep else if ((tab == Tab.Servers || tab == Tab.Menu) && serversScrolled) C.Header else C.Bg,
        tween(Motion.Medium), label = "statusStrip",
    )

    val stripAlpha by animateFloatAsState(if (homeVisible) 0f else 1f, tween(Motion.Medium), label = "stripAlpha")

    val dockInset by animateDpAsState(if (dockHidden) 0.dp else Sizes.Dock, tween(Motion.Medium, easing = Motion.Standard), label = "dockInset")

    Box(Modifier.fillMaxSize().background(C.Bg).imePadding()) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    when {
                        targetState == Screen.Stats -> panelTransition(entering = true)
                        initialState == Screen.Stats -> panelTransition(entering = false)
                        else -> axisTransition(forward = targetState.ordinal > initialState.ordinal)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                label = "screen",
            ) { s ->

                Box(
                    Modifier.fillMaxSize()
                        .then(if (s == Screen.Stats) Modifier.background(C.Deep) else Modifier)
                        .then(if (s == Screen.Home) Modifier else Modifier.padding(horizontal = largeScreenContentPadding()))
                        .then(if (s == Screen.Home) Modifier else Modifier.statusBarsPadding())
                        .navigationBarsPadding().padding(bottom = if (s == Screen.Stats) 0.dp else dockInset),
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
                            recents = recents,
                            onConnectRecent = { p -> connect(p.link) },
                            onRandomServer = ::connectRandom,
                        )
                        Screen.Servers -> ServersScreen(
                            profiles = profiles,
                            selectedLink = selectedLink,
                            onConnect = { p -> connect(p.link); tab = Tab.Home },
                            onConnectFastest = ::connectFastest,
                            onDelete = { p -> store.remove(p.link); reload() },
                            onPasteClipboard = ::importClipboard,
                            onScanQr = { qrLauncher.launch(Unit) },
                            onImportFile = { fileLauncher.launch(arrayOf("*/*")) },
                            onAddManual = { importText(it) },
                            onRefreshSubscriptions = { if (subs.isEmpty()) toast("No subscriptions added", SnackType.Warning) else refreshSubs(subs) },
                            favorites = favs,
                            pings = pings, testing = testing, onTestLatency = ::testLatency,
                            subscriptions = subs, sources = sources,
                            onDeleteSubscription = { url ->
                                val n = store.removeSubscription(url)
                                reload(); toast(if (n == 1) "Subscription and 1 server deleted" else "Subscription and $n servers deleted")
                            },
                            onToggleFavorite = { p ->
                                store.favorites = if (p.link in favs) favs - p.link else favs + p.link
                                favs = store.favorites
                            },
                            onCopy = { p ->
                                copyToClipboard("proxy", p.link)
                                toast("Copied to clipboard")
                            },
                            onAddSubscription = { refreshSubs(listOf(it)) },
                            onDeleteMany = { links ->
                                val n = store.removeMany(links)
                                reload(); toast(if (n == 1) "Server deleted" else "$n servers deleted")
                            },
                            onDeleteAll = { store.clear(); reload(); toast("All servers deleted") },
                            sort = sort, onSortChange = { sort = it; store.sortName = it.name },
                            filter = serverFilter, onFilterChange = { serverFilter = it },
                            onScrolledChange = { serversScrolled = it },
                            subInfo = subInfo,
                            onCopySubscription = { url ->
                                copyToClipboard("subscription", url)
                                toast("Copied to clipboard")
                            },
                        )
                        Screen.Speed -> SpeedScreen(
                            onFullScreenChange = { menuFullScreen = it },
                            onScrolledChange = { serversScrolled = it },
                        )
                        Screen.Menu -> MenuScreen(
                            store = store,
                            debugInfo = {
                                "App ${ctx.appVersionName()}\n" +
                                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                                    "Device ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                                    "Status $status\nServers ${profiles.size}\nSubscriptions ${subs.size}\n" +
                                    "Last error ${VpnState.error.value ?: "none"}"
                            },
                            onScrolledChange = { serversScrolled = it },
                            onFullScreenChange = { menuFullScreen = it },
                        )
                    }
                }
            }

            Spacer(
                Modifier.align(Alignment.TopStart).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                    .graphicsLayer { alpha = stripAlpha }.background(statusStripColor),
            )
        }
        AnimatedVisibility(
            visible = !dockHidden,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(Motion.Medium, easing = Motion.Standard)) { it } + fadeIn(tween(Motion.Medium)),
            exit = slideOutVertically(tween(Motion.Medium, easing = Motion.Standard)) { it } + fadeOut(tween(Motion.Fast)),
        ) {
            NavigationBar(containerColor = C.Card, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    val selected = t == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = t },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = C.Accent, unselectedIconColor = C.Muted,
                            selectedTextColor = C.Accent, unselectedTextColor = C.Muted,
                            indicatorColor = C.NavPill,
                        ),
                        icon = { Icon(t.icon(selected), contentDescription = null) },
                        label = { Text(t.label, style = Type.Label, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) },
                    )
                }
            }
        }
        AppSnackbarHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = dockInset).widthIn(max = Sizes.MaxContent))
    }
}
