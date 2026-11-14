package app.xvpn.vpn

import android.net.VpnService
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet

/**
 * Thin wrapper over the Xray gomobile AAR (2dust/AndroidLibXrayLite -> libs/libv2ray.aar).
 * API follows the v2rayNG 1.8.x era of that library; if you use a different AAR version,
 * only this file needs adjusting.
 */
class XrayCore(private val service: VpnService) {

    private val callback = object : V2RayVPNServiceSupportsSet {
        override fun onEmitStatus(l: Long, s: String?): Long = 0
        override fun prepare(): Long = 0
        override fun protect(l: Long): Boolean = service.protect(l.toInt())   // keep Xray's own sockets out of the tunnel
        override fun setup(s: String?): Long = 0
        override fun shutdown(): Long = 0
    }

    private var point: V2RayPoint? = null

    val isRunning: Boolean get() = point?.isRunning == true

    fun start(configJson: String, serverHostPort: String) {
        Libv2ray.initV2Env(service.filesDir.absolutePath, "")
        val p = Libv2ray.newV2RayPoint(callback, true)
        p.configureFileContent = configJson
        p.domainName = serverHostPort
        p.runLoop(false)
        point = p
    }

    fun stop() {
        point?.stopLoop()
        point = null
    }

    /** Cumulative bytes through the outbound tagged "proxy". */
    fun uplink(): Long = point?.queryStats("proxy", "uplink") ?: 0
    fun downlink(): Long = point?.queryStats("proxy", "downlink") ?: 0
}
