package app.xvpn.vpn

import android.content.Context
import android.provider.Settings
import android.util.Base64
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.security.MessageDigest

/**
 * Thin wrapper over the Xray gomobile AAR (2dust/AndroidLibXrayLite -> libs/libv2ray.aar),
 * written against its CoreController API (initCoreEnv / newCoreController / startLoop / stopLoop).
 *
 * Xray runs with only a local SOCKS inbound (tunFd = 0); packets reach it through tun2socks.
 * Its outbound sockets bypass the VPN because XrayVpnService excludes this app from the tunnel.
 */
class XrayCore(private val context: Context) {

    private val callback = object : CoreCallbackHandler {
        override fun startup(): Long = 0
        override fun shutdown(): Long = 0
        override fun onEmitStatus(l: Long, s: String?): Long = 0
    }

    private var controller: CoreController? = null

    val isRunning: Boolean get() = controller?.isRunning == true

    fun start(configJson: String) {
        Libv2ray.initCoreEnv(context.filesDir.absolutePath, xudpBaseKey())
        val c = Libv2ray.newCoreController(callback)
        c.startLoop(configJson, 0)
        controller = c
    }

    fun stop() {
        runCatching { controller?.stopLoop() }
        controller = null
    }

    /** Xray wants a base64 (raw URL) 32-byte key for XUDP; derive a stable one per device. */
    private fun xudpBaseKey(): String {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "xvpn"
        val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
