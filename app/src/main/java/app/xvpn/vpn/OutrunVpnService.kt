package app.xvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.ConnectivityManager
import android.net.IpPrefix
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import app.xvpn.MainActivity
import app.xvpn.R
import app.xvpn.data.AppLog
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
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

private class NoVpnPermission : Exception("VPN permission was not granted")
private class RootUnavailable : Exception("Su not available")

class OutrunVpnService : VpnService() {
    companion object {
        const val ACTION_START = "app.xvpn.START"
        const val ACTION_STOP = "app.xvpn.STOP"
        const val ACTION_NEXT = "app.xvpn.NEXT"
        const val ACTION_ADOPT = "app.xvpn.ADOPT"
        const val ACTION_APPLY_MODE = "app.xvpn.APPLY_MODE"
        const val ACTION_RECONNECT = "app.xvpn.RECONNECT"
        const val EXTRA_LINK = "link"
        const val EXTRA_BOOT = "boot"
        private const val TAG = "OutrunVpnService"
        private const val VERIFY_TIMEOUT_MS = 20_000L
        private const val MAX_FALLBACKS = 3
        private const val NET_DEBOUNCE_MS = 1_500L
        private const val HEALTH_INTERVAL_MS = 20_000L
        private const val HEALTH_FAILS = 2
        private val PROBE_URLS = Choices.testUrls.take(2).map { it.url }
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 1

        fun start(ctx: Context, link: String, boot: Boolean = false) = ctx.startForegroundService(
            Intent(ctx, OutrunVpnService::class.java).setAction(ACTION_START).putExtra(EXTRA_LINK, link).putExtra(EXTRA_BOOT, boot)
        )

        /** Root mode runs its own TUN, so the system VPN consent dialog is not needed. */
        fun needsPermission(ctx: Context): Boolean = !Store(ctx).rootMode && VpnService.prepare(ctx) != null

        /** Root mode only: take over a core that kept running after the app was force-stopped. */
        fun adopt(ctx: Context) = ctx.startForegroundService(
            Intent(ctx, OutrunVpnService::class.java).setAction(ACTION_ADOPT)
        )

        /** Root mode was switched on or off while connected: reconnect the current server in the newly chosen mode. */
        fun applyMode(ctx: Context) = ctx.startService(
            Intent(ctx, OutrunVpnService::class.java).setAction(ACTION_APPLY_MODE)
        )

        /** Reconnects the current server so changed connection settings take effect. */
        fun reconnect(ctx: Context) = ctx.startService(
            Intent(ctx, OutrunVpnService::class.java).setAction(ACTION_RECONNECT)
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
    private var rootCore: RootCore? = null

    private var currentLink: String? = null
    private var currentName: String = ""

    @Volatile private var wanted = false
    /** Mode the foreground notification / current session was set up for; null when no session. */
    private var sessionRoot: Boolean? = null
    private var healthJob: Job? = null
    private var netJob: Job? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNet: Network? = null
    private val underlying = ConcurrentHashMap<Network, Boolean>()
    private val validatedOnce: MutableSet<Network> = ConcurrentHashMap.newKeySet()
    private var netLost = false
    @Volatile private var waitingNet = false
    private var canSwitch = false
    private var sessionStart = 0L
    private var sessUp = 0L
    private var sessDown = 0L
    private var pendUp = 0L
    private var pendDown = 0L

    private val notificationManager: NotificationManager get() = getSystemService(NotificationManager::class.java)

    /** Root mode has no VPN profile, which Android 14 requires for the systemExempted type, so it uses specialUse. */
    private fun fg(n: Notification, root: Boolean = Store(this).rootMode) {
        if (Build.VERSION.SDK_INT >= 34) {
            val type = if (root) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            else ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            startForeground(NOTIFICATION_ID, n, type)
        } else startForeground(NOTIFICATION_ID, n)
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager
            .createNotificationChannel(NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> shutdown()
            ACTION_NEXT -> switchNext()
            ACTION_ADOPT -> adopt()
            ACTION_APPLY_MODE -> applyMode()
            ACTION_RECONNECT -> reconnectNow()
            ACTION_START -> {
                val link = intent.getStringExtra(EXTRA_LINK) ?: return START_NOT_STICKY
                begin(link, intent.getBooleanExtra(EXTRA_BOOT, false))
            }
            else -> {
                val link = Store(this).selected?.takeIf { LinkParser.parse(it) != null }
                if (link != null) begin(link)
                else {
                    fg(notification(NState.Idle, getString(R.string.app_name), "Select a server in the app"))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun adopt() {
        wanted = true
        sessionRoot = true
        fg(notification(NState.Reconnecting, "Reconnecting"), root = true)
        scope.launch {
            val rc = RootCore(this@OutrunVpnService)
            val port = runCatching { rc.attach() }.getOrNull()
            val link = Store(this@OutrunVpnService).selected?.takeIf { LinkParser.parse(it) != null }
            if (port == null || link == null) {
                if (port != null) runCatching { rc.stop() }
                AppLog.i(this@OutrunVpnService, "service", "no running root core to take over")
                shutdown()
                return@launch
            }
            val profile = LinkParser.parse(link) ?: return@launch
            rootCore = rc
            currentLink = link
            currentName = profile.name
            VpnState.activeLink.value = link
            canSwitch = nextLink() != null
            registerNetworkWatch()
            VpnState.socksPort.value = port
            VpnState.connectedAt.value = System.currentTimeMillis()
            VpnState.status.value = Status.Connected
            sessionStart = System.currentTimeMillis(); sessUp = 0; sessDown = 0; pendUp = 0; pendDown = 0
            VpnState.sessionUp.value = 0; VpnState.sessionDown.value = 0
            OutrunWidget.refresh(this@OutrunVpnService)
            AppLog.i(this@OutrunVpnService, "service", "took over the running root core for \"${profile.name}\"")
            notificationManager.notify(NOTIFICATION_ID, notification(NState.Connected, "Connected to ${profile.name}", trafficLine()))
            startStats()
            startHealth(port)
        }
    }

    private fun begin(link: String, boot: Boolean = false) {
        wanted = true
        val root = Store(this).rootMode
        sessionRoot = root
        AppLog.i(this, "service", "start requested (${if (root) "root" else "vpn service"} mode${if (boot) ", after boot" else ""})")
        fg(notification(NState.Connecting, connectingTitle(link)))
        registerNetworkWatch()
        if (boot && root) {
            // The root manager can come up a little after BOOT_COMPLETED, so ask for root again for up to a minute.
            VpnState.status.value = Status.Connecting
            scope.launch {
                var ok = false
                var tries = 0
                while (!ok && tries < 12 && wanted) {
                    ok = RootShell.granted(10_000)
                    if (!ok) { tries++; delay(5_000) }
                }
                if (!wanted) return@launch
                if (!ok) {
                    AppLog.i(this@OutrunVpnService, "boot", "root access was not available after boot")
                    VpnState.error.value = "Root access was not available after boot"
                    shutdown()
                    return@launch
                }
                connectOrWait(link)
            }
        } else connectOrWait(link)
    }

    private fun connectOrWait(link: String) {
        if (hasUnderlyingNetwork()) launchConnect(link)
        else {
            currentLink = link
            Store(this).selected = link
            VpnState.activeLink.value = link
            netLost = true
            enterWaitingForNetwork()
        }
    }

    private fun hasUnderlyingNetwork(): Boolean =
        if (netCallback == null) true else underlying.values.any { it }

    private fun isUsable(network: Network, caps: NetworkCapabilities): Boolean {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) { validatedOnce.add(network); return true }
        return network !in validatedOnce
    }

    @Synchronized
    private fun enterWaitingForNetwork() {
        AppLog.i(this, "net", "no usable network, waiting")
        waitingNet = true
        netJob?.cancel(); netJob = null
        connectJob?.cancel()
        healthJob?.cancel(); healthJob = null
        VpnState.upBytesPerSec.value = 0
        VpnState.downBytesPerSec.value = 0
        VpnState.status.value = Status.WaitingForNetwork
        OutrunWidget.refresh(this)
        notificationManager.notify(NOTIFICATION_ID, notification(NState.Waiting, "Waiting for usable network"))
    }

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

    private fun fallbackLink(exclude: Set<String>): String? {
        val store = Store(this)
        val all = store.links().filter { LinkParser.parse(it) != null && it !in exclude }
        val favs = all.filter { it in store.favorites }
        return (favs.ifEmpty { all }).firstOrNull()
    }

    /** Switches the live connection to whatever mode is now saved in settings. */
    private fun applyMode() {
        val link = currentLink
        if (!wanted || link == null) { stopSelf(); return }
        // While waiting for a network the old session is still held; the reconnect on recovery reads the new mode.
        if (waitingNet) return
        val root = Store(this).rootMode
        AppLog.i(this, "service", "switching to ${if (root) "root" else "vpn service"} mode")
        notificationManager.notify(
            NOTIFICATION_ID,
            notification(NState.Reconnecting, "Reconnecting"),
        )
        launchConnect(link)
    }

    private fun reconnectNow() {
        val link = currentLink
        if (!wanted || link == null) { stopSelf(); return }
        if (waitingNet) return
        AppLog.i(this, "service", "reconnecting to apply new settings")
        notificationManager.notify(NOTIFICATION_ID, notification(NState.Reconnecting, "Reconnecting"))
        launchConnect(link)
    }

    private fun switchNext() {
        val next = nextLink() ?: return
        val name = LinkParser.parse(next)?.name ?: ""
        notificationManager.notify(NOTIFICATION_ID, notification(NState.Connecting, connectingTitle(next)))
        wanted = true
        launchConnect(next)
    }

    private fun registerNetworkWatch() {
        if (netCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        underlying.clear(); validatedOnce.clear()
        runCatching {
            @Suppress("DEPRECATION") val all = cm.allNetworks
            all.forEach { n ->
                cm.getNetworkCapabilities(n)?.let { underlying[n] = isUsable(n, it) }
            }
        }
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                underlying[network] = isUsable(network, caps)
                reevaluateNetwork()
            }

            override fun onLost(network: Network) {
                underlying.remove(network); validatedOnce.remove(network)
                reevaluateNetwork()
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        runCatching { cm.registerNetworkCallback(request, cb); netCallback = cb }
    }

    private fun unregisterNetworkWatch() {
        netCallback?.let { cb -> runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } }
        netCallback = null
        underlying.clear(); validatedOnce.clear()
        lastNet = null; netLost = false; waitingNet = false
        netJob?.cancel(); netJob = null
    }

    @Synchronized
    private fun reevaluateNetwork() {
        val best = underlying.entries.filter { it.value }.map { it.key }.let { usable -> usable.firstOrNull { it == lastNet } ?: usable.firstOrNull() }
        if (best == null) {
            lastNet = null
            val st = VpnState.status.value
            if (wanted && !waitingNet && (st == Status.Connected || st == Status.Connecting)) { netLost = true; enterWaitingForNetwork() }
            return
        }
        val changed = lastNet != null && lastNet != best
        val recovered = lastNet == null && netLost
        lastNet = best
        if (!wanted) { waitingNet = false; return }
        runCatching { setUnderlyingNetworks(arrayOf(best)) }
        if (rootCore != null && changed && !recovered) { netLost = false; waitingNet = false; return }
        if (changed || recovered) {
            AppLog.i(this, "net", if (recovered) "network back" else "network changed")
            netLost = false
            val link = currentLink ?: return
            netJob?.cancel()
            netJob = scope.launch {
                delay(NET_DEBOUNCE_MS)
                waitingNet = false
                notificationManager.notify(NOTIFICATION_ID, notification(NState.Reconnecting, "Reconnecting"))
                launchConnect(link)
            }
        } else { netLost = false; waitingNet = false }
    }

    private fun startHealth(port: Int) {
        healthJob?.cancel()
        healthJob = scope.launch {
            var fails = 0
            while (isActive) {
                delay(HEALTH_INTERVAL_MS)
                if (waitingNet || VpnState.status.value != Status.Connected) { fails = 0; continue }
                val ok = withContext(Dispatchers.IO) { PROBE_URLS.any { probe(port, it) } }
                fails = if (ok) 0 else fails + 1
                if (!ok) AppLog.i(this@OutrunVpnService, "health", "probe failed ($fails/$HEALTH_FAILS)")
                if (fails == 1) lastNet?.let { n -> runCatching { getSystemService(ConnectivityManager::class.java)?.reportNetworkConnectivity(n, false) } }
                if (fails >= HEALTH_FAILS) {
                    val link = currentLink ?: return@launch
                    notificationManager.notify(NOTIFICATION_ID, notification(NState.Reconnecting, "Reconnecting"))
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

    /** Proton-style sizes: decimal units, two decimals ("0.00 KB/s", "12.34 MB"). */
    private fun fmtBytes(b: Long, perSecond: Boolean): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = b.toDouble(); var i = 0
        while (v >= 1000 && i < units.lastIndex) { v /= 1000; i++ }
        return String.format(Locale.US, "%.2f %s%s", v, units[i], if (perSecond) "/s" else "")
    }

    /** Same line as Proton's notification: "↓ session | speed  ↑ session | speed". */
    private fun trafficLine(): String =
        "↓ ${fmtBytes(sessDown, false)} | ${fmtBytes(VpnState.downBytesPerSec.value, true)}  " +
            "↑ ${fmtBytes(sessUp, false)} | ${fmtBytes(VpnState.upBytesPerSec.value, true)}"

    private fun prefixLength(ip: String): Int = ip.substringAfter('/', if (':' in ip) "128" else "32").toInt()

    private suspend fun connect(link: String, tried: Set<String>) {
        VpnState.error.value = null
        VpnState.status.value = Status.Connecting
        OutrunWidget.refresh(this)

        val hadSession = tun != null || core != null || rootCore != null
        teardownSession()
        currentLink = link
        if (tried.isEmpty()) Store(this).selected = link
        VpnState.activeLink.value = link
        canSwitch = nextLink() != null
        if (hadSession) delay(300)
        var x: SingBoxCore? = null
        var rc: RootCore? = null
        var pfd: ParcelFileDescriptor? = null
        var tunStarted = false
        try {
            val profile = LinkParser.parse(link) ?: error("Invalid link")
            Store(this).let { st ->
                AppLog.i(
                    this, "service",
                    "connecting to \"${profile.name}\" (${LinkParser.protocolOf(link)}), mode=${if (st.rootMode) "root" else "vpn"}, " +
                        "split=${if (st.splitEnabled) st.splitMode else "off"}, lan=${st.lanAccess}, ipv6=${st.ipv6Mode}, mtu=${st.mtu}, " +
                        "dns=${if (st.customDnsEnabled) "custom" else if (st.netShield) "netshield" else "default"}" +
                        if (tried.isNotEmpty()) ", fallback #${tried.size}" else "",
                )
            }

            val port = SingBoxCore.freePort()
            val settings = Store(this)
            if (settings.rootMode && !RootShell.granted()) {
                // Root is gone: use the normal VPN service instead. Without the VPN permission only the UI can ask for it.
                AppLog.i(this, "service", "su is not available, switching to vpn service mode")
                settings.rootMode = false
                if (VpnService.prepare(this) != null) { VpnState.rootUnavailable.value = 2; throw RootUnavailable() }
                VpnState.rootUnavailable.value = 1
            }
            if (sessionRoot != settings.rootMode) {
                sessionRoot = settings.rootMode
                // Android 14 ties the foreground type to the mode (specialUse for root, systemExempted for the VPN service).
                runCatching { fg(notification(NState.Connecting, connectingTitle(link)), settings.rootMode) }
            }
            if (settings.rootMode) {
                rc = RootCore(this)
                rootCore = rc
                val clash = if (rc.hasClashApi()) SingBoxCore.freePort() else 0
                rc.start(ConfigBuilder.buildRoot(link, port, rootDns(settings), rootTun(settings, clash)), port, clash)
                currentCoroutineContext().ensureActive()
            } else {
                // A root core from an earlier app process (or an earlier mode) would keep routing underneath the VPN service.
                if (RootCore.isRunning(this)) {
                    AppLog.i(this, "service", "stopping a leftover root core")
                    runCatching { RootCore(this).stop() }
                }
                x = SingBoxCore(this)
                core = x
                val dns = settings.dnsServers()
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
            }

            if (!verifyProxy(port)) error("Server is not responding")

            currentCoroutineContext().ensureActive()
            if (!hasUnderlyingNetwork()) {
                netLost = true
                enterWaitingForNetwork()
                return
            }
            VpnState.socksPort.value = port
            VpnState.connectedAt.value = System.currentTimeMillis()
            VpnState.status.value = Status.Connected
            OutrunWidget.refresh(this)
            AppLog.i(this, "service", "connected to \"${profile.name}\"")
            currentName = profile.name
            sessionStart = System.currentTimeMillis(); sessUp = 0; sessDown = 0; pendUp = 0; pendDown = 0
            VpnState.sessionUp.value = 0; VpnState.sessionDown.value = 0
            if (Build.VERSION.SDK_INT >= 29 && rc == null) {
                VpnState.alwaysOn.value = isAlwaysOn
                VpnState.lockdown.value = isLockdownEnabled
            } else if (rc != null) {
                VpnState.alwaysOn.value = false
                VpnState.lockdown.value = false
            }
            notificationManager.notify(NOTIFICATION_ID, notification(NState.Connected, "Connected to ${profile.name}", trafficLine()))
            startStats()
            startHealth(port)
            Store(this).pushRecent(link)
        } catch (e: Throwable) {
            if (!currentCoroutineContext().isActive) {
                if (tunStarted) runCatching { Tun2Socks.TProxyStopService() }
                runCatching { x?.stop() }
                runCatching { rc?.stop() }
                runCatching { pfd?.close() }
            } else {
                Log.w(TAG, "connect failed: ${e.javaClass.simpleName}")
                AppLog.i(this, "service", "connect failed: ${e.javaClass.simpleName}: ${e.message}")
                val noRoot = e is RootUnavailable ||
                    (e.message.orEmpty().let { "su is not available" in it || "root denied" in it })
                if (wanted && e !is NoVpnPermission && !noRoot && !hasUnderlyingNetwork()) {
                    netLost = true
                    enterWaitingForNetwork()
                    return
                }
                val next = if (e !is NoVpnPermission && !noRoot && wanted && tried.size < MAX_FALLBACKS) fallbackLink(tried + link) else null
                if (next != null) {
                    notificationManager.notify(
                        NOTIFICATION_ID, notification(NState.Error, "Error", ConnectionErrors.message(ConnectionErrors.classify(e, e is NoVpnPermission), e.message)),
                    )
                    connect(next, tried + link)
                } else if (noRoot) {
                    AppLog.i(this, "service", "root access is not available")
                    Store(this).rootMode = false
                    if (VpnState.rootUnavailable.value == 0) VpnState.rootUnavailable.value = 2
                    shutdown()
                } else {
                    val msg = ConnectionErrors.message(ConnectionErrors.classify(e, e is NoVpnPermission), e.message ?: e.javaClass.simpleName)
                    val full = if (tried.isEmpty()) msg else "$msg (tried ${tried.size + 1} servers)"
                    VpnState.error.value = full
                    shutdown(failure = full)
                }
            }
        }
    }

    private fun rootDns(settings: Store): String {
        val list = settings.dnsServers()
        return list.firstOrNull { ':' !in it } ?: list.first()
    }

    private fun rootTun(settings: Store, clashPort: Int): ConfigBuilder.RootTun {
        val include = settings.splitEnabled && settings.splitMode == "include"
        val exclude = settings.splitEnabled && !include
        val incApps = if (include) settings.splitIncludeApps.filter { it != packageName } else emptyList()
        val excApps = if (exclude) settings.splitApps.filter { it != packageName } else emptyList()
        return ConfigBuilder.RootTun(
            iface = RootCore.IFACE,
            mtu = settings.mtu,
            blockIpv6 = settings.ipv6Mode == "block",
            lan = settings.lanAccess,
            includePackages = incApps,
            excludePackages = excApps + packageName,
            includeIps = if (include) settings.splitIncludeIps else emptyList(),
            excludeIps = if (exclude) settings.splitExcludeIps else emptyList(),
            clashPort = clashPort,
        )
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
                rootCore?.let { return runCatching { it.totals() }.getOrDefault(0L to 0L) }
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
                when (VpnState.status.value) {
                    Status.Connected -> notificationManager.notify(
                        NOTIFICATION_ID,
                        notification(NState.Connected, "Connected to $currentName", trafficLine()),
                    )
                    Status.WaitingForNetwork -> notificationManager.notify(
                        NOTIFICATION_ID, notification(NState.Waiting, "Waiting for usable network"),
                    )
                    else -> {}
                }
            }
        }
    }

    /** [async] is for shutdown on the main thread: stopping the root core can take a second or two. */
    private fun teardownSession(async: Boolean = false): Thread? {
        statsJob?.cancel(); statsJob = null
        healthJob?.cancel(); healthJob = null
        finishSession()
        val rc = rootCore
        rootCore = null
        var pending: Thread? = null
        if (rc != null) {
            if (async) pending = thread(name = "root-stop") { runCatching { rc.stop() } } else runCatching { rc.stop() }
        } else runCatching { Tun2Socks.TProxyStopService() }
        runCatching { core?.stop() }
        core = null
        runCatching { tun?.close() }
        tun = null
        VpnState.upBytesPerSec.value = 0
        VpnState.downBytesPerSec.value = 0
        VpnState.speedHistory.value = emptyList()
        VpnState.socksPort.value = 0
        return pending
    }

    private fun shutdown(failure: String? = null) {
        AppLog.i(this, "service", "disconnect")
        wanted = false
        sessionRoot = null
        unregisterNetworkWatch()
        connectJob?.cancel()
        VpnState.status.value = Status.Disconnecting
        notificationManager.notify(NOTIFICATION_ID, notification(NState.Disconnecting, "Disconnecting…"))
        OutrunWidget.refresh(this)
        val pending = teardownSession(async = true)
        if (pending == null) finishShutdown(failure)
        else thread(name = "shutdown-wait") {
            runCatching { pending.join() }
            android.os.Handler(android.os.Looper.getMainLooper()).post { finishShutdown(failure) }
        }
    }

    private fun finishShutdown(failure: String?) {
        if (wanted) return // a new connection was requested while disconnecting
        VpnState.status.value = Status.Disconnected
        VpnState.activeLink.value = null
        VpnState.sessionUp.value = 0; VpnState.sessionDown.value = 0
        OutrunWidget.refresh(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
        failure?.let { notifyFailure(it) }
    }

    override fun onRevoke() = shutdown()

    override fun onDestroy() {
        finishSession()
        runCatching { core?.stop() }
        rootCore?.let { rc -> rootCore = null; thread(name = "root-stop") { runCatching { rc.stop() } } }
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

    private enum class NState { Connecting, Connected, Reconnecting, Waiting, Disconnecting, Error, Idle }

    private fun connectingTitle(link: String?): String =
        link?.let { LinkParser.parse(it)?.name }?.takeIf { it.isNotBlank() }?.let { "Connecting to $it" } ?: "Connecting"

    /** Same layout as Proton's status notification: state title, optional traffic/error line, Disconnect only while connecting/connected. */
    private fun notification(state: NState, title: String, text: String? = null, dismissible: Boolean = false): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_STATUS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = when (state) {
            NState.Connected -> R.drawable.ic_stat_outrun
            NState.Connecting, NState.Reconnecting, NState.Waiting, NState.Disconnecting -> R.drawable.ic_stat_outrun_connecting
            NState.Error, NState.Idle -> R.drawable.ic_stat_outrun_disconnected
        }
        val b = Notification.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(icon)
            .setContentIntent(pi)
            .setOngoing(!dismissible)
            .setAutoCancel(dismissible)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        if (state == NState.Connecting || state == NState.Connected) {
            b.addAction(action("Disconnect", ACTION_STOP, 1, R.drawable.ic_proton_cross))
        }
        return b.build()
    }

    /** Final failure: shown in the same status notification (same slot, title "Error"), but dismissible because the service is gone. */
    private fun notifyFailure(message: String) {
        notificationManager.notify(NOTIFICATION_ID, notification(NState.Error, "Error", message, dismissible = true))
    }
}
