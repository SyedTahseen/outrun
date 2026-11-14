package app.xvpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow

enum class Status { Disconnected, Connecting, Connected }

/** Process-wide VPN state observed by the UI. */
object VpnState {
    val status = MutableStateFlow(Status.Disconnected)
    val connectedAt = MutableStateFlow(0L)       // epoch millis
    val upBytesPerSec = MutableStateFlow(0L)
    val downBytesPerSec = MutableStateFlow(0L)
    val error = MutableStateFlow<String?>(null)
}
