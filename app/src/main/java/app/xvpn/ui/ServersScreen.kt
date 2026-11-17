package app.xvpn.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.xvpn.data.ConfigBuilder
import app.xvpn.data.LinkParser
import app.xvpn.data.Profile
import app.xvpn.data.SubInfo
import java.net.URI

enum class SortMode(val label: String) { Default("Default"), Name("Name"), Latency("Latency"), Protocol("Protocol") }

private fun subHost(url: String): String =
    runCatching { URI(url).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url.take(24)

@Composable
fun ServersScreen(
    profiles: List<Profile>,
    selectedLink: String?,
    onConnect: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onPasteClipboard: () -> Unit,
    onScanQr: () -> Unit,
    onImportFile: () -> Unit,
    onAddManual: (String) -> Boolean,
    onRefreshSubscriptions: () -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteMany: (List<String>) -> Unit = {},
    favorites: Set<String>,
    onToggleFavorite: (Profile) -> Unit,
    onCopy: (Profile) -> Unit,
    onAddSubscription: (String) -> Unit,
    pings: Map<String, Int>,
    testing: Boolean,
    onTestLatency: () -> Unit,
    subscriptions: List<String>,
    sources: Map<String, String>,
    onDeleteSubscription: (String) -> Unit,
    sort: SortMode,
    onSortChange: (SortMode) -> Unit,
    filter: String?,
    onFilterChange: (String?) -> Unit,
    onScrolledChange: (Boolean) -> Unit = {},
    subInfo: Map<String, SubInfo> = emptyMap(),
    onCopySubscription: (String) -> Unit = {},
    onConnectFastest: () -> Unit = {},
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var addMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var delSub by remember { mutableStateOf<String?>(null) }
    var subDialog by remember { mutableStateOf(false) }
    var menuPage by remember { mutableStateOf("main") }
    var confirmDel by remember { mutableStateOf<DelAction?>(null) }
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

        when (sort) {
            SortMode.Default -> list
            SortMode.Name -> list.sortedBy { it.name.lowercase() }
            SortMode.Protocol -> list.sortedBy { it.protocol }
            SortMode.Latency -> list.sortedBy { pings[it.link]?.takeIf { ms -> ms > 0 } ?: Int.MAX_VALUE }
        }
    }

    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }

    ReportScrolled(scrolled, onScrolledChange)

    Column(Modifier.fillMaxSize()) {
        HeaderSurface(scrolled) {
            AnimatedContent(
                targetState = searching,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = { fadeTransition() },
                label = "serversHeader",
            ) { isSearch ->
                if (isSearch) {
                    HeaderBar {
                        Row(Modifier.fillMaxWidth().height(Sizes.Control), verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                textStyle = Type.Body.copy(color = C.Text),
                                cursorBrush = SolidColor(C.Accent),
                                modifier = Modifier.weight(1f).focusRequester(focus),
                                decorationBox = { inner ->
                                    Box {
                                        if (query.isEmpty()) Text("Search servers", style = Type.Body, color = C.Muted)
                                        inner()
                                    }
                                },
                            )
                            IconAction(ProtonIcons.Cross, "Close search") { searching = false; query = "" }
                        }
                    }
                } else {
                    ScreenHeaderContent("Servers", scrolled = scrolled) {
                        IconAction(ProtonIcons.Magnifier, "Search") { searching = true }
                        Box {
                            IconAction(ProtonIcons.Plus, "Add") { addMenu = true }
                            AppDropdownMenu(addMenu, { addMenu = false }) {
                                AppMenuItem(ProtonIcons.QrCode, "Scan QR code") { addMenu = false; onScanQr() }
                                AppMenuItem(Icons.Outlined.ContentPaste, "Paste from clipboard") { addMenu = false; onPasteClipboard() }
                                AppMenuItem(ProtonIcons.File, "Import from file") { addMenu = false; onImportFile() }
                                AppMenuItem(ProtonIcons.Pen, "Add manually") { addMenu = false; manual = true }
                                AppMenuItem(ProtonIcons.Link, "Add subscription link") { addMenu = false; subDialog = true }
                            }
                        }
                        Box {
                            IconAction(ProtonIcons.ThreeDotsHorizontal, "More") { menuPage = "main"; moreMenu = true }
                            AppDropdownMenu(moreMenu, { moreMenu = false }, Modifier.widthIn(min = 220.dp)) {
                                        when (menuPage) {
                                            "sort" -> {
                                                AppMenuHeader("Sort") { menuPage = "main" }
                                                SortMode.entries.forEach { m ->
                                                    AppMenuChoice(m.label, m == sort) { onSortChange(m); moreMenu = false }
                                                }
                                            }
                                            "delete" -> {
                                                AppMenuHeader("Delete") { menuPage = "main" }
                                                val invalid = remember(profiles) { invalidLinks(profiles) }
                                                val dupes = remember(profiles, favorites, selectedLink) {
                                                    duplicateLinks(profiles, favorites + listOfNotNull(selectedLink))
                                                }
                                                val dead = remember(profiles, pings) { profiles.filter { pings[it.link] == -1 }.map { it.link } }
                                                AppMenuItem(ProtonIcons.ExclamationCircle, "Invalid", destructive = true, enabled = invalid.isNotEmpty(), trailing = { CountText(invalid.size) }) {
                                                    moreMenu = false
                                                    confirmDel = DelAction("Delete ${invalid.size} invalid server(s)?", "These links can't be turned into a working configuration.", invalid)
                                                }
                                                AppMenuItem(ProtonIcons.SquaresInSquare, "Duplicates", destructive = true, enabled = dupes.isNotEmpty(), trailing = { CountText(dupes.size) }) {
                                                    moreMenu = false
                                                    confirmDel = DelAction("Delete ${dupes.size} duplicate(s)?", "One copy of each server is kept. Favorites and the selected server are kept first.", dupes)
                                                }
                                                AppMenuItem(Icons.Outlined.WifiOff, "Unreachable", destructive = true, enabled = dead.isNotEmpty(), trailing = { CountText(dead.size) }) {
                                                    moreMenu = false
                                                    confirmDel = DelAction("Delete ${dead.size} unreachable server(s)?", "These failed the last latency test.", dead)
                                                }
                                                AppMenuItem(ProtonIcons.Trash, "All servers", destructive = true) {
                                                    moreMenu = false
                                                    confirmDel = DelAction("Delete all servers?", "All ${profiles.size} server(s) will be removed.", null)
                                                }
                                            }
                                            else -> {
                                                AppMenuItem(ProtonIcons.Bolt, if (testing) "Testing…" else "Test latency") { moreMenu = false; if (!testing) onTestLatency() }
                                                AppMenuItem(ProtonIcons.LinesLongToSmall, "Sort", trailing = { Chevron() }) { menuPage = "sort" }
                                                AppMenuItem(ProtonIcons.ArrowsRotate, "Update subs") { moreMenu = false; onRefreshSubscriptions() }
                                                AppMenuItem(ProtonIcons.Trash, "Delete", trailing = { Chevron() }) { menuPage = "delete" }
                                            }
                                        }
                                    }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.Gutter, vertical = Space.Sm),
                horizontalArrangement = Arrangement.spacedBy(Space.Sm),
            ) {
                SelectChip("All", f == null) { onFilterChange(null) }
                if (hasFav) SelectChip("Favorites", f == "fav") { onFilterChange("fav") }
                SelectChip("Default", f == "default") { onFilterChange("default") }
                subscriptions.forEach { url ->
                    val n = sources.count { it.value == url }
                    SelectChip(
                        "${subInfo[url]?.title?.takeIf { it.isNotBlank() } ?: subHost(url)} ($n)", f == "sub:$url",
                        onLongClick = { delSub = url },
                    ) { onFilterChange("sub:$url") }
                }
            }
        }

        AnimatedVisibility(
            visible = testing,
            enter = expandVertically(tween(Motion.Medium)) + fadeIn(tween(Motion.Medium)),
            exit = shrinkVertically(tween(Motion.Medium)) + fadeOut(tween(Motion.Fast)),
        ) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = C.Accent, trackColor = C.Control)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(targetState = shown.isEmpty(), animationSpec = tween(Motion.Medium), label = "serversList") { empty ->
                if (empty) {
                    EmptyState(
                        if (profiles.isEmpty()) "No servers yet.\nTap + to scan a QR code, paste a link,\nimport a file or add one manually."
                        else "No servers match.",
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize().verticalScrollbar(listState), state = listState) {
                        item(key = "count") {
                            Row(Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.Lg), verticalAlignment = Alignment.CenterVertically) {
                                Text("All servers (", style = Type.Small, color = C.Muted, modifier = Modifier.padding(vertical = Space.Sm))
                                AnimatedContent(
                                    targetState = profiles.size,
                                    transitionSpec = { numberTransition(up = targetState > initialState) },
                                    label = "serverCount",
                                ) { n -> Text("$n", style = Type.Small, color = C.Muted, modifier = Modifier.padding(vertical = Space.Sm)) }
                                Text(")", style = Type.Small, color = C.Muted, modifier = Modifier.padding(vertical = Space.Sm))
                            }
                        }
                        if (query.isBlank() && f == null) item(key = "fastest") {
                            FastestRow(testing, onConnectFastest)
                        }
                        items(shown, key = { it.link }) { p ->
                            ServerRow(
                                p, p.link == selectedLink, p.link in favorites, pings[p.link], testing,
                                { onConnect(p) }, { onDelete(p) }, { onToggleFavorite(p) }, { onCopy(p) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }

        val subUrl = f?.takeIf { it.startsWith("sub:") }?.removePrefix("sub:")

        val lastSub = remember { mutableStateOf<String?>(null) }
        SideEffect { if (subUrl != null) lastSub.value = subUrl }
        val barUrl = subUrl ?: lastSub.value
        AnimatedVisibility(
            visible = subUrl != null,
            enter = expandVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeIn(tween(Motion.Medium)),
            exit = shrinkVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeOut(tween(Motion.Fast)),
        ) {
            if (barUrl != null) SubscriptionBar(
                info = subInfo[barUrl], host = subHost(barUrl),
                onCopy = { onCopySubscription(barUrl) },
            )
        }
    }

    confirmDel?.let { d ->
        AppDialog(
            title = d.title, confirmLabel = "Delete", confirmColor = C.Red,
            onConfirm = {
                if (d.links == null) onDeleteAll() else onDeleteMany(d.links)
                confirmDel = null
            },
            onDismiss = { confirmDel = null },
        ) { Text(d.text, style = Type.Small, color = C.Text) }
    }
    delSub?.let { url ->
        val n = sources.count { it.value == url }
        AppDialog(
            title = "Delete subscription?", confirmLabel = "Delete", confirmColor = C.Red,
            onConfirm = { onDeleteSubscription(url); delSub = null; onFilterChange(null) },
            onDismiss = { delSub = null },
        ) { Text("${subHost(url)} and its $n server(s) will be removed.", style = Type.Small, color = C.Text) }
    }
    if (subDialog) SubscriptionDialog(onDismiss = { subDialog = false }, onAdd = { onAddSubscription(it); subDialog = false })
    if (manual) ManualDialog(onDismiss = { manual = false }, onAdd = { onAddManual(it).also { ok -> if (ok) manual = false } })
}

@Composable
private fun SubscriptionBar(info: SubInfo?, host: String, onCopy: () -> Unit) {
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
    val hasUsage = info != null && (info.total > 0 || info.used > 0)
    Column(Modifier.fillMaxWidth().background(C.Card)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.Gutter, end = Space.Xs, top = Space.Sm, bottom = Space.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        info?.title?.takeIf { it.isNotBlank() } ?: host, style = Type.Small, color = C.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (expText != null) {
                        Spacer(Modifier.width(Space.Sm))
                        Text(expText, style = Type.SmallMedium, color = expColor, maxLines = 1)
                    }
                }
                if (hasUsage && info != null) {
                    Text(
                        if (info.total > 0) "${fmtSize(info.used, binary = true)} of ${fmtSize(info.total, binary = true)} used" else "${fmtSize(info.used, binary = true)} used · Unlimited",
                        style = Type.Caption, color = if (exhausted) C.Red else C.Muted, maxLines = 1,
                    )
                    if (info.total > 0) {
                        val frac = (info.used.toFloat() / info.total).coerceIn(0f, 1f)
                        Spacer(Modifier.height(Space.Xs))
                        AppProgressBar(frac, if (exhausted) C.Red else if (frac > 0.9f) C.Amber else C.Accent)
                    }
                }
            }
            IconAction(ProtonIcons.SquaresInSquare, "Copy subscription link", tint = C.Muted, onClick = onCopy)
        }
    }
}

