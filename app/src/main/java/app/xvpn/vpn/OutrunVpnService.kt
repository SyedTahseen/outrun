package app.xvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import app.xvpn.MainActivity
import app.xvpn.R
import app.xvpn.data.Choices
import app.xvpn.data.ConfigBuilder
import app.xvpn.data.LinkParser
import app.xvpn.data.Session
import app.xvpn.data.Store
import app.xvpn.data.Usage
import app.xvpn.data.openConnection
import app.xvpn.widget.OutrunWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress
import java.util.Locale

/** establish() returned null: the user has not granted (or has revoked) VPN permission, so trying other servers can't help. */
private class NoVpnPermission : Exception("VPN permission was not granted")

class OutrunVpnService : VpnService() {
    companion object {
        const val ACTION_START = "app.xvpn.START"
        const val ACTION_STOP = "app.xvpn.STOP"
        const val ACTION_NEXT = "app.xvpn.NEXT"
        const val EXTRA_LINK = "link"
        private const val TAG = "OutrunVpnService"
        private const val VERIFY_TIMEOUT_MS = 20_000L
        private const val MAX_FALLBACKS = 3            // other servers tried when one fails to connect
        private const val NET_DEBOUNCE_MS = 1_500L     // network callbacks come in bursts; reconnect once they settle
        private const val HEALTH_INTERVAL_MS = 60_000L // tunnel health probe while connected
        private const val HEALTH_FAILS = 2             // consecutive failed probes before reconnecting
        private val PROBE_URLS = Choices.testUrls.take(2).map { it.url }
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 1

        fun start(ctx: Context, link: String) = ctx.startForegroundService(
            Intent(ctx, OutrunVpnService::class.java).setAction(ACTION_START).putExtra(EXTRA_LINK, link)
        )

        fun stop(ctx: Context) = ctx.startService(
            Intent(ctx, OutrunVpnService::class.java).setAction(ACTION_STOP)
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statsJob: Job? = null
    private var connectJob: Job? = null
    private var tun: ParcelFileDescriptor? = null
    private var core: SingBoxCore? = null

    private var currentLink: String? = null
    private var currentName: String = ""

    /** True while the user wants the VPN up (set on start, cleared on disconnect); gates every automatic reconnect. */
    @Volatile private var wanted = false
    private var healthJob: Job? = null
    private var netJob: Job? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNet: Network? = null
    private var netLost = false
    @Volatile private var waitingNet = false
    private var canSwitch = false
    private var sessionStart = 0L
    private var sessUp = 0L
    private var sessDown = 0L
    private var pendUp = 0L
    private var pendDown = 0L

    private val notificationManager: NotificationManager get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        notificationManager
            .createNotificationChannel(NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> shutdown()
            ACTION_NEXT -> switchNext()
            ACTION_START -> {
                val link = intent.getStringExtra(EXTRA_LINK) ?: return START_NOT_STICKY
                begin(link)
            }
            else -> {
                // Started by the system as an Always-on VPN (no action of ours): connect to the selected server.
                val link = Store(this).selected?.takeIf { LinkParser.parse(it) != null }
                if (link != null) begin(link)
                else {
                    startForeground(NOTIFICATION_ID, notification(getString(R.string.app_name), "Select a server in the app"))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun begin(link: String) {
        wanted = true
        startForeground(NOTIFICATION_ID, notification(getString(R.string.app_name), "Connecting…"))
        registerNetworkWatch()
        launchConnect(link)
    }

    /** Cancels any attempt in progress and connects to [link] (with fallback to other servers if enabled). */
    private fun launchConnect(link: String) {
        val prev = connectJob
        connectJob = scope.launch { prev?.cancelAndJoin(); connect(link, emptySet()) }
    }

    private fun nextLink(): String? {
        val store = Store(this)
        val all = store.links().filter { LinkParser.parse(it) != null }
        val favs = all.filter { it in store.favorites }
        val pool = if (favs.size >= 2) favs else all
        if (pool.size < 2) return null
        return pool[(pool.indexOf(currentLink) + 1) % pool.size]
    }

    /** Next server to try after [exclude] failed: favorites first, then every other server, in list order. */
    private fun fallbackLink(exclude: Set<String>): String? {
        val store = Store(this)
        val all = store.links().filter { LinkParser.parse(it) != null && it !in exclude }
        val favs = all.filter { it in store.favorites }
        return (favs.ifEmpty { all }).firstOrNull()
    }

    private fun switchNext() {
        val next = nextLink() ?: return
        val name = LinkParser.parse(next)?.name ?: ""
        notificationManager.notify(NOTIFICATION_ID, notification(getString(R.string.app_name), "Switching to $name…"))
        wanted = true
        launchConnect(next)
    }

    // ---- network changes and tunnel health ----

    /** Watches the real (non-VPN) default network: when it changes (Wi-Fi <-> mobile) or returns after a drop, the tunnel is rebuilt. */
    private fun registerNetworkWatch() {
        if (netCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true) return
                onUnderlyingAvailable(network)
            }

            override fun onLost(network: Network) {
                if (network == lastNet) {
                    lastNet = null; netLost = true
                    if (wanted && VpnState.status.value == Status.Connected) waitingNet = true
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); netCallback = cb }
    }

    private fun unregisterNetworkWatch() {
        netCallback?.let { cb -> runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } }
        netCallback = null
        lastNet = null; netLost = false; waitingNet = false
        netJob?.cancel(); netJob = null
    }

    @Synchronized
    private fun onUnderlyingAvailable(network: Network) {
        val changed = lastNet != null && lastNet != network
        val recovered = lastNet == null && netLost
        lastNet = network
        waitingNet = false
        if (!wanted) return
        runCatching { setUnderlyingNetworks(arrayOf(network)) }
        if ((changed || recovered) && Store(this).autoReconnect) {
            netLost = false
            val link = currentLink ?: return
            netJob?.cancel()
            netJob = scope.launch {
                delay(NET_DEBOUNCE_MS)
                notificationManager.notify(NOTIFICATION_ID, notification(getString(R.string.app_name), "Network changed, reconnecting…"))
                launchConnect(link)
            }
        } else netLost = false
    }

    /** While connected, probes the tunnel once a minute; after [HEALTH_FAILS] failures in a row it reconnects (and falls back if needed). */
    private fun startHealth(port: Int) {
        healthJob?.cancel()
        healthJob = scope.launch {
            var fails = 0
            while (isActive) {
                delay(HEALTH_INTERVAL_MS)
                if (waitingNet || VpnState.status.value != Status.Connected || !Store(this@OutrunVpnService).autoReconnect) { fails = 0; continue }
                val ok = withContext(Dispatchers.IO) { PROBE_URLS.any { probe(port, it) } }
                fails = if (ok) 0 else fails + 1
                if (fails >= HEALTH_FAILS) {
                    val link = currentLink ?: return@launch
                    notificationManager.notify(NOTIFICATION_ID, notification(getString(R.string.app_name), "Connection lost, reconnecting…"))
                    launchConnect(link)
                    return@launch
                }
            }
        }
    }

    @Synchronized
    private fun flushUsage() {
        if (pendUp > 0 || pendDown > 0) {
            Usage.addToday(this, pendUp, pendDown)
            pendUp = 0; pendDown = 0
        }
    }

    @Synchronized
    private fun finishSession() {
        if (sessionStart == 0L) return
        flushUsage()
        Usage.saveSession(this, Session(sessionStart, System.currentTimeMillis(), currentName, sessUp, sessDown))
        sessionStart = 0L; sessUp = 0; sessDown = 0
    }

    private fun rate(b: Long): String =
        if (b >= 1_000_000) String.format(Locale.US, "%.1f MB/s", b / 1_000_000.0)
        else String.format(Locale.US, "%.0f KB/s", b / 1000.0)

    /** Prefix length of "addr/len"; a bare address is a single host (/32 or /128). */
    private fun prefixLength(ip: String): Int = ip.substringAfter('/', if (':' in ip) "128" else "32").toInt()

    private suspend fun connect(link: String, tried: Set<String>) {
        VpnState.error.value = null
        VpnState.status.value = Status.Connecting
        OutrunWidget.refresh(this)

        val hadSession = tun != null || core != null
        teardownSession()
        currentLink = link
        if (tried.isEmpty()) Store(this).selected = link   // a fallback server doesn't replace the user's choice
        VpnState.activeLink.value = link
        canSwitch = nextLink() != null
        if (hadSession) delay(300)
        var x: SingBoxCore? = null
        var pfd: ParcelFileDescriptor? = null
        var tunStarted = false
        try {
            val profile = LinkParser.parse(link) ?: error("Invalid link")

            val port = SingBoxCore.freePort()
            x = SingBoxCore(this)
            core = x
            val settings = Store(this)
            val dns = settings.dnsServers()       // custom DNS, else NetShield's filtering DNS when it is on
            val mtu = settings.mtu
            x.start(ConfigBuilder.build(link, port, settings.resolverDns()), port)
            currentCoroutineContext().ensureActive()

            val b = Builder()
                .setSession(profile.name)
                .setMtu(mtu)
                .addAddress(Tun2Socks.TUN_IPV4, 30)
            val v6 = settings.ipv6Mode != "block"
            if (v6) b.addAddress(Tun2Socks.TUN_IPV6, 126)
            dns.forEach { b.addDnsServer(it) }

            val include = settings.splitEnabled && settings.splitMode == "include"
            val exclude = settings.splitEnabled && !include
            val incApps = if (include) settings.splitIncludeApps.filter { it != packageName } else emptyList()
            val incIps = if (include) settings.splitIncludeIps else emptyList()
            if (incIps.isEmpty() && settings.lanAccess) {
                LanRoutes.ipv4.forEach { (addr, len) -> b.addRoute(addr, len) }
                b.addRoute(LanRoutes.IPV6_ADDRESS, LanRoutes.IPV6_PREFIX)
            } else if (incIps.isEmpty()) { b.addRoute("0.0.0.0", 0); b.addRoute("::", 0) }
            else incIps.forEach { ip ->
                runCatching { b.addRoute(ip.substringBefore('/'), prefixLength(ip)) }
            }
            if (exclude && Build.VERSION.SDK_INT >= 33) settings.splitExcludeIps.forEach { ip ->
                runCatching {
                    b.excludeRoute(IpPrefix(InetAddress.getByName(ip.substringBefore('/')), prefixLength(ip)))
                }
            }

            if (incApps.isNotEmpty()) incApps.forEach { pkg -> runCatching { b.addAllowedApplication(pkg) } }
            else {
                if (exclude) settings.splitApps.forEach { pkg ->
                    if (pkg != packageName) runCatching { b.addDisallowedApplication(pkg) }
                }
                b.addDisallowedApplication(packageName)
            }
            pfd = b.establish() ?: throw NoVpnPermission()
            tun = pfd
            currentCoroutineContext().ensureActive()

            val cfg = File(filesDir, "tun2socks.yml").apply {
                writeText(Tun2Socks.yaml(port, mtu, v6))
            }
            var started = Tun2Socks.TProxyStartService(cfg.absolutePath, pfd.fd)
            if (!started) {
                runCatching { Tun2Socks.TProxyStopService() }
                delay(400)
                started = Tun2Socks.TProxyStartService(cfg.absolutePath, pfd.fd)
            }
            if (!started) error("tun2socks failed to start")
            tunStarted = true

            if (!verifyProxy(port)) error("Server is not responding")

            VpnState.socksPort.value = port
            VpnState.connectedAt.value = System.currentTimeMillis()
            VpnState.status.value = Status.Connected
            OutrunWidget.refresh(this)
            currentName = profile.name
            sessionStart = System.currentTimeMillis(); sessUp = 0; sessDown = 0; pendUp = 0; pendDown = 0
            VpnState.sessionUp.value = 0; VpnState.sessionDown.value = 0
            if (Build.VERSION.SDK_INT >= 29) {
                VpnState.alwaysOn.value = isAlwaysOn
                VpnState.lockdown.value = isLockdownEnabled
            }
            notificationManager.notify(NOTIFICATION_ID, notification("Connected · ${profile.name}", "↓ 0 KB/s   ↑ 0 KB/s"))
            startStats()
            startHealth(port)
            Store(this).pushRecent(link)
        } catch (e: Throwable) {
            if (!currentCoroutineContext().isActive) {
                if (tunStarted) runCatching { Tun2Socks.TProxyStopService() }
                runCatching { x?.stop() }
                runCatching { pfd?.close() }
            } else {
                Log.w(TAG, "connect failed: ${e.javaClass.simpleName}")
                val next = if (e !is NoVpnPermission && wanted && Store(this).autoReconnect && tried.size < MAX_FALLBACKS) fallbackLink(tried + link) else null
                if (next != null) {
                    notificationManager.notify(
                        NOTIFICATION_ID, notification(getString(R.string.app_name), "Server unreachable, trying ${LinkParser.parse(next)?.name ?: "another server"}…"),
                    )
                    connect(next, tried + link)
                } else {
                    val msg = e.message ?: e.javaClass.simpleName
                    VpnState.error.value = if (tried.isEmpty()) msg else "$msg (tried ${tried.size + 1} servers)"
                    shutdown()
                }
            }
        }
    }

    private suspend fun verifyProxy(port: Int): Boolean {
        val deadline = System.currentTimeMillis() + VERIFY_TIMEOUT_MS
        var n = 0
        while (System.currentTimeMillis() < deadline) {
            currentCoroutineContext().ensureActive()
            if (withContext(Dispatchers.IO) { probe(port, PROBE_URLS[n++ % PROBE_URLS.size]) }) return true
            delay(600)
        }
        return false
    }

    private fun probe(port: Int, url: String): Boolean = runCatching {
        val c = openConnection(url, port, timeoutMs = 5000)
        try {
            c.instanceFollowRedirects = false
            c.responseCode.let { it == 204 || it == 200 }
        } finally { c.disconnect() }
    }.getOrDefault(false)

    private fun startStats() {
        statsJob?.cancel()
        statsJob = scope.launch {
            fun read(): Pair<Long, Long> {
                val s = runCatching { Tun2Socks.TProxyGetStats() }.getOrNull()
                return if (s != null && s.size >= 4) s[1] to s[3] else 0L to 0L
            }
            var (lastUp, lastDown) = read()
            var ticks = 0
            while (isActive) {
                delay(1000)
                val (up, down) = read()
                val dUp = (up - lastUp).coerceAtLeast(0)
                val dDown = (down - lastDown).coerceAtLeast(0)
                lastUp = up; lastDown = down
                VpnState.upBytesPerSec.value = dUp
                VpnState.downBytesPerSec.value = dDown
                VpnState.speedHistory.value = (VpnState.speedHistory.value + (dUp to dDown)).takeLast(60)
                synchronized(this@OutrunVpnService) {
                    sessUp += dUp; sessDown += dDown; pendUp += dUp; pendDown += dDown
                    VpnState.sessionUp.value = sessUp; VpnState.sessionDown.value = sessDown
                }
                if (++ticks % 10 == 0) flushUsage()
                if (VpnState.status.value == Status.Connected) {
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        if (waitingNet) notification("Connected · $currentName", "Waiting for network…")
                        else notification("Connected · $currentName", "↓ ${rate(VpnState.downBytesPerSec.value)}   ↑ ${rate(VpnState.upBytesPerSec.value)}"),
                    )
                }
            }
        }
    }

    private fun teardownSession() {
        statsJob?.cancel(); statsJob = null
        healthJob?.cancel(); healthJob = null
        finishSession()
        runCatching { Tun2Socks.TProxyStopService() }
        runCatching { core?.stop() }
        core = null
        runCatching { tun?.close() }
        tun = null
        VpnState.upBytesPerSec.value = 0
        VpnState.downBytesPerSec.value = 0
        VpnState.speedHistory.value = emptyList()
        VpnState.socksPort.value = 0
    }

    private fun shutdown() {
        wanted = false
        unregisterNetworkWatch()
        connectJob?.cancel()
        teardownSession()
        VpnState.status.value = Status.Disconnected
        VpnState.activeLink.value = null
        VpnState.sessionUp.value = 0; VpnState.sessionDown.value = 0
        OutrunWidget.refresh(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = shutdown()

    override fun onDestroy() {
        finishSession()
        runCatching { core?.stop() }
        scope.cancel()
        super.onDestroy()
    }

    private fun action(title: String, act: String, req: Int, icon: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, req, Intent(this, OutrunVpnService::class.java).setAction(act),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun notification(title: String, text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val b = Notification.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_outrun)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(action("Disconnect", ACTION_STOP, 1, android.R.drawable.ic_menu_close_clear_cancel))
        if (canSwitch) b.addAction(action("Switch server", ACTION_NEXT, 2, android.R.drawable.ic_menu_rotate))
        return b.build()
    }
}
