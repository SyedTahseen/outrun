package app.xvpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.xvpn.ui.LogScreen
import app.xvpn.ui.OutrunApp
import app.xvpn.ui.setUpEdgeToEdge

class LogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpEdgeToEdge()
        val info = intent.getStringExtra(EXTRA_INFO).orEmpty()
        setContent { OutrunApp { LogScreen(info, onBack = ::finish) } }
    }

    companion object { const val EXTRA_INFO = "info" }
}
