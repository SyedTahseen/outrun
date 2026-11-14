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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class XrayVpnService : VpnService() {

    companion object {
        const val ACTION_START = "app.xvpn.START"
        const val ACTION_STOP = "app.xvpn.STOP"
        const val EXTRA_LINK = "link"
        private const val MTU = 1500
        private const val CHANNEL = "vpn"

        fun start(ctx: Context, link: String) = ctx.startForegroundService(
            Intent(ctx, XrayVpnService::class.java).setAction(ACTION_START).putExtra(EXTRA_LINK, link)
        )

        fun stop(ctx: Context) = ctx.startService(
            Intent(ctx, XrayVpnService::class.java).setAction(ACTION_STOP)
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statsJob: Job? = null
    private var tun: ParcelFileDescriptor? = null
    private var core: XrayCore? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> shutdown()
            ACTION_START -> {
                val link = intent.getStringExtra(EXTRA_LINK) ?: return START_NOT_STICKY
                startForeground(1, notification("Connecting…"))
                scope.launch { connect(link) }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun connect(link: String) {
        VpnState.error.value = null
        VpnState.status.value = Status.Connecting
        try {
            val profile = LinkParser.parse(link) ?: error("Invalid link")

            // 1) Start Xray (SOCKS inbound on 127.0.0.1:10808)
            val x = XrayCore(this)
            x.start(ConfigBuilder.build(link))
            core = x

            // 2) Create the TUN interface
            val pfd = Builder()
                .setSession(profile.name)
                .setMtu(MTU)
                .addAddress("10.10.14.1", 30)
                .addAddress("fd00:10:10:14::1", 126)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addDisallowedApplication(packageName)   // never loop our own traffic
                .establish() ?: error("VPN permission was not granted")
            tun = pfd

            // 3) tun2socks: TUN fd <-> Xray SOCKS
            val cfg = File(filesDir, "tun2socks.yml").apply {
                writeText(Tun2Socks.yaml(ConfigBuilder.SOCKS_PORT, MTU))
            }
            if (!Tun2Socks.TProxyStartService(cfg.absolutePath, pfd.fd)) error("tun2socks failed to start")

            VpnState.connectedAt.value = System.currentTimeMillis()
            VpnState.status.value = Status.Connected
            getSystemService(NotificationManager::class.java).notify(1, notification("Connected · ${profile.name}"))
            startStats()
        } catch (e: Throwable) {
            VpnState.error.value = e.message ?: e.javaClass.simpleName
            shutdown()
        }
    }

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
                lastUp = up; lastDown = down
            }
        }
    }

    private fun shutdown() {
        statsJob?.cancel()
        runCatching { Tun2Socks.TProxyStopService() }
        runCatching { core?.stop() }
        core = null
        runCatching { tun?.close() }
        tun = null
        VpnState.upBytesPerSec.value = 0
        VpnState.downBytesPerSec.value = 0
        VpnState.status.value = Status.Disconnected
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = shutdown()

    override fun onDestroy() {
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
            .setContentTitle("Xray VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
