package app.xvpn.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.foundation.background
import app.xvpn.data.SubInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xvpn.data.Profile

enum class SortMode(val label: String) { Default("Default"), Name("Name"), Latency("Latency"), Protocol("Protocol") }

private fun subHost(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url.take(24)

@Composable
fun ServersScreen(
    profiles: List<Profile>,
    selectedLink: String?,
    onConnect: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onPasteClipboard: () -> Unit,
    onScanQr: () -> Unit,
    onImportFile: () -> Unit,
    onAddManual: (String) -> Boolean,   // returns true if the link was valid and added
    onRefreshSubscriptions: () -> Unit,
    onDeleteAll: () -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (Profile) -> Unit,
    onCopy: (Profile) -> Unit,
    onAddSubscription: (String) -> Unit,
    pings: Map<String, Int>,          // link -> ms, -1 = failed
    testing: Boolean,
    onTestLatency: () -> Unit,
    subscriptions: List<String>,
    sources: Map<String, String>,     // link -> subscription URL (missing = Default)
    onDeleteSubscription: (String) -> Unit,
    sort: SortMode,                       // kept by the caller so it survives switching tabs
    onSortChange: (SortMode) -> Unit,
    filter: String?,                      // selected chip: null = All, "fav", "default" or "sub:<url>"
    onFilterChange: (String?) -> Unit,
    onScrolledChange: (Boolean) -> Unit = {},
    subInfo: Map<String, SubInfo> = emptyMap(),
    onRefreshSubscription: (String) -> Unit = {},
    onCopySubscription: (String) -> Unit = {},
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var delSub by remember { mutableStateOf<String?>(null) }
    var subDialog by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { focus.requestFocus() } }

    val hasFav = profiles.any { it.link in favorites }
    val f = filter?.takeIf {
        it == "default" || (it == "fav" && hasFav) || (it.startsWith("sub:") && it.removePrefix("sub:") in subscriptions)
    }
    val shown = profiles.filter {
        when {
            f == null -> true
            f == "fav" -> it.link in favorites
            f == "default" -> sources[it.link].isNullOrEmpty()
            else -> sources[it.link] == f.removePrefix("sub:")
        } &&
            (query.isBlank() || it.name.contains(query, true) || it.host.contains(query, true))
    }.let { list ->
        // favourites keep their place; the Favorites pill is how you see them together
        when (sort) {
            SortMode.Default -> list
            SortMode.Name -> list.sortedBy { it.name.lowercase() }
            SortMode.Protocol -> list.sortedBy { it.protocol }
            SortMode.Latency -> list.sortedBy { pings[it.link]?.takeIf { ms -> ms > 0 } ?: Int.MAX_VALUE }
        }
    }

    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val headerColor by animateColorAsState(if (scrolled) C.Card else C.Bg, label = "headerColor")
    // Tell the activity so the status bar strip can follow the header colour.
    LaunchedEffect(scrolled) { onScrolledChange(scrolled) }
    DisposableEffect(Unit) { onDispose { onScrolledChange(false) } }

    Column(Modifier.fillMaxSize()) {
        // header: background colour at rest, switches to the bottom-bar colour once the list is scrolled
        Column(Modifier.fillMaxWidth().background(headerColor)) {
            Spacer(Modifier.height(20.dp))
            AnimatedContent(
                targetState = searching,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp),
                transitionSpec = {
                    val dir = if (targetState) 1 else -1
                    (fadeIn(tween(Motion.Medium, delayMillis = 60)) + slideInHorizontally(tween(Motion.Medium, easing = Motion.Enter)) { dir * it / 10 })
                        .togetherWith(fadeOut(tween(Motion.Short)) + slideOutHorizontally(tween(Motion.Short)) { -dir * it / 10 })
                },
                label = "searchBar",
            ) { isSearching ->
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                if (isSearching) {
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = C.Text, fontSize = 18.sp),
                        cursorBrush = SolidColor(C.Accent),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search servers", color = C.Muted, fontSize = 18.sp)
                            inner()
                        },
                    )
                    HeaderIcon(Icons.Filled.Close, "Close search") { searching = false; query = "" }
                } else {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text("Servers", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.clip(RoundedCornerShape(Radius.Small)).background(C.ButtonGrey).padding(horizontal = 10.dp, vertical = 3.dp),
                        ) { Text("${profiles.size}", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                    }
                    HeaderIcon(Icons.Filled.Search, "Search") { searching = true }
                    Box {
                        HeaderIcon(Icons.Filled.Add, "Add") { addMenu = true }
                        DropdownMenu(addMenu, { addMenu = false }, shape = RoundedCornerShape(Radius.Card), containerColor = C.Card) {
                            MenuRow(Icons.Outlined.QrCodeScanner, "Scan QR code") { addMenu = false; onScanQr() }
                            MenuRow(Icons.Outlined.ContentPaste, "Paste from clipboard") { addMenu = false; onPasteClipboard() }
                            MenuRow(Icons.Outlined.InsertDriveFile, "Import from file") { addMenu = false; onImportFile() }
                            MenuRow(Icons.Outlined.Edit, "Add manually") { addMenu = false; manual = true }
                            MenuRow(Icons.Outlined.Link, "Add subscription link") { addMenu = false; subDialog = true }
                        }
                    }
                    Box {
                        HeaderIcon(Icons.Filled.MoreVert, "More") { moreMenu = true }
                        DropdownMenu(moreMenu, { moreMenu = false }, shape = RoundedCornerShape(Radius.Card), containerColor = C.Card) {
                            MenuRow(Icons.Outlined.Speed, if (testing) "Testing…" else "Test latency") { moreMenu = false; if (!testing) onTestLatency() }
                            MenuRow(Icons.AutoMirrored.Outlined.Sort, "Sort by: ${sort.label}") { moreMenu = false; sortMenu = true }
                            MenuRow(Icons.Outlined.Refresh, "Update subscriptions") { moreMenu = false; onRefreshSubscriptions() }
                            MenuRow(Icons.Outlined.DeleteOutline, "Delete all servers") { moreMenu = false; onDeleteAll() }
                        }
                        DropdownMenu(sortMenu, { sortMenu = false }, shape = RoundedCornerShape(Radius.Card), containerColor = C.Card) {
                            SortMode.values().forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.label, color = if (m == sort) C.Accent else C.Text, fontSize = 17.sp) },
                                    trailingIcon = { if (m == sort) Icon(Icons.Filled.Check, null, tint = C.Accent, modifier = Modifier.size(20.dp)) },
                                    onClick = { onSortChange(m); sortMenu = false },
                                )
                            }
                        }
                    }
                }
            }
            }
            Spacer(Modifier.height(18.dp))
            // All / Default / one pill per subscription (long-press a subscription pill to delete it)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Chip("All", f == null) { onFilterChange(null) }
                if (hasFav) Chip("★ Favorites", f == "fav") { onFilterChange("fav") }
                Chip("Default", f == "default") { onFilterChange("default") }
                subscriptions.forEach { url ->
                    val n = sources.count { it.value == url }
                    Chip("${subInfo[url]?.title?.takeIf { it.isNotBlank() } ?: subHost(url)} ($n)", f == "sub:$url", onLongClick = { delSub = url }) { onFilterChange("sub:$url") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Crossfade(shown.isEmpty(), Modifier.weight(1f).fillMaxWidth(), animationSpec = tween(Motion.Medium), label = "list") { empty ->
            if (empty) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (profiles.isEmpty()) "No servers yet.\nTap + to scan a QR code, paste a link,\nimport a file or add one manually."
                        else "No servers match.",
                        color = C.Muted, fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(shown, key = { it.link }) { p ->
                        // animateItem: rows slide to their new place when sorting/filtering and fade in/out when added/removed
                        ServerRow(
                            p, p.link == selectedLink, p.link in favorites, pings[p.link], testing,
                            { onConnect(p) }, { onDelete(p) }, { onToggleFavorite(p) }, { onCopy(p) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
        // minimal footer with the selected subscription's metadata: quota, expiry, message, link
        val subUrl = f?.takeIf { it.startsWith("sub:") }?.removePrefix("sub:")
        AnimatedContent(
            targetState = subUrl,
            transitionSpec = {
                (slideInVertically(tween(Motion.Medium, easing = Motion.Enter)) { it / 2 } + fadeIn(tween(Motion.Medium)))
                    .togetherWith(slideOutVertically(tween(Motion.Short)) { it / 2 } + fadeOut(tween(Motion.Short)))
                    .using(SizeTransform(clip = true))
            },
            label = "subBar",
        ) { url ->
            if (url != null) SubscriptionBar(
                url = url, info = subInfo[url], host = subHost(url),
                onRefresh = { onRefreshSubscription(url) }, onCopy = { onCopySubscription(url) },
            ) else Spacer(Modifier.fillMaxWidth())
        }
    }

    delSub?.let { url ->
        val n = sources.count { it.value == url }
        AlertDialog(
            onDismissRequest = { delSub = null },
            containerColor = C.Card,
            shape = RoundedCornerShape(Radius.Card),
            title = { Text("Delete subscription?", color = C.Text, fontSize = 20.sp) },
            text = { Text("${subHost(url)} and its $n server(s) will be removed.", color = C.Muted, fontSize = 14.sp) },
            confirmButton = { TextButton({ onDeleteSubscription(url); delSub = null; onFilterChange(null) }) { Text("Delete", color = C.Red) } },
            dismissButton = { TextButton({ delSub = null }) { Text("Cancel", color = C.Muted) } },
        )
    }
    if (subDialog) SubscriptionDialog(onDismiss = { subDialog = false }, onAdd = { onAddSubscription(it); subDialog = false })
    if (manual) ManualDialog(onDismiss = { manual = false }, onAdd = { onAddManual(it).also { ok -> if (ok) manual = false } })
}

@Composable
internal fun HeaderIcon(icon: ImageVector, desc: String, tint: Color = C.Text, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.size(44.dp).bounceClick(CircleShape, scaleTo = 0.82f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(26.dp))
    }
}

private fun fmtBytes(b: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = b.toDouble(); var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i == 0) "$b B" else String.format(java.util.Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

private fun fmtAgo(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60000
    return when { m < 1 -> "just now"; m < 60 -> "${m}m ago"; m < 1440 -> "${m / 60}h ago"; else -> "${m / 1440}d ago" }
}

/** Compact subscription info bar pinned above the bottom dock while a subscription pill is selected. */
@Composable
private fun SubscriptionBar(url: String, info: SubInfo?, host: String, onRefresh: () -> Unit, onCopy: () -> Unit) {
    val now = System.currentTimeMillis()
    val expMs = (info?.expire ?: 0L) * 1000
    val left = expMs - now
    val days = (left + 86_399_999L) / 86_400_000L
    val expired = info != null && info.expire > 0 && left <= 0
    val exhausted = info != null && info.total > 0 && info.used >= info.total
    val expText = when {
        info == null || info.expire <= 0 -> null
        expired -> "Expired"
        left < 86_400_000L -> "${maxOf(1L, left / 3_600_000L)}h left"
        else -> "$days days left"
    }
    val expColor = when { expired -> C.Red; days <= 7 -> C.Amber; else -> C.Muted }
    Box(Modifier.fillMaxWidth().background(C.Bg).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Card)).background(C.Card).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        info?.title?.takeIf { it.isNotBlank() } ?: host, color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (expText != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(expText, color = expColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
                if (info != null && (info.total > 0 || info.used > 0)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (info.total > 0) "${fmtBytes(info.used)} of ${fmtBytes(info.total)} used" else "${fmtBytes(info.used)} used · Unlimited",
                        color = if (exhausted) C.Red else C.Muted, fontSize = 13.sp, maxLines = 1,
                    )
                    if (info.total > 0) {
                        val frac = (info.used.toFloat() / info.total).coerceIn(0f, 1f)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.ButtonGrey)) {
                            Box(
                                Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(2.dp))
                                    .background(if (exhausted) C.Red else if (frac > 0.9f) C.Amber else C.Accent),
                            )
                        }
                    }
                }
                if (info != null && info.announce.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(info.announce.trim().replace("\n", " "), color = C.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    host + if (info != null && info.fetchedAt > 0) " · updated ${fmtAgo(info.fetchedAt)}" else "",
                    color = C.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            HeaderIcon(Icons.Outlined.ContentCopy, "Copy subscription link", tint = C.Muted, onClick = onCopy)
            HeaderIcon(Icons.Outlined.Refresh, "Update subscription", tint = C.Muted, onClick = onRefresh)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = C.Text, fontSize = 17.sp) },
        leadingIcon = { Icon(icon, null, tint = C.Text, modifier = Modifier.size(22.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) C.Button else C.ButtonGrey, tween(Motion.Medium), label = "chip")
    Box(
        Modifier.height(44.dp)
            .bounceClick(RoundedCornerShape(Radius.Small), scaleTo = 0.94f, haptic = HapticFeedbackType.TextHandleMove, onLongClick = onLongClick, onClick = onClick)
            .background(bg).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun ServerRow(
    p: Profile, selected: Boolean, fav: Boolean, ping: Int?, testing: Boolean,
    onConnect: () -> Unit, onDelete: () -> Unit, onFavorite: () -> Unit, onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    // the selected server gets a raised row (card colour)
    val rowBg by animateColorAsState(if (selected) C.Card else C.Bg, tween(Motion.Medium), label = "rowBg")
    val nameColor by animateColorAsState(if (selected) C.Green else C.Text, tween(Motion.Medium), label = "rowName")
    Column(modifier.fillMaxWidth().background(rowBg)) {
      Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onConnect).padding(start = 20.dp, end = 8.dp).height(84.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagBadge(p.name)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(Flags.clean(p.name), color = nameColor, fontSize = 17.sp, maxLines = 1)
                Text("${p.host}:${p.port}", color = C.Muted, fontSize = 13.sp, maxLines = 1)
                Text(app.xvpn.data.LinkParser.describe(p.link), color = C.Muted.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
            }
            // latency result cross-fades in (…  ->  123 ms) rather than snapping
            Crossfade(ping to testing, animationSpec = tween(Motion.Medium), label = "ping") { (pg, t) ->
                when {
                    pg == null -> if (t) Text("…", color = C.Muted, fontSize = 14.sp)
                    pg < 0 -> Text("Timeout", color = C.Red, fontSize = 13.sp)
                    else -> Text("$pg ms", color = if (pg < 500) C.Green else if (pg < 1200) C.Amber else C.Red, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            FavoriteStar(fav, onFavorite)
            Box {
                HeaderIcon(Icons.Filled.MoreVert, "Options") { menu = true }
                DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(Radius.Card), containerColor = C.Card) {
                    MenuRow(Icons.Outlined.ContentCopy, "Copy") { menu = false; onCopy() }
                    MenuRow(Icons.Outlined.DeleteOutline, "Delete") { menu = false; onDelete() }
                }
            }
        }
      }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.CardBorder))
    }
}

/** Star that pops with a springy overshoot when you favourite something, and fades its colour either way. */
@Composable
private fun FavoriteStar(fav: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(fav) {
        if (first) { first = false; return@LaunchedEffect }
        if (fav) { scale.snapTo(0.5f); scale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium)) }
        else scale.animateTo(1f)
    }
    val tint by animateColorAsState(if (fav) C.Amber else C.Muted, tween(Motion.Medium), label = "star")
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    HeaderIcon(
        if (fav) Icons.Filled.Star else Icons.Outlined.StarBorder, "Favorite", tint = tint,
        modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
    ) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
}

