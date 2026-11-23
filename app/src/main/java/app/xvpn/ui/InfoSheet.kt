package app.xvpn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = C.Bg,
        contentColor = C.Text,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle(color = C.TextHint) },
        content = content,
    )
}

@Composable
fun InfoSheetContent(
    title: String, details: String?,
    learnMoreLabel: String? = null, onLearnMore: (() -> Unit)? = null,
    subDetails: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.padding(start = Space.Lg, end = Space.Lg, bottom = Space.Xs)) {
        Text(title, style = Type.Subheadline, color = C.Text, modifier = Modifier.padding(bottom = Space.Lg))
        details?.let { Text(it, style = Type.Small, color = C.Text, modifier = Modifier.padding(bottom = Space.Lg)) }
        subDetails?.invoke()
        Spacer(Modifier.height(Space.Sm))

        if (learnMoreLabel != null && onLearnMore != null) {
            AppButton(
                learnMoreLabel, onLearnMore, Modifier.fillMaxWidth().padding(bottom = Space.Md),
                ButtonStyle.OutlineAccent, trailingIcon = ProtonIcons.ArrowOutSquare,
            )
        }
    }
}
