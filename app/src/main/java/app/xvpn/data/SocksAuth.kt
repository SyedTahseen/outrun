package app.xvpn.data

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-session credentials for the loopback SOCKS inbound (and the Clash API in root mode). Without them any app on the
 * phone could scan localhost, use the tunnel, or read the connection list of the root core.
 */
class Creds(val user: String, val pass: String, val secret: String) {
    companion object {
        private val rng = SecureRandom()
        private fun token() = ByteArray(16).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        fun random() = Creds("outrun", token(), token())
    }
}

object SocksAuth {
    private val byPort = ConcurrentHashMap<Int, Creds>()

    @Volatile private var installed = false

    fun register(port: Int, creds: Creds) { byPort[port] = creds; install() }

    fun unregister(port: Int) { byPort.remove(port) }

    fun credsFor(port: Int): Creds? = byPort[port]

    /** HttpURLConnection through a SOCKS proxy asks the default Authenticator for the proxy's user name and password. */
    @Synchronized
    private fun install() {
        if (installed) return
        Authenticator.setDefault(object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication? {
                if (requestingProtocol?.startsWith("SOCKS", ignoreCase = true) != true) return null
                val c = byPort[requestingPort] ?: return null
                return PasswordAuthentication(c.user, c.pass.toCharArray())
            }
        })
        installed = true
    }
}
