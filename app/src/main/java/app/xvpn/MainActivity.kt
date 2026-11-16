package app.xvpn

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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
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
import app.xvpn.data.GeoIp
import app.xvpn.data.LinkParser
import app.xvpn.data.NetInfo
import app.xvpn.data.RealDelay
import app.xvpn.data.Store
import app.xvpn.ui.AppIndication
import app.xvpn.ui.C
import app.xvpn.ui.DesignScale
import app.xvpn.ui.HomeScreen
import app.xvpn.ui.MenuScreen
import app.xvpn.ui.Motion
import app.xvpn.ui.Radius
import app.xvpn.ui.ServersScreen
import app.xvpn.ui.SortMode
import app.xvpn.ui.StatsScreen
import app.xvpn.ui.axisTransition
import app.xvpn.ui.rememberTick
import app.xvpn.ui.setUpEdgeToEdge
import app.xvpn.vpn.OutrunVpnService
import app.xvpn.widget.OutrunWidget
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setUpEdgeToEdge()
        setContent { DesignScale { AppIndication { App() } } }
    }

    override fun onResume() {
        super.onResume()
        OutrunWidget.refresh(this)
    }

    // Pick up a newly selected server / VPN permission when the user leaves the app.
    override fun onStop() {
        super.onStop()
        OutrunWidget.refresh(this)
    }
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
            runCatching { RealDelay.test(ctx, list, store.testUrl()) { link, ms -> pings = pings + (link to ms) } }
            testing = false
        }
    }

    fun clipboardText(): String =
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

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
        toast(if (found.isEmpty()) "No valid links found" else "Added $n server(s)")
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
        if (r.resultCode == Activity.RESULT_OK) selectedLink?.let { OutrunVpnService.start(ctx, it) }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun connect(link: String) {
        selectedLink = link; store.selected = link
        val intent = VpnService.prepare(ctx)
        if (intent != null) vpnPermission.launch(intent) else OutrunVpnService.start(ctx, link)
    }

    fun toggle() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        when (status) {
            Status.Disconnected -> selectedLink?.let { connect(it) } ?: run { tab = Tab.Servers }
            else -> OutrunVpnService.stop(ctx)
        }
    }

    LaunchedEffect(Unit) {
        if (store.autoConnect && status == Status.Disconnected && selectedLink != null) toggle()
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
                    toast(
                        when {
                            failed > 0 && failed == list.size -> "Couldn't update: $err"
                            else -> "Updated: $added new, $removed removed ($total in subscription)" +
                                (if (failed > 0) ", $failed failed" else "") +
                                (if (skipped.isNotEmpty()) ". Skipped unsupported: " + skipped.entries.joinToString { "${it.key} ×${it.value}" } else "")
                        },
                    )
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

    val statsOn = showStats && status == Status.Connected
    val screen = if (statsOn) Screen.Stats else tab.screen()
    val homeVisible = screen == Screen.Home
    val dockHidden = statsOn

    var serversScrolled by remember { mutableStateOf(false) }
    val statusStripColor by animateColorAsState(
        if ((tab == Tab.Servers || tab == Tab.Menu) && serversScrolled) C.Card else C.Bg,
        tween(Motion.Medium), label = "statusStrip",
    )

    val stripAlpha by animateFloatAsState(if (homeVisible) 0f else 1f, tween(Motion.Medium), label = "stripAlpha")

    Box(Modifier.fillMaxSize().background(C.Bg).imePadding()) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { axisTransition(forward = targetState.ordinal > initialState.ordinal) },
                modifier = Modifier.fillMaxSize(),
                label = "screen",
            ) { s ->

                Box(
                    Modifier.fillMaxSize()
                        .then(if (s == Screen.Home) Modifier else Modifier.statusBarsPadding())
                        .then(if (s == Screen.Stats) Modifier.navigationBarsPadding() else Modifier.navigationBarsPadding().padding(bottom = 72.dp)),
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
                            onDelete = { p -> store.remove(p.link); reload() },
                            onPasteClipboard = ::importClipboard,
                            onScanQr = { qrLauncher.launch(Unit) },
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
                                copyToClipboard("proxy", p.link)
                                toast("Copied ${p.name}")
                            },
                            onAddSubscription = { refreshSubs(listOf(it)) },
                            onDeleteMany = { links ->
                                val n = store.removeMany(links)
                                reload(); toast("Deleted $n server${if (n == 1) "" else "s"}")
                            },
                            onDeleteAll = { store.clear(); reload(); toast("All servers deleted") },
                            sort = sort, onSortChange = { sort = it; store.sortName = it.name },
                            filter = serverFilter, onFilterChange = { serverFilter = it },
                            onScrolledChange = { serversScrolled = it },
                            subInfo = subInfo,
                            onRefreshSubscription = { refreshSubs(listOf(it)) },
                            onCopySubscription = { url ->
                                copyToClipboard("subscription", url)
                                toast("Subscription link copied")
                            },
                        )
                        Screen.Menu -> MenuScreen(
                            store = store,
                            onExport = {
                                copyToClipboard("servers", profiles.joinToString("\n") { it.link })
                                toast("Copied ${profiles.size} server link${if (profiles.size == 1) "" else "s"}")
                            },
                            onDebugInfo = {
                                val info = "App ${runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()}\n" +
                                    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                                    "Device ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                                    "Status $status\nServers ${profiles.size}\nSubscriptions ${subs.size}\n" +
                                    "Last error ${VpnState.error.value ?: "none"}"
                                copyToClipboard("debug", info)
                                toast("Debug info copied")
                            },
                            onScrolledChange = { serversScrolled = it },
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
            exit = slideOutVertically(tween(Motion.Medium, easing = Motion.Standard)) { it } + fadeOut(tween(Motion.Short)),
        ) {
            Row(
                Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding().height(72.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab.entries.forEach { t -> DockItem(t, selected = t == tab) { tab = t } }
            }
        }
    }
}

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
