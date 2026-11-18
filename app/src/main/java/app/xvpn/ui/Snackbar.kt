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
import kotlinx.coroutines.flow.collectLatest

enum class SnackType { Success, Warning, Error, Norm }

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
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

@Composable
fun AppSnackbarHost(modifier: Modifier = Modifier) {
    val state = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        // collectLatest: a new message replaces the one on screen instead of queueing behind its ~4s display time.
        Snack.events.collectLatest { (msg, type) ->
            state.currentSnackbarData?.dismiss()
            state.showSnackbar(AppSnackVisuals(msg, type))
        }
    }
    SnackbarHost(state, modifier) { data -> AppSnackbar(data) }
}

@Composable
private fun AppSnackbar(data: SnackbarData) {
    val container: Color = when ((data.visuals as? AppSnackVisuals)?.type) {
        SnackType.Success -> C.SuccessSolid
        SnackType.Warning -> C.Amber
        SnackType.Error -> C.Red
        else -> C.Text
    }
    Snackbar(
        modifier = Modifier.padding(Space.Md),
        shape = RoundedCornerShape(Radius.Control),
        containerColor = container,
        contentColor = C.Inverted,
        content = { Text(data.visuals.message, style = Type.Small) },
    )
}
