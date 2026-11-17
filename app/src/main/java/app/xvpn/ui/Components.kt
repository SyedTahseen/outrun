package app.xvpn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// ------------------------------------------------------------------ surfaces

/**
 * The one card. Flat Card fill, Radius.Card; [bordered] adds the 1dp Border (only the selected-server card on Home uses it). With [onClick] it also ripples and shrinks when pressed.
 * Use padding = 0.dp when the content is a list of rows that bring their own padding.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = Space.Lg,
    containerColor: Color = C.Card,
    onClick: (() -> Unit)? = null,
    bordered: Boolean = false,
    translucentBorder: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.Card)
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressable(shape, Press.Card, onClick = onClick) else Modifier.clip(shape))
            .background(containerColor)
            .then(if (bordered) Modifier.border(1.dp, C.Border, shape) else Modifier)
            .then(if (translucentBorder) Modifier.translucentBorder(shape) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Proton TranslucentSurfaceBorder: 1dp vertical gradient, white 8% -> 2%, for surfaces floating over the map. */
fun Modifier.translucentBorder(shape: androidx.compose.ui.graphics.Shape): Modifier =
    border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.02f))), shape)

/** Hairline between rows (Proton VpnDivider: separatorNorm, Dp.Hairline). */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Dp.Hairline).background(C.Border))
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Space.Xxl), contentAlignment = Alignment.Center) {
        Text(text, style = Type.Small, color = C.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun AppProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(Motion.Medium, easing = Motion.Standard), label = "progress")
    Box(modifier.fillMaxWidth().height(Space.Xs).clip(CircleShape).background(C.Border)) {
        Box(Modifier.fillMaxWidth(shown).height(Space.Xs).background(color))
    }
}

/** Country flag in a 36x24 badge; a globe when the server has no known country. */
@Composable
fun FlagBadge(name: String?) {
    val res = name?.let { Flags.iso(it) }?.let { FLAG_RES[it] }
    Box(
        Modifier.size(width = 36.dp, height = 24.dp).clip(RoundedCornerShape(Radius.Badge)).background(C.Border),
        contentAlignment = Alignment.Center,
    ) {
        if (res != null) Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(ProtonIcons.Earth, null, tint = C.Muted, modifier = Modifier.size(Sizes.IconSm))
    }
}

// ------------------------------------------------------------------ headers

/**
 * Background of a screen header. It is the page color, and turns into the Card color once the page below is scrolled,
 * which also tints the status bar strip (see MainActivity).
 */
@Composable
fun HeaderSurface(scrolled: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val bg by animateColorAsState(if (scrolled) C.Header else C.Bg, tween(Motion.Medium), label = "headerSurface")
    Column(Modifier.fillMaxWidth().background(bg), content = content)
}

/**
 * The 64dp app-bar row (Material 3 TopAppBar, as Proton's SimpleTopAppBar). [backEdge]: the first item is a 48dp back
 * button, so the row starts 4dp from the edge instead of the 16dp gutter.
 */
@Composable
fun HeaderBar(backEdge: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (backEdge) Space.Xs else Space.Gutter, end = Space.Xs).height(Sizes.TopBar),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Standard screen header, following Proton's two app bars.
 *  - Sub-page ([onBack] set): Material 3 TopAppBar. 64dp, back button, 16sp Medium title (SimpleTopAppBar / body1Medium).
 *  - Top-level screen: Material 3 MediumTopAppBar (CollapsibleToolbarScaffold). 112dp with a 28sp title at the bottom; once the
 *    page is [scrolled] it collapses to 64dp with a 16sp title. The background goes Bg -> Card at the same moment.
 * [actions] sit at the end of the first 64dp row.
 */
