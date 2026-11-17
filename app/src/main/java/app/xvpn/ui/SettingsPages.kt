package app.xvpn.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.xvpn.R
import app.xvpn.data.Choices
import kotlin.math.min

private fun titleVisibilityFraction(state: LazyListState, titleItemIndex: Int): Float {
    val info = state.layoutInfo.visibleItemsInfo.take(titleItemIndex + 1).firstOrNull { it.index == titleItemIndex }
    return if (info != null && info.size > 0) {
        val size = info.size.toFloat()
        1f - min(size, size + info.offset.toFloat()) / size
    } else if (state.firstVisibleItemIndex == 0) 0f else 1f
}

@Composable
fun FeaturePage(
    title: String,
    onBack: () -> Unit,
    onScrolledChange: (Boolean) -> Unit,
    bottom: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val fraction by remember { derivedStateOf { titleVisibilityFraction(listState, 1) } }
    ReportScrolled(scrolled, onScrolledChange)
    Column(Modifier.fillMaxSize()) {
        HeaderSurface(scrolled) {
            HeaderBar(backEdge = true) {
                IconAction(ProtonIcons.ArrowLeft, "Back", onClick = onBack)
                Spacer(Modifier.width(Space.Sm))
                if (fraction > 0f) Text(title, style = Type.BodyMedium, color = C.Text.copy(alpha = fraction), maxLines = 1)
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, content = content)
        bottom?.invoke()
    }
}

fun LazyListScope.featureHeader(imageRes: Int, title: String, description: String) {
    item {
        Image(painterResource(imageRes), null, Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.Lg))
    }
    item {
        Text(title, style = Type.Heading, color = C.Text, modifier = Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.Lg))
    }
    item {
        Text(
            description, style = Type.Small, color = C.Muted,
            modifier = Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.Sm),
        )
    }
}

@Composable
fun FeatureToggle(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(Space.Lg)).cardSurface(RoundedCornerShape(Space.Lg))
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(horizontal = Space.Xl, vertical = Space.Lg),
        horizontalArrangement = Arrangement.spacedBy(Space.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.Body, color = C.Text, modifier = Modifier.weight(1f))
        AppSwitch(checked, null)
    }
}

private fun formatItems(items: List<String>): String = when (items.size) {
    0 -> "None"
    1 -> items[0]
    2 -> "${items[0]}, ${items[1]}"
    else -> "${items[0]}, ${items[1]} and ${items.size - 2} more"
}

@Composable
fun SplitTunnelingPage(
    on: Boolean, including: Boolean, appNames: List<String>, ips: List<String>,
    onOn: (Boolean) -> Unit, onMode: (including: Boolean) -> Unit, onApps: () -> Unit, onIps: () -> Unit,
    onBack: () -> Unit, onScrolledChange: (Boolean) -> Unit,
) {
    var modeDialog by remember { mutableStateOf(false) }
    val word = if (including) "Included" else "Excluded"
    FeaturePage("Split tunneling", onBack, onScrolledChange) {
        featureHeader(
            R.drawable.setting_split_tunneling, "Split tunneling",
            "Customize your connection by deciding which apps and IP addresses are protected by VPN.",
        )
        item {
            FeatureToggle("Split tunneling", on, Modifier.padding(horizontal = Space.Gutter, vertical = Space.Lg), onOn)
        }
        if (on) {
            item {
                SettingRow("Mode", if (including) "Include" else "Exclude", Modifier.animateItem()) { modeDialog = true }
            }
            item { ListRow(ProtonIcons.Mobile, "$word apps", formatItems(appNames), onClick = onApps) }
            item { ListRow(ProtonIcons.WindowTerminal, "$word IP addresses", formatItems(ips), onClick = onIps) }
            item {
                Text(
                    "Applies the next time you connect." + if (!including) " Excluding IP addresses needs Android 13 or newer." else "",
                    style = Type.Caption, color = C.Muted,
                    modifier = Modifier.padding(start = Space.Gutter, end = Space.Gutter, top = Space.Sm, bottom = Space.Lg),
                )
            }
        }
    }
    if (modeDialog) {
        AppDialog(title = "Mode", onDismiss = { modeDialog = false }, dismissLabel = null) {
            ChoiceRow("Exclude", "Selected apps and IP addresses are excluded from VPN connection.", !including) { onMode(false); modeDialog = false }
            ChoiceRow("Include", "Only selected apps and IP addresses connect through VPN, all other traffic is unprotected.", including) { onMode(true); modeDialog = false }
        }
    }
}