private class DelAction(val title: String, val text: String, val links: List<String>?)

private fun invalidLinks(profiles: List<Profile>): List<String> =
    profiles.filter { runCatching { ConfigBuilder.outbound(it.link) }.isFailure }.map { it.link }

private fun duplicateLinks(profiles: List<Profile>, keep: Set<String>): List<String> {
    val groups = LinkedHashMap<String, MutableList<String>>()
    for (p in profiles) {
        val key = runCatching { ConfigBuilder.outbound(p.link).apply { remove("tag") }.toString() }
            .getOrDefault(p.link.substringBefore('#'))
        groups.getOrPut(key) { ArrayList() } += p.link
    }
    val out = ArrayList<String>()
    for (g in groups.values) {
        if (g.size < 2) continue
        val keeper = g.firstOrNull { it in keep } ?: g.first()
        g.filterTo(out) { it != keeper }
    }
    return out
}

@Composable
private fun Chevron() { Icon(ProtonIcons.ChevronRight, null, tint = C.Muted, modifier = Modifier.size(Sizes.Icon)) }

@Composable
private fun CountText(n: Int) { Text("$n", style = Type.Small, color = C.Muted) }

@Composable
private fun ServerRow(
    p: Profile, selected: Boolean, fav: Boolean, ping: Int?, testing: Boolean,
    onConnect: () -> Unit, onDelete: () -> Unit, onFavorite: () -> Unit, onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().background(C.Bg)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onConnect).heightIn(min = Sizes.ServerRow)
                .padding(start = Space.Gutter, end = Space.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagBadge(p.name)
            Spacer(Modifier.width(Space.Md))
            Column(Modifier.weight(1f).padding(vertical = Space.Md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Flags.clean(p.name), style = Type.Body, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (selected) ActiveDot(Modifier.padding(start = Space.Sm))
                }
                Text("${p.host}:${p.port}", style = Type.Small, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Space.Xs))
                Text(LinkParser.describe(p.link), style = Type.Caption, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PingLabel(ping, testing)
            FavStar(fav, onFavorite)
            Box {
                IconAction(ProtonIcons.ThreeDotsHorizontal, "Options") { menu = true }
                AppDropdownMenu(menu, { menu = false }) {
                    AppMenuItem(ProtonIcons.SquaresInSquare, "Copy") { menu = false; onCopy() }
                    AppMenuItem(ProtonIcons.Trash, "Delete") { menu = false; onDelete() }
                }
            }
        }
        HairlineDivider()
    }
}

