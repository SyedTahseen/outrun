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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val headerColor by animateColorAsState(if (scrolled) C.Card else C.Bg, label = "headerColor")

    LaunchedEffect(scrolled) { onScrolledChange(scrolled) }
    DisposableEffect(Unit) { onDispose { onScrolledChange(false) } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().background(headerColor)) {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = searching,
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = { fadeTransition() },
                    label = "serversHeader",
                ) { isSearch ->
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isSearch) {
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
                                ) {
                                    AnimatedContent(
                                        targetState = profiles.size,
                                        transitionSpec = { numberTransition(up = targetState > initialState) },
                                        label = "serverCount",
                                    ) { n -> Text("$n", color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
                                }
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
                                HeaderIcon(Icons.Filled.MoreVert, "More") { menuPage = "main"; moreMenu = true }
                                DropdownMenu(
                                    moreMenu, { moreMenu = false }, shape = RoundedCornerShape(Radius.Card), containerColor = C.Card,
                                    modifier = Modifier.widthIn(min = 220.dp),
                                ) {
                                    when (menuPage) {
                                        "sort" -> {
                                            MenuBack("Sort") { menuPage = "main" }
                                            SortMode.entries.forEach { m ->
                                                DropdownMenuItem(
                                                    text = { Text(m.label, color = if (m == sort) C.Accent else C.Text, fontSize = 17.sp) },
                                                    trailingIcon = { if (m == sort) Icon(Icons.Filled.Check, null, tint = C.Accent, modifier = Modifier.size(20.dp)) },
                                                    onClick = { onSortChange(m); moreMenu = false },
                                                )
                                            }
                                        }
                                        "delete" -> {
                                            MenuBack("Delete") { menuPage = "main" }
                                            val invalid = remember(profiles) { invalidLinks(profiles) }
                                            val dupes = remember(profiles, favorites, selectedLink) {
                                                duplicateLinks(profiles, favorites + listOfNotNull(selectedLink))
                                            }
                                            val dead = remember(profiles, pings) { profiles.filter { pings[it.link] == -1 }.map { it.link } }
                                            MenuRow(Icons.Outlined.ErrorOutline, "Invalid", C.Red, invalid.isNotEmpty(), { CountText(invalid.size) }) {
                                                moreMenu = false
                                                confirmDel = DelAction("Delete ${invalid.size} invalid server(s)?", "These links can't be turned into a working configuration.", invalid)
                                            }
                                            MenuRow(Icons.Outlined.ContentCopy, "Duplicates", C.Red, dupes.isNotEmpty(), { CountText(dupes.size) }) {
                                                moreMenu = false
                                                confirmDel = DelAction("Delete ${dupes.size} duplicate(s)?", "One copy of each server is kept. Favorites and the selected server are kept first.", dupes)
                                            }
                                            MenuRow(Icons.Outlined.WifiOff, "Unreachable", C.Red, dead.isNotEmpty(), { CountText(dead.size) }) {
                                                moreMenu = false
                                                confirmDel = DelAction("Delete ${dead.size} unreachable server(s)?", "These failed the last latency test.", dead)
                                            }
                                            MenuRow(Icons.Outlined.DeleteOutline, "All servers", C.Red) {
                                                moreMenu = false
                                                confirmDel = DelAction("Delete all servers?", "All ${profiles.size} server(s) will be removed.", null)
                                            }
                                        }
                                        else -> {
                                            MenuRow(Icons.Outlined.Speed, if (testing) "Testing…" else "Test latency") { moreMenu = false; if (!testing) onTestLatency() }
                                            MenuRow(Icons.AutoMirrored.Outlined.Sort, "Sort", trailing = { Chevron() }) { menuPage = "sort" }
                                            MenuRow(Icons.Outlined.Refresh, "Update subs") { moreMenu = false; onRefreshSubscriptions() }
                                            MenuRow(Icons.Outlined.DeleteOutline, "Delete", trailing = { Chevron() }) { menuPage = "delete" }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

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

        AnimatedVisibility(
            visible = testing,
            enter = expandVertically(tween(Motion.Medium)) + fadeIn(tween(Motion.Medium)),
            exit = shrinkVertically(tween(Motion.Medium)) + fadeOut(tween(Motion.Short)),
        ) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = C.Accent, trackColor = C.ButtonGrey)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Crossfade(targetState = shown.isEmpty(), animationSpec = tween(Motion.Medium), label = "serversList") { empty ->
                if (empty) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (profiles.isEmpty()) "No servers yet.\nTap + to scan a QR code, paste a link,\nimport a file or add one manually."
                            else "No servers match.",
                            color = C.Muted, fontSize = 14.sp,
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().verticalScrollbar(listState), state = listState) {
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
            exit = shrinkVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeOut(tween(Motion.Short)),
        ) {
            if (barUrl != null) SubscriptionBar(
                url = barUrl, info = subInfo[barUrl], host = subHost(barUrl),
                onRefresh = { onRefreshSubscription(barUrl) }, onCopy = { onCopySubscription(barUrl) },
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
        ) { Text(d.text, color = C.Muted, fontSize = 14.sp) }
    }
    delSub?.let { url ->
        val n = sources.count { it.value == url }
        AppDialog(
            title = "Delete subscription?", confirmLabel = "Delete", confirmColor = C.Red,
            onConfirm = { onDeleteSubscription(url); delSub = null; onFilterChange(null) },
            onDismiss = { delSub = null },
        ) { Text("${subHost(url)} and its $n server(s) will be removed.", color = C.Muted, fontSize = 14.sp) }
    }
    if (subDialog) SubscriptionDialog(onDismiss = { subDialog = false }, onAdd = { onAddSubscription(it); subDialog = false })
    if (manual) ManualDialog(onDismiss = { manual = false }, onAdd = { onAddManual(it).also { ok -> if (ok) manual = false } })
}

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
                        if (info.total > 0) "${fmtSize(info.used, binary = true)} of ${fmtSize(info.total, binary = true)} used" else "${fmtSize(info.used, binary = true)} used · Unlimited",
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
private fun MenuRow(
    icon: ImageVector, label: String, tint: Color = C.Accent, enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, color = if (!enabled) C.Muted else if (tint == C.Red) C.Red else C.Text, fontSize = 17.sp, maxLines = 1) },
        leadingIcon = { Icon(icon, null, tint = if (enabled) tint else C.Muted, modifier = Modifier.size(22.dp)) },
        trailingIcon = trailing,
        enabled = enabled,
        onClick = onClick,
    )
}