@Composable
fun SettingRow(title: String, value: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Control)).clickable(onClick = onClick).padding(Space.Lg)) {
        Text(title, style = Type.Body, color = C.Text)
        if (value != null) {
            Spacer(Modifier.height(Space.Xs))
            Text(value, style = Type.Body, color = C.Muted)
        }
    }
}

@Composable
fun KillSwitchPage(onBack: () -> Unit, onOpenVpnSettings: () -> Unit, onLearnMore: () -> Unit) {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    val cog = "cogwheel"
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Kill switch", onBack)
        Column(Modifier.fillMaxHeight().padding(horizontal = Space.Gutter)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Image(painterResource(R.drawable.killswitch_settings_promo), null, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(Space.Xxxl))
                Text("How to enable kill switch", style = Type.Heading, color = C.Text)
                Spacer(Modifier.height(Space.Lg))
                Text("1. Open your device settings", style = Type.Body, color = C.Text)
                Text(
                    buildAnnotatedString { append("2. Find Outrun and tap "); appendInlineContent(cog, "settings") },
                    inlineContent = mapOf(
                        cog to InlineTextContent(Placeholder(1.25.em, 1.25.em, PlaceholderVerticalAlign.Center)) {
                            Icon(ProtonIcons.CogWheel, null, tint = C.Text, modifier = Modifier.fillMaxSize())
                        },
                    ),
                    style = Type.Body, color = C.Text,
                )
                Text(
                    buildAnnotatedString {
                        append("3. Enable \""); withStyle(bold) { append("Always-on VPN") }; append("\" and \"")
                        withStyle(bold) { append("Block connections without VPN") }; append("\"")
                    },
                    style = Type.Body, color = C.Text,
                )
                Spacer(Modifier.height(Space.Lg))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Sm), verticalAlignment = Alignment.Top) {
                    Icon(ProtonIcons.InfoCircle, null, tint = C.Muted, modifier = Modifier.size(Sizes.Icon))
                    Column(Modifier.weight(1f)) {
                        Text("When kill switch is on, you won’t be able to connect to:", style = Type.Small, color = C.Muted)
                        Spacer(Modifier.height(Space.Lg))
                        Bullet("Websites and apps excluded from your VPN connection through split tunneling")
                        Bullet("Devices on your local area network (LAN)")
                    }
                }
            }
            Spacer(Modifier.height(Space.Lg))
            AppButton("Settings", onOpenVpnSettings, Modifier.fillMaxWidth(), ButtonStyle.Primary, trailingIcon = ProtonIcons.ArrowOutSquare)
            Spacer(Modifier.height(Space.Lg))
            AppButton("What is kill switch?", onLearnMore, Modifier.fillMaxWidth(), ButtonStyle.OutlineAccent, trailingIcon = ProtonIcons.ArrowOutSquare)
            Spacer(Modifier.height(Space.Lg))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•", style = Type.Small, color = C.Muted, modifier = Modifier.padding(horizontal = 5.dp))
        Text(text, style = Type.Small, color = C.Muted)
    }
}

