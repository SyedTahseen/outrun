package app.xvpn.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext

/** The app's own recent log output (apps can only read their own entries). */
private suspend fun readLogs(): List<String> = withContext(Dispatchers.IO) {
    runCatching {
        val p = ProcessBuilder("logcat", "-d", "-t", "400", "-v", "time").redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().use { it.readText() }
        p.destroy()
        text.trim().ifEmpty { "No log entries yet." }.lines()
    }.getOrElse { listOf("Couldn't read logs: ${it.message}") }
}

/** [PV] LogActivity: its own screen (back arrow, "Log", share). Lines stream in and the list stays at the bottom unless the user scrolled up. */
@Composable
fun LogScreen(info: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var lines by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) {
        while (true) { lines = readLogs(); delay(2000) }
    }

    val listState = rememberLazyListState()
    var pinned by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { !it }.collect { pinned = !listState.canScrollForward }
    }
    LaunchedEffect(lines) {
        val l = lines ?: return@LaunchedEffect
        if (pinned && l.isNotEmpty()) listState.scrollToItem(l.lastIndex)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader("Log", onBack = onBack) {
            IconAction(ProtonIcons.ArrowUpFromSquare, "Share logs") {
                lines?.let { shareLogs(ctx, info, it) }
            }
        }
        val shown = lines
        if (shown == null) {
            Text("Loading…", style = Type.Small, color = C.Muted, modifier = Modifier.padding(horizontal = Space.Gutter))
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(), state = listState,
                contentPadding = PaddingValues(start = Space.Gutter, end = Space.Gutter, bottom = Space.Lg),
            ) {
                items(shown.size) { i -> Text(shown[i], style = Type.LogLine, color = C.Text) }
            }
        }
    }
}

private fun shareLogs(ctx: Context, info: String, lines: List<String>) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Outrun VPN logs").putExtra(Intent.EXTRA_TEXT, info + "\n\n" + lines.joinToString("\n"))
    runCatching { ctx.startActivity(Intent.createChooser(send, "Share logs")) }
        .onFailure { Toast.makeText(ctx, "Couldn't share logs", Toast.LENGTH_SHORT).show() }
}
