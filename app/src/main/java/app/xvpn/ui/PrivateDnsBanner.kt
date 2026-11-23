package app.xvpn.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import app.xvpn.data.PrivateDns

@Composable
fun rememberPrivateDnsActive(): Boolean {
    val ctx = LocalContext.current
    var active by remember { mutableStateOf(PrivateDns.isStrictActive(ctx)) }
    DisposableEffect(ctx) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (Build.VERSION.SDK_INT < 28 || cm == null) return@DisposableEffect onDispose { }
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                active = PrivateDns.isStrict(linkProperties)
            }
            override fun onLost(network: Network) {
                active = PrivateDns.isStrictActive(ctx)
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm.unregisterNetworkCallback(cb) } }
    }
    return active
}

@Composable
fun PrivateDnsBanner(feature: String) {
    val ctx = LocalContext.current
    Column(
        Modifier.padding(horizontal = Space.Gutter, vertical = Space.Md).fillMaxWidth()
            .clip(RoundedCornerShape(Radius.Control)).background(C.Card).padding(Space.Lg),
    ) {
        Text("$feature is unavailable", style = Type.BodyMedium, color = C.Text)
        Spacer(Modifier.height(Space.Sm))
        Text(
            "Your device is using a private DNS provider, which overrides the DNS this VPN sets. Turn Private DNS off, or set it to Automatic, to use $feature.",
            style = Type.Small, color = C.Muted,
        )
        Spacer(Modifier.height(Space.Lg))
        AppButton("Network settings", {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }, Modifier.fillMaxWidth(), ButtonStyle.OutlineAccent)
    }
}
