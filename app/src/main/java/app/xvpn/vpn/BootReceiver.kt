package app.xvpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import app.xvpn.data.LinkParser
import app.xvpn.data.Store

/** Connects to the selected server after the phone boots when "Connect on boot" is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = Store(context)
        if (!store.connectOnBoot) return
        val link = (store.selected ?: store.profiles().firstOrNull()?.link)?.takeIf { LinkParser.parse(it) != null } ?: return
        // Without the system VPN permission there is nobody to approve the dialog at boot, so do nothing.
        if (VpnService.prepare(context) != null) return
        runCatching { OutrunVpnService.start(context.applicationContext, link) }
    }
}
