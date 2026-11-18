package app.xvpn.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.xvpn.data.AppLog
import app.xvpn.data.LinkParser
import app.xvpn.data.Store

/** Connects the selected server after the phone boots, when "Auto-connect on boot" is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = Store(context)
        if (!store.autoConnectBoot) return
        val link = (store.selected ?: store.profiles().firstOrNull()?.link)?.takeIf { LinkParser.parse(it) != null }
        if (link == null) { AppLog.i(context, "boot", "auto-connect skipped: no server selected"); return }
        if (OutrunVpnService.needsPermission(context)) {
            AppLog.i(context, "boot", "auto-connect skipped: VPN permission not granted yet (connect once from the app)")
            return
        }
        AppLog.i(context, "boot", "auto-connecting after boot")
        runCatching { OutrunVpnService.start(context, link, boot = true) }
            .onFailure { AppLog.i(context, "boot", "could not start: ${it.javaClass.simpleName}: ${it.message}") }
    }
}