@Composable
fun ThemePage(selected: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Theme", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(horizontal = Space.Gutter).height(IntrinsicSize.Max)) {
                ThemeTile("Light", R.drawable.theme_light, selected == AppTheme.LIGHT, Modifier.weight(1f)) { onSelect(AppTheme.LIGHT) }
                ThemeTile("Dark", R.drawable.theme_dark, selected == AppTheme.DARK, Modifier.weight(1f)) { onSelect(AppTheme.DARK) }
                ThemeTile("Device theme", R.drawable.theme_auto, selected == AppTheme.SYSTEM, Modifier.weight(1f)) { onSelect(AppTheme.SYSTEM) }
            }
            Row(Modifier.padding(horizontal = Space.Gutter).height(IntrinsicSize.Max)) {
                ThemeTile("Spark", R.drawable.theme_spark, selected == AppTheme.SPARK, Modifier.weight(1f)) { onSelect(AppTheme.SPARK) }
                Spacer(Modifier.weight(2f))
            }
        }
    }
}

@Composable
private fun ThemeTile(label: String, imageRes: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(Radius.Badge))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .fillMaxHeight().padding(vertical = 10.dp, horizontal = Space.Xs),
        verticalArrangement = Arrangement.spacedBy(Space.Sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(painterResource(imageRes), null)
        Box(Modifier.weight(1f)) { Text(label, style = Type.Small, color = C.Text, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center)) }
        AppRadio(selected)
    }
}

@Composable
private fun SettingsItemScaffold(
    modifier: Modifier, description: String?,
    title: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Space.Lg, vertical = Space.Lg)) {
        title()
        if (description != null) {
            Spacer(Modifier.height(Space.Lg))
            Text(description, style = Type.Small, color = C.Muted, modifier = Modifier.padding(end = Space.Sm))
        }
    }
}

@Composable
fun SettingsValueItem(name: String, value: String, description: String?, onClick: () -> Unit) {
    SettingsItemScaffold(Modifier.clickable(onClick = onClick), description) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = Type.Body, color = C.Text, modifier = Modifier.weight(1f))
            Text(value, style = Type.Small, color = C.Muted)
            Icon(ProtonIcons.ChevronRight, null, tint = C.Muted, modifier = Modifier.padding(start = Space.Xs).size(16.dp))
        }
    }
}

@Composable
fun SettingsToggleItem(name: String, checked: Boolean, description: String?, onToggle: (Boolean) -> Unit) {
    SettingsItemScaffold(Modifier.toggleable(value = checked, onValueChange = onToggle, role = Role.Switch), description) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = Type.Body, color = C.Text, modifier = Modifier.weight(1f))
            AppSwitch(checked, null)
        }
    }
}

@Composable
fun LanPage(on: Boolean, onOn: (Boolean) -> Unit, onBack: () -> Unit, onScrolledChange: (Boolean) -> Unit) {
    FeaturePage("LAN connections", onBack, onScrolledChange) {
        featureHeader(
            R.drawable.setting_lan, "LAN connections",
            "Allows you to connect to printers, speakers, and other devices in your device\u2019s local network.",
        )
        item { FeatureToggle("LAN connections", on, Modifier.padding(horizontal = Space.Gutter, vertical = Space.Lg), onOn) }
        item {
            Text(
                "Traffic to private addresses (192.168.x.x, 10.x.x.x, 172.16\u201331.x.x) goes directly, not through the VPN. Applies the next time you connect. Ignored when split tunneling includes only specific IP addresses.",
                style = Type.Caption, color = C.Muted,
                modifier = Modifier.padding(start = Space.Gutter, end = Space.Gutter, bottom = Space.Lg),
            )
        }
    }
}

