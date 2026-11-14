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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
        setContent { App() }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Servers("Servers", Icons.Filled.Place),
    Menu("Menu", Icons.Filled.Menu),
}

@Composable
private fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val store = remember { Store(ctx) }
    var tab by remember { mutableStateOf(Tab.Home) }
    var profiles by remember { mutableStateOf(store.profiles()) }
    var selectedLink by remember { mutableStateOf(store.selected ?: profiles.firstOrNull()?.link) }
    var subs by remember { mutableStateOf(store.subscriptions) }
    var message by remember { mutableStateOf<String?>(null) }
    val status by VpnState.status.collectAsState()

    fun reload() {
        profiles = store.profiles(); subs = store.subscriptions
        if (selectedLink == null || profiles.none { it.link == selectedLink }) selectedLink = profiles.firstOrNull()?.link
    }

    fun clipboardText(): String =
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()

    fun importClipboard() {
        val found = LinkParser.parseMany(clipboardText())
        val n = store.add(found)
        message = if (found.isEmpty()) "No valid links found in clipboard" else "Added $n server(s)"
        reload()
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
            when (tab) {
                Tab.Home -> HomeScreen(
                    selected = profiles.firstOrNull { it.link == selectedLink },
                    onToggle = ::toggle,
                    onChangeServer = { tab = Tab.Servers },
                )
                Tab.Servers -> ServersScreen(
                    profiles = profiles,
                    selectedLink = selectedLink,
                    onConnect = { p -> connect(p.link); tab = Tab.Home },
                    onDelete = { p -> store.remove(p.link); reload() },
                    onPasteLink = ::importClipboard,
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
        Row(
            Modifier.fillMaxWidth().background(C.Card).navigationBarsPadding().padding(vertical = 10.dp),
        ) {
            Tab.values().forEach { t ->
                Column(
                    Modifier.weight(1f).clickable { tab = t },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val c = if (t == tab) C.Accent else C.Muted
                    Icon(t.icon, t.label, tint = c)
                    Text(t.label, color = c, fontSize = 11.sp)
                }
            }
        }
    }
}