@Composable
private fun ManualDialog(onDismiss: () -> Unit, onAdd: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        shape = RoundedCornerShape(Radius.Card),
        title = { Text("Add manually", color = C.Text, fontSize = 20.sp) },
        text = {
            Column {
                Text("Paste a vless://, trojan://, ss:// or vmess:// link (one per line).", color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = text, onValueChange = { text = it; bad = false },
                    textStyle = TextStyle(color = C.Text, fontSize = 13.sp),
                    cursorBrush = SolidColor(C.Accent),
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(Radius.Small)).background(C.Bg).padding(12.dp),
                )
                if (bad) Text("No valid link found", color = C.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton({ if (!onAdd(text)) bad = true }) { Text("Add", color = C.Accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

@Composable
private fun SubscriptionDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        shape = RoundedCornerShape(Radius.Card),
        title = { Text("Add subscription link", color = C.Text, fontSize = 20.sp) },
        text = {
            Column {
                Text("Servers from this URL are added now and when you update subscriptions.", color = C.Muted, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    textStyle = TextStyle(color = C.Text, fontSize = 14.sp),
                    cursorBrush = SolidColor(C.Accent),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Small)).background(C.Bg).padding(14.dp),
                    decorationBox = { inner ->
                        if (url.isEmpty()) Text("https://…", color = C.Muted, fontSize = 14.sp)
                        inner()
                    },
                )
            }
        },
        confirmButton = { TextButton({ if (url.isNotBlank()) onAdd(url.trim()) }) { Text("Add", color = C.Accent) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}
