package app.xvpn.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build

object PrivateDns {
    fun isStrict(lp: LinkProperties?): Boolean =
        Build.VERSION.SDK_INT >= 28 && lp != null && lp.isPrivateDnsActive && lp.privateDnsServerName != null

    fun isStrictActive(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return runCatching { cm.getLinkProperties(cm.activeNetwork) }.getOrNull().let(::isStrict)
    }
}
