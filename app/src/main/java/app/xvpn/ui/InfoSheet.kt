package app.xvpn.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Proton's SimpleModalBottomSheet: backgroundNorm container, full-height expansion only, iconHint drag handle. */
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

/** Proton's GenericLearnMore: 16dp side padding, subheadline title, 14sp body, optional extra content. */
@Composable
fun InfoSheetContent(title: String, details: String?, subDetails: (@Composable () -> Unit)? = null) {
    Column(Modifier.padding(start = Space.Lg, end = Space.Lg, bottom = Space.Xs)) {
        Text(title, style = Type.Subheadline, color = C.Text, modifier = Modifier.padding(bottom = Space.Lg))
        details?.let { Text(it, style = Type.Small, color = C.Text, modifier = Modifier.padding(bottom = Space.Lg)) }
        subDetails?.invoke()
        Spacer(Modifier.height(Space.Sm))
    }
}