@Composable
private fun MenuBack(title: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, color = C.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = C.Text, modifier = Modifier.size(22.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun Chevron() { Icon(Icons.Filled.ChevronRight, null, tint = C.Muted, modifier = Modifier.size(20.dp)) }

@Composable
private fun CountText(n: Int) { Text("$n", color = C.Muted, fontSize = 15.sp) }

@Composable
private fun Chip(label: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) C.Button else C.ButtonGrey, tween(Motion.Medium), label = "chipBg")
    val tick = rememberTick()
    Box(
        Modifier.height(44.dp)
            .pressable(RoundedCornerShape(Radius.Small), 0.95f, onLongClick = onLongClick) { if (!selected) tick(); onClick() }
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

    val rowBg by animateColorAsState(if (selected) C.Card else C.Bg, tween(Motion.Medium), label = "rowBg")
    val nameColor by animateColorAsState(if (selected) C.Green else C.Text, tween(Motion.Medium), label = "rowName")
    Column(modifier.fillMaxWidth().background(rowBg)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onConnect).padding(start = 20.dp, end = 8.dp).height(84.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlagBadge(p.name)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(Flags.clean(p.name), color = nameColor, fontSize = 17.sp, maxLines = 1)
                Text("${p.host}:${p.port}", color = C.Muted, fontSize = 13.sp, maxLines = 1)
                Text(LinkParser.describe(p.link), color = C.Muted.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1)
            }
            PingLabel(ping, testing)
            FavStar(fav, onFavorite)
            Box {
                HeaderIcon(Icons.Filled.MoreVert, "Options") { menu = true }
                DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(Radius.Card), containerColor = C.Card) {
                    MenuRow(Icons.Outlined.ContentCopy, "Copy") { menu = false; onCopy() }
                    MenuRow(Icons.Outlined.DeleteOutline, "Delete") { menu = false; onDelete() }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.CardBorder))
    }
}

@Composable
private fun PingLabel(ping: Int?, testing: Boolean) {
    AnimatedContent(
        targetState = ping to testing,
        transitionSpec = {
            ContentTransform(
                fadeIn(tween(Motion.Short + 50, delayMillis = 60)) + slideInVertically(tween(Motion.Short + 50)) { it / 3 },
                fadeOut(tween(Motion.Short - 40)),
                0f,
                SizeTransform(clip = false),
            )
        },
        label = "ping",
    ) { (p, t) ->
        when {
            p == null -> if (t) Text("…", color = C.Muted, fontSize = 14.sp)
            p < 0 -> Text("Timeout", color = C.Red, fontSize = 13.sp)
            else -> Text("$p ms", color = if (p < 500) C.Green else if (p < 1200) C.Amber else C.Red, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FavStar(fav: Boolean, onClick: () -> Unit) {
    val tick = rememberTick()
    val tint by animateColorAsState(if (fav) C.Amber else C.Muted, tween(Motion.Medium), label = "favTint")
    Box(
        Modifier.size(44.dp).pressable(RoundedCornerShape(22.dp), 1f) { tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (fav) Icons.Filled.Star else Icons.Outlined.StarBorder, "Favorite", tint = tint,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun AppDialog(
    title: String, confirmLabel: String, confirmColor: Color,
    onConfirm: () -> Unit, onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        shape = RoundedCornerShape(Radius.Card),
        title = { Text(title, color = C.Text, fontSize = 20.sp) },
        text = content,
        confirmButton = { TextButton(onConfirm) { Text(confirmLabel, color = confirmColor) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

@Composable
private fun ManualDialog(onDismiss: () -> Unit, onAdd: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    AppDialog(
        title = "Add manually", confirmLabel = "Add", confirmColor = C.Accent,
        onConfirm = { if (!onAdd(text)) bad = true }, onDismiss = onDismiss,
    ) {
        Column {
            Text("Paste a WireGuard .conf or a vless://, trojan://, ss://, vmess://, hysteria2://, tuic:// or wireguard:// link (one per line).", color = C.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = text, onValueChange = { text = it; bad = false },
                textStyle = TextStyle(color = C.Text, fontSize = 13.sp),
                cursorBrush = SolidColor(C.Accent),
                modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(Radius.Small)).background(C.Bg).padding(12.dp),
            )
            if (bad) Text("No valid link found", color = C.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun SubscriptionDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AppDialog(
        title = "Add subscription link", confirmLabel = "Add", confirmColor = C.Accent,
        onConfirm = { if (url.isNotBlank()) onAdd(url.trim()) }, onDismiss = onDismiss,
    ) {
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
    }
}
