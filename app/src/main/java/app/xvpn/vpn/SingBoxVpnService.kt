package app.xvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import app.xvpn.MainActivity
import app.xvpn.data.ConfigBuilder
import app.xvpn.data.LinkParser
import app.xvpn.data.Store
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
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

class SingBoxVpnService : VpnService() {

    companion object {
        const val ACTION_START = "app.xvpn.START"
        const val ACTION_STOP = "app.xvpn.STOP"
        const val EXTRA_LINK = "link"
        private const val MTU = 1500
        /** How long we wait for traffic to actually flow through the server before giving up. */
        private const val VERIFY_TIMEOUT_MS = 20_000L
        private val PROBE_URLS = listOf("https://www.gstatic.com/generate_204", "https://cp.cloudflare.com/generate_204")
        private const val CHANNEL = "vpn"

        fun start(ctx: Context, link: String) = ctx.startForegroundService(
            Intent(ctx, SingBoxVpnService::class.java).setAction(ACTION_START).putExtra(EXTRA_LINK, link)
        )

        fun stop(ctx: Context) = ctx.startService(
            Intent(ctx, SingBoxVpnService::class.java).setAction(ACTION_STOP)
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statsJob: Job? = null
    private var connectJob: Job? = null
    private var tun: ParcelFileDescriptor? = null
    private var core: SingBoxCore? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> shutdown()
            ACTION_START -> {
                val link = intent.getStringExtra(EXTRA_LINK) ?: return START_NOT_STICKY
                startForeground(1, notification("Connecting…"))
                val prev = connectJob
                // a new start first waits for the previous attempt to unwind, so two attempts never share the TUN
                connectJob = scope.launch { prev?.cancelAndJoin(); connect(link) }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun connect(link: String) {
        VpnState.error.value = null
        VpnState.status.value = Status.Connecting
        // Switching servers while connected: the previous session (tun2socks, sing-box, TUN) is still alive.
        // tun2socks only runs one instance, so release everything first, otherwise it fails to start.
        val hadSession = tun != null || core != null
        teardownSession()
        if (hadSession) delay(300)
        var x: SingBoxCore? = null
        var pfd: ParcelFileDescriptor? = null
        var tunStarted = false
        try {
            val profile = LinkParser.parse(link) ?: error("Invalid link")

            // 1) Start sing-box (SOCKS inbound on a free 127.0.0.1 port)
            val port = SingBoxCore.freePort()
            x = SingBoxCore(this)
            core = x
            val settings = Store(this)
            val dns = settings.dnsServers()
            x.start(ConfigBuilder.build(link, port, dns.first()), port)
            currentCoroutineContext().ensureActive()

            // 2) Create the TUN interface
            val b = Builder()
                .setSession(profile.name)
                .setMtu(MTU)
                .addAddress("10.10.14.1", 30)
                .addAddress("fd00:10:10:14::1", 126)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
            dns.forEach { b.addDnsServer(it) }
            // split tunneling: the chosen apps bypass the VPN (a VPN may only use allowed OR disallowed apps)
            if (settings.splitEnabled) settings.splitApps.forEach { pkg ->
                if (pkg != packageName) runCatching { b.addDisallowedApplication(pkg) }
            }
            pfd = b.addDisallowedApplication(packageName)   // never loop our own traffic
                .establish() ?: error("VPN permission was not granted")
            tun = pfd
            currentCoroutineContext().ensureActive()

            // 3) tun2socks: TUN fd <-> sing-box SOCKS
            val cfg = File(filesDir, "tun2socks.yml").apply {
                writeText(Tun2Socks.yaml(port, MTU))
            }
            var started = Tun2Socks.TProxyStartService(cfg.absolutePath, pfd.fd)
            if (!started) {
                // a previous instance may still be winding down: stop it and try once more
                runCatching { Tun2Socks.TProxyStopService() }
                delay(400)
                started = Tun2Socks.TProxyStartService(cfg.absolutePath, pfd.fd)
            }
            if (!started) error("tun2socks failed to start")
            tunStarted = true

            // 4) The local pieces are up, but that does not mean the server works. Only report "Connected"
            //    once a real request has gone through the proxy (this is the "Connecting…" phase in the UI).
            if (!verifyProxy(port)) error("Server is not responding")

            VpnState.socksPort.value = port
            VpnState.connectedAt.value = System.currentTimeMillis()
            VpnState.status.value = Status.Connected
            getSystemService(NotificationManager::class.java).notify(1, notification("Connected · ${profile.name}"))
            startStats()
        } catch (e: Throwable) {
            if (!currentCoroutineContext().isActive) {
                // cancelled (user pressed Cancel, or a new start replaced us): shutdown() already reported the state,
                // just release what this attempt created
                if (tunStarted) runCatching { Tun2Socks.TProxyStopService() }
                runCatching { x?.stop() }
                runCatching { pfd?.close() }
            } else {
                VpnState.error.value = e.message ?: e.javaClass.simpleName
                shutdown()
            }
        }
    }

    /** True once an HTTPS request through sing-box's SOCKS port succeeds. Retries for up to [VERIFY_TIMEOUT_MS]. */
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
        val c = URL(url).openConnection(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port))) as HttpURLConnection
        try {
            c.connectTimeout = 5000
            c.readTimeout = 5000
            c.instanceFollowRedirects = false
            c.responseCode.let { it == 204 || it == 200 }
        } finally { c.disconnect() }
    }.getOrDefault(false)

    /** Speeds come from tun2socks counters: [txPackets, txBytes, rxPackets, rxBytes]. */
    private fun startStats() {
        statsJob?.cancel()
        statsJob = scope.launch {
            fun read(): Pair<Long, Long> {
                val s = runCatching { Tun2Socks.TProxyGetStats() }.getOrNull()
                return if (s != null && s.size >= 4) s[1] to s[3] else 0L to 0L   // tx bytes, rx bytes
            }
            var (lastUp, lastDown) = read()
            while (isActive) {
                delay(1000)
                val (up, down) = read()
                VpnState.upBytesPerSec.value = (up - lastUp).coerceAtLeast(0)
                VpnState.downBytesPerSec.value = (down - lastDown).coerceAtLeast(0)
                VpnState.speedHistory.value =
                    (VpnState.speedHistory.value + (VpnState.upBytesPerSec.value to VpnState.downBytesPerSec.value)).takeLast(60)
                lastUp = up; lastDown = down
            }
        }
    }

    /** Releases the running session's resources without touching the service itself (used when switching servers). */
    private fun teardownSession() {
        statsJob?.cancel(); statsJob = null
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
        statsJob?.cancel()
        runCatching { Tun2Socks.TProxyStopService() }
        runCatching { core?.stop() }
        core = null
        runCatching { tun?.close() }
        tun = null
        VpnState.upBytesPerSec.value = 0
        VpnState.downBytesPerSec.value = 0
        VpnState.speedHistory.value = emptyList()
        VpnState.socksPort.value = 0
        VpnState.status.value = Status.Disconnected
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = shutdown()

    override fun onDestroy() {
        runCatching { core?.stop() }
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("SingBox VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
