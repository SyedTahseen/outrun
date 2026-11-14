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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import app.xvpn.data.Ping
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.xvpn.ui.ServersScreen
import app.xvpn.vpn.Status
import app.xvpn.vpn.VpnState
import app.xvpn.vpn.SingBoxVpnService
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DesignScale { App() } }
    }
}

/**
 * The design is drawn on a 360dp-wide phone. Phones differ in "display size" / font size settings, which
 * made every dp/sp value render larger or smaller than the mockup. Re-base the density so the screen is always
 * 360dp wide (portrait) and ignore the system font scale: layout proportions then match the design everywhere.
 */
@Composable
private fun DesignScale(content: @Composable () -> Unit) {
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

@Composable
private fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { Store(ctx) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var profiles by remember { mutableStateOf(store.profiles()) }
    var selectedLink by remember { mutableStateOf(store.selected ?: profiles.firstOrNull()?.link) }
    var subs by remember { mutableStateOf(store.subscriptions) }
    var favs by remember { mutableStateOf(store.favorites) }
    var sources by remember { mutableStateOf(store.sources) }
    var message by remember { mutableStateOf<String?>(null) }
    val status by VpnState.status.collectAsState()
    val socks by VpnState.socksPort.collectAsState()
    val real by NetInfo.real.collectAsState()
    val proxy by NetInfo.proxy.collectAsState()
    var showStats by remember { mutableStateOf(false) }

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
        profiles = store.profiles(); subs = store.subscriptions; sources = store.sources; favs = store.favorites
        if (selectedLink == null || profiles.none { it.link == selectedLink }) selectedLink = profiles.firstOrNull()?.link
    }

    val scope = rememberCoroutineScope()
    var pings by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var testing by remember { mutableStateOf(false) }
    fun testLatency() {
        if (testing || profiles.isEmpty()) return
        testing = true; pings = emptyMap()
        val list = profiles
        scope.launch {
            val sem = Semaphore(8)
            list.map { p -> launch { sem.withPermit { val ms = Ping.tcp(p.host, p.port); pings = pings + (p.link to ms) } } }.joinAll()
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

    val qrLauncher = rememberLauncherForActivityResult(com.journeyapps.barcodescanner.ScanContract()) { r ->
        r.contents?.let { importText(it) }
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

    fun refreshSubs(list: List<String>) {
        message = "Updating…"
        thread {
            var added = 0; var failed = 0
            list.forEach { runCatching { added += store.importSubscription(it) }.onFailure { failed++ } }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                message = "Added $added server(s)" + if (failed > 0) ", $failed failed" else ""
                reload()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            if (showStats && status == Status.Connected) StatsScreen(
                profile = profiles.firstOrNull { it.link == selectedLink },
                real = real, proxy = proxy, onBack = { showStats = false },
            ) else when (tab) {
                Tab.Home -> HomeScreen(
                    selected = profiles.firstOrNull { it.link == selectedLink },
                    real = real, proxy = proxy,
                    onToggle = ::toggle,
                    onChangeServer = { tab = Tab.Servers },
                    onOpenStats = { showStats = true },
                )
                Tab.Servers -> ServersScreen(
                    profiles = profiles,
                    selectedLink = selectedLink,
                    onConnect = { p -> connect(p.link); tab = Tab.Home },
                    onDelete = { p -> store.remove(p.link); store.favorites = store.favorites - p.link; favs = store.favorites; reload() },
                    onPasteClipboard = ::importClipboard,
                    onScanQr = {
                        qrLauncher.launch(
                            com.journeyapps.barcodescanner.ScanOptions()
                                .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                                .setPrompt("Scan a proxy QR code")
                                .setBeepEnabled(false)
                                .setOrientationLocked(false)
                        )
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
                )
                Tab.Menu -> MenuScreen(
                    subscriptions = subs,
                    message = message,
                    onAddSubscription = { refreshSubs(listOf(it)) },
                    onRefreshSubscriptions = { refreshSubs(subs) },
                    onImportClipboard = ::importClipboard,
                )
            }
        }
        if (!(showStats && status == Status.Connected)) Row(
            Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding().padding(vertical = 10.dp),
        ) {
            Tab.values().forEach { t ->
                Column(
                    Modifier.weight(1f).clickable { tab = t },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val sel = t == tab
                    val c = if (sel) C.Accent else C.Muted
                    Box(
                        Modifier.width(64.dp).height(32.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) C.NavPill else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Icon(t.icon, t.label, tint = c, modifier = Modifier.size(22.dp)) }
                    Text(t.label, color = c, fontSize = 12.sp)
                }
            }
        }
    }
}
