package app.xvpn.vpn

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class ErrorType { VpnPermission, ServerUnreachable, EngineMissing, EngineFailed, TunnelFailed, Generic }

object ConnectionErrors {
    fun classify(e: Throwable, noVpnPermission: Boolean = false): ErrorType {
        val m = e.message.orEmpty()
        return when {
            noVpnPermission -> ErrorType.VpnPermission
            e is SocketTimeoutException || e is ConnectException || e is UnknownHostException ||
                "not responding" in m -> ErrorType.ServerUnreachable
            "binary missing" in m -> ErrorType.EngineMissing
            "sing-box exited" in m || "did not start in time" in m || "failed to start sing-box" in m -> ErrorType.EngineFailed
            "tun2socks" in m -> ErrorType.TunnelFailed
            else -> ErrorType.Generic
        }
    }

    fun message(type: ErrorType, details: String? = null): String = when (type) {
        ErrorType.VpnPermission -> "VPN permission was not granted. Allow it and try again."
        ErrorType.ServerUnreachable -> "Couldn't connect to the VPN. Please check your connection and try again."
        ErrorType.EngineMissing -> "The VPN engine is missing for this device."
        ErrorType.EngineFailed -> "The VPN engine failed to start. Please try again in a few seconds."
        ErrorType.TunnelFailed -> "Couldn't set up the VPN tunnel. Please try again."
        ErrorType.Generic ->
            if (details.isNullOrBlank()) "Unspecified failure while connecting."
            else "Couldn't establish a VPN connection. Please try again in a few seconds. Details: ${details.lineSequence().first().take(160)}"
    }
}