@Composable
fun ScreenHeader(
    title: String?,
    onBack: (() -> Unit)? = null,
    scrolled: Boolean = false,
    titleTrailing: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    HeaderSurface(scrolled) {
        if (onBack != null) {
            HeaderBar(backEdge = true) {
                IconAction(ProtonIcons.ArrowLeft, "Back", onClick = onBack)
                Spacer(Modifier.width(Space.Sm))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (title != null) Text(
                        title, style = Type.BodyMedium, color = C.Text, maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (titleTrailing != null) {
                        Spacer(Modifier.width(Space.Md))
                        titleTrailing()
                    }
                }
                actions()
            }
        } else {
            val spec = tween<androidx.compose.ui.unit.Dp>(Motion.Medium, easing = Motion.Standard)
            val height by androidx.compose.animation.core.animateDpAsState(if (scrolled) Sizes.TopBar else Sizes.TopBarExpanded, spec, label = "headerHeight")
            val size by animateFloatAsState(if (scrolled) 16f else 28f, tween(Motion.Medium, easing = Motion.Standard), label = "headerTitle")
            Box(Modifier.fillMaxWidth().height(height)) {
                Row(
                    Modifier.align(Alignment.TopEnd).padding(end = Space.Xs).height(Sizes.TopBar),
                    verticalAlignment = Alignment.CenterVertically, content = actions,
                )
                Row(
                    Modifier.align(Alignment.BottomStart).padding(start = Space.Gutter, end = Space.Gutter * 3, bottom = if (scrolled) Space.Lg else Space.Xxl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) Text(
                        title, style = Type.Headline.copy(fontSize = size.sp, lineHeight = (size * 1.2f).sp), color = C.Text, maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (titleTrailing != null) {
                        Spacer(Modifier.width(Space.Md))
                        titleTrailing()
                    }
                }
            }
        }
    }
}

/** Accent heading above a group of rows or a card. [first] uses the tighter spacing at the top of a page. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, first: Boolean = false, horizontal: Dp = Space.Gutter) {
    Text(
        text, style = Type.Section, color = C.Accent,
        modifier = modifier.padding(start = horizontal, end = horizontal, top = Space.Xxl, bottom = Space.Sm), // Proton SettingsSectionHeading: top 24, bottom 8 (first = same)
    )
}

/** Small muted label above a form control (no horizontal padding; the parent provides the gutter). */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Type.SmallMedium, color = C.Muted, modifier = modifier.padding(top = Space.Xl, bottom = Space.Sm))
}

// ------------------------------------------------------------------ rows

/** Settings-style row: icon, title, optional second line, optional "opens elsewhere" arrow. */
@Composable
fun ListRow(icon: ImageVector, title: String, subtitle: String? = null, external: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.Row).clickable(onClick = onClick).padding(horizontal = Space.Gutter, vertical = Space.Lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.Text, modifier = Modifier.size(Sizes.Icon))
        Spacer(Modifier.width(Space.Lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body, color = C.Text)
            if (subtitle != null) Text(subtitle, style = Type.Small, color = C.Muted, maxLines = 2, modifier = Modifier.padding(top = Space.Xs))
        }
        if (external) {
            Spacer(Modifier.width(Space.Md))
            Icon(ProtonIcons.ArrowOutSquare, null, tint = C.Muted, modifier = Modifier.size(Sizes.Icon))
        }
    }
}

/** Radio row: a choice with a title and an explanation. */
@Composable
fun ChoiceRow(label: String, note: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Control)).clickable(onClick = onClick)
            .padding(horizontal = Space.Xs, vertical = Space.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppRadio(selected)
        Spacer(Modifier.width(Space.Lg))
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.Body, color = C.Text)
            Text(note, style = Type.Small, color = C.Muted, maxLines = 4)
        }
    }
}

/** Card of label / value rows separated by hairlines. */
@Composable
fun DetailCard(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    AppCard(modifier, padding = 0.dp) {
        items.forEachIndexed { i, (label, value) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.Lg, vertical = Space.Md), verticalAlignment = Alignment.Top) {
                Text(label, style = Type.Body, color = C.Muted)
                Spacer(Modifier.width(Space.Lg))
                Text(value, style = Type.BodyMedium, color = C.Text, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            if (i < items.lastIndex) HairlineDivider()
        }
    }
}

/** Card with a switch; the whole card toggles. */
@Composable
fun SwitchCard(label: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    AppCard(modifier.padding(horizontal = Space.Gutter), padding = 0.dp, onClick = { onChange(!checked) }) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.Lg, end = Space.Md, top = Space.Sm, bottom = Space.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = Type.Body, color = C.Text, modifier = Modifier.weight(1f))
            AppSwitch(checked, onChange)
        }
    }
}

// ------------------------------------------------------------------ page furniture

/** Tinted rounded tile with an icon, at the top of a settings page. */
@Composable
fun HeroIcon(icon: ImageVector, size: Dp = Sizes.Tile, centered: Boolean = false, tint: Color = C.Accent) {
    Box(Modifier.fillMaxWidth().padding(horizontal = Space.Gutter), contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(Radius.Card)).background(Brush.linearGradient(listOf(C.AccentMid, C.AccentDeep))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.56f)) }
    }
}

