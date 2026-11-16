package app.xvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.IpPrefix
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

class OutrunVpnService : VpnService() {
    companion object {
        const val ACTION_START = "app.xvpn.START"
        const val ACTION_STOP = "app.xvpn.STOP"
        const val ACTION_NEXT = "app.xvpn.NEXT"
        const val EXTRA_LINK = "link"
        private const val TAG = "OutrunVpnService"
        private const val MTU = 1500
        private const val VERIFY_TIMEOUT_MS = 20_000L
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
                startForeground(NOTIFICATION_ID, notification("Outrun VPN", "Connecting…"))
                val prev = connectJob

                connectJob = scope.launch { prev?.cancelAndJoin(); connect(link) }
            }
        }
        return START_NOT_STICKY
    }

    private fun nextLink(): String? {
        val store = Store(this)
        val all = store.links().filter { LinkParser.parse(it) != null }
        val favs = all.filter { it in store.favorites }
        val pool = if (favs.size >= 2) favs else all
        if (pool.size < 2) return null
        return pool[(pool.indexOf(currentLink) + 1) % pool.size]
    }

    private fun switchNext() {
        val next = nextLink() ?: return
        val name = LinkParser.parse(next)?.name ?: ""
        notificationManager.notify(NOTIFICATION_ID, notification("Outrun VPN", "Switching to $name…"))
        val prev = connectJob
        connectJob = scope.launch { prev?.cancelAndJoin(); connect(next) }
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
        if (b >= 1_000_000) "%.1f MB/s".format(b / 1_000_000.0) else "%.0f KB/s".format(b / 1000.0)

    private suspend fun connect(link: String) {
        VpnState.error.value = null
        VpnState.status.value = Status.Connecting

        val hadSession = tun != null || core != null
        teardownSession()
        currentLink = link
        Store(this).selected = link
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
            val dns = settings.dnsServers()
            x.start(ConfigBuilder.build(link, port, dns.first()), port)
            currentCoroutineContext().ensureActive()

            val b = Builder()
                .setSession(profile.name)
                .setMtu(MTU)
                .addAddress(Tun2Socks.TUN_IPV4, 30)
            val v6 = settings.ipv6Mode != "block"
            if (v6) b.addAddress(Tun2Socks.TUN_IPV6, 126)
            dns.forEach { b.addDnsServer(it) }

            val include = settings.splitEnabled && settings.splitMode == "include"
            val exclude = settings.splitEnabled && !include
            val incApps = if (include) settings.splitIncludeApps.filter { it != packageName } else emptyList()
            val incIps = if (include) settings.splitIncludeIps else emptyList()
            if (incIps.isEmpty()) { b.addRoute("0.0.0.0", 0); b.addRoute("::", 0) }
            else incIps.forEach { ip ->
                runCatching { b.addRoute(ip.substringBefore('/'), ip.substringAfter('/', if (':' in ip) "128" else "32").toInt()) }
            }
            if (exclude && Build.VERSION.SDK_INT >= 33) settings.splitExcludeIps.forEach { ip ->
                runCatching {
                    b.excludeRoute(IpPrefix(
                        InetAddress.getByName(ip.substringBefore('/')),
                        ip.substringAfter('/', if (':' in ip) "128" else "32").toInt(),
                    ))
                }
            }

            if (incApps.isNotEmpty()) incApps.forEach { pkg -> runCatching { b.addAllowedApplication(pkg) } }
            else {
                if (exclude) settings.splitApps.forEach { pkg ->
                    if (pkg != packageName) runCatching { b.addDisallowedApplication(pkg) }
                }
                b.addDisallowedApplication(packageName)
            }
            pfd = b.establish() ?: error("VPN permission was not granted")
            tun = pfd
            currentCoroutineContext().ensureActive()

            val cfg = File(filesDir, "tun2socks.yml").apply {
                writeText(Tun2Socks.yaml(port, MTU, v6))
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
            currentName = profile.name
            sessionStart = System.currentTimeMillis(); sessUp = 0; sessDown = 0; pendUp = 0; pendDown = 0
            VpnState.sessionUp.value = 0; VpnState.sessionDown.value = 0
            if (Build.VERSION.SDK_INT >= 29) {
                VpnState.alwaysOn.value = isAlwaysOn
                VpnState.lockdown.value = isLockdownEnabled
            }
            notificationManager.notify(NOTIFICATION_ID, notification("Connected · ${profile.name}", "↓ 0 KB/s   ↑ 0 KB/s"))
            startStats()
        } catch (e: Throwable) {
            if (!currentCoroutineContext().isActive) {
                if (tunStarted) runCatching { Tun2Socks.TProxyStopService() }
                runCatching { x?.stop() }
                runCatching { pfd?.close() }
            } else {
                Log.w(TAG, "connect failed: ${e.javaClass.simpleName}")
                VpnState.error.value = e.message ?: e.javaClass.simpleName
                shutdown()
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
                VpnState.upBytesPerSec.value = (up - lastUp).coerceAtLeast(0)
                VpnState.downBytesPerSec.value = (down - lastDown).coerceAtLeast(0)
                VpnState.speedHistory.value =
                    (VpnState.speedHistory.value + (VpnState.upBytesPerSec.value to VpnState.downBytesPerSec.value)).takeLast(60)
                val dUp = (up - lastUp).coerceAtLeast(0); val dDown = (down - lastDown).coerceAtLeast(0)
                lastUp = up; lastDown = down
                synchronized(this@OutrunVpnService) {
                    sessUp += dUp; sessDown += dDown; pendUp += dUp; pendDown += dDown
                    VpnState.sessionUp.value = sessUp; VpnState.sessionDown.value = sessDown
                }
                if (++ticks % 10 == 0) flushUsage()
                if (VpnState.status.value == Status.Connected) {
                    notificationManager.notify(
                        NOTIFICATION_ID, notification("Connected · $currentName", "↓ ${rate(VpnState.downBytesPerSec.value)}   ↑ ${rate(VpnState.upBytesPerSec.value)}"),
                    )
                }
            }
        }
    }

    private fun teardownSession() {
        statsJob?.cancel(); statsJob = null
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
        connectJob?.cancel()
        teardownSession()
        VpnState.status.value = Status.Disconnected
        VpnState.activeLink.value = null
        VpnState.sessionUp.value = 0; VpnState.sessionDown.value = 0
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
