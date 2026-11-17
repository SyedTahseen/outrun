package app.xvpn.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.flow.MutableSharedFlow

/** Proton ProtonSnackbarType: the fill colour carries the meaning. */
enum class SnackType { Success, Warning, Error, Norm }

/**
 * Replaces android.widget.Toast everywhere. Proton VPN never uses system toasts: it shows a [ProtonSnackbar]
 * (solid status fill, shapes.medium, 12dp margin, body2Regular text in textInverted).
 * Call [Snack.show] from anywhere; [AppSnackbarHost] (placed once in MainActivity) displays it.
 */
object Snack {
    internal val events = MutableSharedFlow<Pair<String, SnackType>>(extraBufferCapacity = 8)
    fun show(message: String, type: SnackType = SnackType.Norm) { events.tryEmit(message to type) }
}

private data class AppSnackVisuals(
    override val message: String,
    val type: SnackType,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short   // [PV] no action -> Short
}

@Composable
fun AppSnackbarHost(modifier: Modifier = Modifier) {
    val state = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        Snack.events.collect { (msg, type) ->
            state.currentSnackbarData?.dismiss()
            state.showSnackbar(AppSnackVisuals(msg, type))
        }
    }
    SnackbarHost(state, modifier) { data -> AppSnackbar(data) }
}

@Composable
private fun AppSnackbar(data: SnackbarData) {
    val container: Color = when ((data.visuals as? AppSnackVisuals)?.type) {
        SnackType.Success -> C.SuccessSolid   // [PC] notificationSuccess (dark)
        SnackType.Warning -> C.Amber          // [PC] notificationWarning
        SnackType.Error -> C.Red              // [PC] notificationError
        else -> C.Text                        // [PC] notificationNorm = shade100
    }
    Snackbar(
        modifier = Modifier.padding(Space.Md),                 // [PV] ProtonSnackbar: padding(12.dp)
        shape = RoundedCornerShape(Radius.Control),            // [PV] shapes.medium
        containerColor = container,
        contentColor = C.Inverted,                             // [PV] textInverted = shade0 (Cinder)
        content = { Text(data.visuals.message, style = Type.Small) },   // [PV] defaultSmallUnspecified = body2Regular
    )
}
