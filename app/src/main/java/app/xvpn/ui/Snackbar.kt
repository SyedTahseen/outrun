package app.xvpn.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

enum class SnackType { Success, Warning, Error, Norm }

internal class SnackEvent(
    val message: String,
    val type: SnackType,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
)

object Snack {
    internal val events = MutableSharedFlow<SnackEvent>(extraBufferCapacity = 8)

    fun show(message: String, type: SnackType = SnackType.Norm, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        events.tryEmit(SnackEvent(message, type, actionLabel, onAction))
    }
}

private data class AppSnackVisuals(
    override val message: String,
    override val actionLabel: String?,
    val type: SnackType,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

@Composable
fun AppSnackbarHost(modifier: Modifier = Modifier) {
    val state = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {

        Snack.events.collectLatest { e ->
            state.currentSnackbarData?.dismiss()
            val result = state.showSnackbar(AppSnackVisuals(e.message, e.actionLabel, e.type))
            if (result == SnackbarResult.ActionPerformed) e.onAction?.invoke()
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
    val label = data.visuals.actionLabel
    val action: (@Composable () -> Unit)? = if (label != null) {
        @Composable {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = C.Inverted),
                onClick = { data.performAction() },
                content = { Text(label, style = Type.SmallMedium) },
            )
        }
    } else null
    Snackbar(
        modifier = Modifier.padding(Space.Md),
        action = action,
        shape = RoundedCornerShape(Radius.Control),
        containerColor = container,
        contentColor = C.Inverted,
        actionContentColor = C.Inverted,
        content = { Text(data.visuals.message, style = Type.Small) },
    )
}