@Composable
fun CustomDnsPage(
    on: Boolean, servers: List<String>, netShieldConflict: Boolean,
    onOn: (Boolean) -> Unit, onMove: (from: Int, to: Int) -> Unit, onRemove: (String) -> Unit, onAdd: () -> Unit,
    onBack: () -> Unit, onScrolledChange: (Boolean) -> Unit,
) {
    var conflictDialog by remember { mutableStateOf(false) }
    FeaturePage(
        "Custom DNS", onBack, onScrolledChange,
        bottom = if (on) ({
            Box(Modifier.fillMaxWidth().padding(Space.Lg)) {
                AppButton("Add new DNS server", onAdd, Modifier.fillMaxWidth(), ButtonStyle.Primary)
            }
        }) else null,
    ) {
        featureHeader(
            R.drawable.setting_custom_dns, "Custom DNS",
            "Connect to VPN with a self-hosted or third-party domain name system (DNS).",
        )
        item {
            FeatureToggle(
                "Custom DNS", on, Modifier.padding(horizontal = Space.Gutter, vertical = Space.Lg),
            ) { v -> onOn(v); if (v && netShieldConflict) conflictDialog = true }
        }
        if (on) {
            item(key = "title") {
                Text("Servers (${servers.size})", style = Type.SmallMedium, color = C.Text, modifier = Modifier.padding(Space.Lg))
            }
            itemsIndexed(servers, key = { _, s -> s }) { i, server ->
                Row(
                    Modifier.fillMaxWidth().padding(start = Space.Lg).heightIn(min = 56.dp).animateItem(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(server, style = Type.Body, color = C.Text, modifier = Modifier.weight(1f))
                    if (i > 0) IconAction(ProtonIcons.ChevronUp, "Move up") { onMove(i, i - 1) }
                    else Spacer(Modifier.size(Sizes.Control))
                    if (i < servers.lastIndex) IconAction(ProtonIcons.ChevronDown, "Move down") { onMove(i, i + 1) }
                    else Spacer(Modifier.size(Sizes.Control))
                    IconAction(ProtonIcons.Trash, "Delete") { onRemove(server) }
                    Spacer(Modifier.width(Space.Xs))
                }
            }
            item(key = "footer") {
                Text(
                    if (servers.size <= 1) "If your custom DNS server doesn\u2019t work, the standard VPN DNS will be used."
                    else "We\u2019ll try to connect to these servers in order. If none of them work, the VPN DNS will be used.",
                    style = Type.Small, color = C.Shade50,
                    modifier = Modifier.padding(start = Space.Lg, end = Space.Lg, top = Space.Sm, bottom = Space.Lg),
                )
            }
        }
    }
    if (conflictDialog) {
        AppDialog(
            title = "NetShield will be unavailable", onDismiss = { conflictDialog = false },
            confirmLabel = "Got it", onConfirm = { conflictDialog = false }, dismissLabel = null,
        ) {
            Text("NetShield can not filter out ads, trackers and malware when custom DNS is used.", style = Type.Small, color = C.Text)
        }
    }
}

@Composable
fun AddDnsPage(existing: List<String>, onAdd: (String) -> Unit, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun submit() {
        val t = text.trim()
        val addr = Choices.parseDnsAddress(t)
        error = when {
            t.isEmpty() -> "Enter an IPv4 or IPv6 address"
            addr == null -> "Enter a valid IPv4 or IPv6 address"
            addr in existing -> "Server already added"
            else -> null
        }
        if (error == null && addr != null) onAdd(addr)
    }
    androidx.activity.compose.BackHandler(true) { onBack() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Add new DNS server", onBack)
        Row(Modifier.fillMaxWidth().padding(Space.Lg), verticalAlignment = Alignment.Top) {
            AppTextField(
                value = text, onValueChange = { text = it; error = null }, placeholder = "192.0.2.0", isError = error != null,
                modifier = Modifier.weight(1f).focusRequester(focus),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
            )
            Spacer(Modifier.width(Space.Sm))
            AppButton("Add", { submit() }, Modifier, ButtonStyle.Primary, ButtonSize.Compact)
        }
        Text(
            error ?: "Enter the server\u2019s IPv4 or IPv6 address",
            style = Type.Caption, color = if (error != null) C.Red else C.Muted,
            modifier = Modifier.padding(horizontal = Space.Lg),
        )
    }
}
