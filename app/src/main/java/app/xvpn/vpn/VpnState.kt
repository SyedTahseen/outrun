package app.xvpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow

enum class Status { Disconnected, Connecting, WaitingForNetwork, Connected, Disconnecting }

object VpnState {
    val status = MutableStateFlow(Status.Disconnected)
    val connectedAt = MutableStateFlow(0L)
    val upBytesPerSec = MutableStateFlow(0L)
    val downBytesPerSec = MutableStateFlow(0L)
    val error = MutableStateFlow<String?>(null)
    val socksPort = MutableStateFlow(0)
    val speedHistory = MutableStateFlow<List<Pair<Long, Long>>>(emptyList())

    val sessionUp = MutableStateFlow(0L)
    val sessionDown = MutableStateFlow(0L)

    val activeLink = MutableStateFlow<String?>(null)

    /** Root mode is on but su is missing or root was revoked. 1 = already fell back to VPN mode, 2 = the UI must reconnect (needs the VPN consent dialog). */
    val rootUnavailable = MutableStateFlow(0)

    val alwaysOn = MutableStateFlow(false)
    val lockdown = MutableStateFlow(false)
}