@Composable
private fun FastestRow(testing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(C.Bg)) {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !testing, onClick = onClick).heightIn(min = Sizes.ServerRow)
                .padding(start = Space.Gutter, end = Space.Gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FastestFlag()
            Spacer(Modifier.width(Space.Md))
            Column(Modifier.weight(1f).padding(vertical = Space.Md)) {
                Text("Fastest server", style = Type.Body, color = C.Text, maxLines = 1)
                if (testing) Text("Testing servers…", style = Type.Caption, color = C.Muted, maxLines = 1)
            }
        }
        HairlineDivider()
    }
}

@Composable
private fun PingLabel(ping: Int?, testing: Boolean) {
    AnimatedContent(
        targetState = ping to testing,
        transitionSpec = {
            ContentTransform(
                fadeIn(tween(Motion.Short, delayMillis = Motion.Overlap / 2)) + slideInVertically(tween(Motion.Short)) { it / 3 },
                fadeOut(tween(Motion.Fast)),
                0f,
                SizeTransform(clip = false),
            )
        },
        label = "ping",
    ) { (p, t) ->
        when {
            p == null -> if (t) Text("…", style = Type.Small, color = C.Muted)
            p < 0 -> Text("Timeout", style = Type.Small, color = C.Red)
            else -> Text("$p ms", style = Type.SmallMedium, color = if (p < 500) C.Green else if (p < 1200) C.Amber else C.Red)
        }
    }
}

