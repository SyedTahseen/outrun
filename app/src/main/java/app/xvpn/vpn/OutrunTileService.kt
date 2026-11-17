package app.xvpn.vpn

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.xvpn.MainActivity
import app.xvpn.R
import app.xvpn.data.LinkParser
import app.xvpn.data.Store
import app.xvpn.ui.Flags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Quick Settings tile: tap to connect to the selected server or disconnect. */
class OutrunTileService : TileService() {
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = CoroutineScope(Dispatchers.Main).launch {
            combine(VpnState.status, VpnState.activeLink) { s, l -> s to l }.collect { (s, l) -> update(s, l) }
        }
    }

    override fun onStopListening() {
        job?.cancel(); job = null
        super.onStopListening()
    }

    private fun update(status: Status, active: String?) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.app_name)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_outrun)
        tile.state = if (status == Status.Disconnected) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        if (Build.VERSION.SDK_INT >= 29) {
            val name = (active ?: Store(this).selected)?.let { LinkParser.parse(it)?.name }?.let { Flags.clean(it) }
            tile.subtitle = when (status) {
                Status.Connected -> name ?: "Connected"
                Status.Connecting -> "Connecting…"
                Status.Disconnected -> "Off"
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (VpnState.status.value != Status.Disconnected) {
            OutrunVpnService.stop(this)
            return
        }
        val store = Store(this)
        val link = (store.selected ?: store.profiles().firstOrNull()?.link)?.takeIf { LinkParser.parse(it) != null }
        if (link == null || VpnService.prepare(this) != null) openApp() else OutrunVpnService.start(this, link)
    }

    /** No server picked yet, or the system VPN permission is still missing: the app has to be opened first. */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }
}
