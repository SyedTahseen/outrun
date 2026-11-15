package app.xvpn.vpn

import kotlinx.coroutines.flow.MutableStateFlow

enum class Status { Disconnected, Connecting, Connected }

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

    val alwaysOn = MutableStateFlow(false)
    val lockdown = MutableStateFlow(false)
}