@Composable
fun PageHeading(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(start = Space.Gutter, end = Space.Gutter, top = Space.Xl, bottom = Space.Md)) {
        Text(title, style = Type.Headline, color = C.Text)
        Spacer(Modifier.height(Space.Sm))
        Text(text, style = Type.Body, color = C.Muted)
    }
}

// ------------------------------------------------------------------ dialogs and menus

/**
 * The one dialog. Title, free content, and right-aligned text actions. [confirmLabel] = null hides the confirm action,
 * [dismissLabel] = null hides the dismiss action.
 */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    confirmColor: Color = C.Accent,
    dismissLabel: String? = "Cancel",
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        // Laid out at full size from the first frame (so the window is centered immediately); only alpha/scale animate.
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) { enter.animateTo(1f, tween(Motion.Short, easing = Motion.Enter)) }
        val shape = RoundedCornerShape(Radius.Sheet)
        Column(
            Modifier.fillMaxWidth()
                .graphicsLayer { alpha = enter.value; scaleX = 0.92f + 0.08f * enter.value; scaleY = 0.92f + 0.08f * enter.value }
                .clip(shape).background(C.Card).padding(Space.Xxl),
        ) {
            Text(title, style = Type.Subtitle, color = C.Text)
            Spacer(Modifier.height(Space.Lg))
            content()
            Spacer(Modifier.height(Space.Xxl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (dismissLabel != null) TextAction(dismissLabel, onDismiss, color = C.Muted)
                if (confirmLabel != null) TextAction(confirmLabel, onConfirm, color = confirmColor)
            }
        }
    }
}

/** Popup menu. Floating surface: the only place besides dialogs where a shadow is used. */
@Composable
fun AppDropdownMenu(expanded: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded, onDismissRequest = onDismiss, modifier = modifier,
        shape = RoundedCornerShape(Radius.Control), containerColor = C.Card, tonalElevation = 0.dp, shadowElevation = Elevation.Menu,
        content = content,
    )
}

@Composable
fun AppMenuItem(
    icon: ImageVector, label: String, destructive: Boolean = false, enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    val textColor = when { !enabled -> C.Muted; destructive -> C.Red; else -> C.Text }
    DropdownMenuItem(
        text = { Text(label, style = Type.Body, color = textColor, maxLines = 1) },
        leadingIcon = { Icon(icon, null, tint = if (!enabled) C.Muted else if (destructive) C.Red else C.Accent, modifier = Modifier.size(Sizes.Icon)) },
        trailingIcon = trailing,
        enabled = enabled,
        onClick = onClick,
    )
}

/** A selectable entry of a menu (sort order): accent text and a check mark when chosen. */
@Composable
fun AppMenuChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = Type.Body, color = if (selected) C.Accent else C.Text) },
        trailingIcon = { if (selected) Icon(ProtonIcons.Checkmark, null, tint = C.Accent, modifier = Modifier.size(Sizes.Icon)) },
        onClick = onClick,
    )
}

/** First entry of a sub-menu: back arrow and the sub-menu title. */
@Composable
fun AppMenuHeader(title: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, style = Type.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = C.Text) },
        leadingIcon = { Icon(ProtonIcons.ArrowLeft, null, tint = C.Text, modifier = Modifier.size(Sizes.Icon)) },
        onClick = onClick,
    )
}

/** Proton ActiveDot: a vpnGreen dot with an expanding, fading ping ring; marks the connected/selected row. */
@Composable
fun ActiveDot(modifier: Modifier = Modifier) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition("activeDot")
    val spec = androidx.compose.animation.core.infiniteRepeatable<Float>(
        animation = tween(1200, 2000), repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
    )
    val scale by t.animateFloat(0.4f, 1f, spec, "scale")
    val alpha by t.animateFloat(0.8f, 0f, spec, "alpha")
    androidx.compose.foundation.Canvas(modifier.size(Sizes.Dot * 2)) {
        val r = Sizes.Dot.toPx() / 2
        drawCircle(C.Green, radius = r)
        drawCircle(C.Green.copy(alpha = alpha), radius = size.minDimension / 2 * scale)
    }
}