@Composable
private fun FavStar(fav: Boolean, onClick: () -> Unit) {
    val tick = rememberTick()
    IconAction(
        if (fav) ProtonIcons.StarFilled else ProtonIcons.Star, "Favorite",
        tint = if (fav) C.Amber else C.Muted,
    ) { tick(); onClick() }
}

@Composable
private fun ManualDialog(onDismiss: () -> Unit, onAdd: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    AppDialog(
        title = "Add manually", confirmLabel = "Add",
        onConfirm = { if (!onAdd(text)) bad = true }, onDismiss = onDismiss,
    ) {
        Text("Paste a WireGuard .conf or a vless://, trojan://, ss://, vmess://, hysteria2://, tuic:// or wireguard:// link (one per line).", style = Type.Small, color = C.Text)
        Spacer(Modifier.height(Space.Md))
        AppTextField(text, { text = it; bad = false }, singleLine = false, onCard = true)
        if (bad) Text("No valid link found", style = Type.Caption, color = C.Red, modifier = Modifier.padding(top = Space.Sm))
    }
}

@Composable
private fun SubscriptionDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AppDialog(
        title = "Add subscription link", confirmLabel = "Add",
        onConfirm = { if (url.isNotBlank()) onAdd(url.trim()) }, onDismiss = onDismiss,
    ) {
        Text("Servers from this URL are added now and when you update subscriptions.", style = Type.Small, color = C.Text)
        Spacer(Modifier.height(Space.Md))
        AppTextField(url, { url = it }, placeholder = "https://…", onCard = true)
    }
}
