package app.xvpn.ui

import app.xvpn.R
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.material3.HorizontalDivider
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

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = Space.Lg,
    containerColor: Color = C.Card,
    onClick: (() -> Unit)? = null,
    bordered: Boolean = false,
    translucentBorder: Boolean = false,
    radius: Dp = Radius.Card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressable(shape, Press.Card, onClick = onClick) else Modifier.clip(shape))
            .background(containerColor)
            .then(if (bordered) Modifier.border(Sizes.Border, C.Border, shape) else Modifier)
            .then(if (translucentBorder) Modifier.translucentBorder(shape) else Modifier)
            .padding(padding),
        content = content,
    )
}

fun Modifier.translucentBorder(shape: androidx.compose.ui.graphics.Shape): Modifier =
    border(Sizes.Border, Brush.verticalGradient(listOf(C.Text.copy(alpha = 0.08f), C.Text.copy(alpha = 0.02f))), shape)

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = Dp.Hairline, color = C.Border)
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

@Composable
fun FlagBadge(name: String?) {
    val res = name?.let { Flags.iso(it) }?.let { FLAG_RES[it] }
    Box(Modifier.size(Sizes.FlagArea), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(width = Sizes.FlagWidth, height = Sizes.FlagHeight).clip(RoundedCornerShape(Radius.Badge)).background(C.Border),
            contentAlignment = Alignment.Center,
        ) {
            if (res != null) Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(ProtonIcons.Earth, null, tint = C.Muted, modifier = Modifier.size(Sizes.IconSm))
        }
    }
}

@Composable
fun FastestFlag() {
    Box(Modifier.size(Sizes.FlagArea), contentAlignment = Alignment.Center) {
        Image(painterResource(if (Palette.dark) R.drawable.flag_fastest_dark else R.drawable.flag_fastest), null, Modifier.size(width = Sizes.FlagWidth, height = Sizes.FlagHeight))
    }
}

@Composable
fun HeaderSurface(scrolled: Boolean = false, base: Color = C.Bg, content: @Composable ColumnScope.() -> Unit) {
    val bg by animateColorAsState(if (scrolled) C.Header else base, label = "headerSurface")
    Column(Modifier.fillMaxWidth().background(bg), content = content)
}

@Composable
fun HeaderBar(backEdge: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (backEdge) Space.Xs else Space.Gutter, end = Space.Xs).height(Sizes.TopBar),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ScreenHeader(
    title: String?,
    onBack: (() -> Unit)? = null,
    scrolled: Boolean = false,
    titleTrailing: (@Composable () -> Unit)? = null,
    base: Color = C.Bg,
    actions: @Composable RowScope.() -> Unit = {},
) {
    HeaderSurface(scrolled, base) { ScreenHeaderContent(title, onBack, scrolled, titleTrailing, actions) }
}

@Composable
fun ScreenHeaderContent(
    title: String?,
    onBack: (() -> Unit)? = null,
    scrolled: Boolean = false,
    titleTrailing: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    run {
        if (onBack != null) {
            HeaderBar(backEdge = true) {
                IconAction(ProtonIcons.ArrowLeft, "Back", onClick = onBack)
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
                    Modifier.align(Alignment.BottomStart).padding(start = Space.Gutter, end = Space.Gutter * 3, bottom = if (scrolled) 15.dp else Space.Xxl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (title != null) Text(
                        title, style = Type.Headline.copy(fontSize = size.sp), color = C.Text, maxLines = 1,
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

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, first: Boolean = false, horizontal: Dp = Space.Gutter) {
    Text(
        text, style = Type.Section, color = C.Accent,
        modifier = modifier.padding(start = horizontal, end = horizontal, top = Space.Xxl, bottom = Space.Sm),
    )
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    // Proton's ProtonOutlinedTextField label: captionMedium + textNorm, 8dp gap to the field.
    Text(text, style = Type.Label, color = C.Text, modifier = modifier.padding(top = Space.Xl, bottom = Space.Sm))
}

@Composable
fun ListRow(icon: ImageVector, title: String, subtitle: String? = null, external: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.Control)).clickable(onClick = onClick).padding(Space.Lg),
        verticalAlignment = if (subtitle != null) Alignment.Top else Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(end = Space.Lg).width(Sizes.IconLg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.Text, modifier = Modifier.size(Sizes.Icon))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(title, style = Type.Body, color = C.Text)
            if (subtitle != null) Text(subtitle, style = Type.Body, color = C.Muted, maxLines = 2, modifier = Modifier.padding(top = Space.Xs + Space.Xs / 2))
        }
        if (external) Icon(ProtonIcons.ArrowOutSquare, null, tint = C.Muted, modifier = Modifier.padding(end = Space.Sm).size(Sizes.Icon))
    }
}

@Composable
fun ChoiceRow(label: String, note: String, selected: Boolean, onClick: () -> Unit) {
    // Mirrors Proton's SettingsRadioItemSmall: body2 title and description, trailing radio, 12dp vertical padding.
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Space.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.Small, color = C.Text)
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(Space.Xs))
                Text(note, style = Type.Small, color = C.Muted, maxLines = 4)
            }
        }
        Box(Modifier.padding(start = Space.Sm)) { AppRadio(selected) }
    }
}

@Composable
fun DetailCard(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    containerColor: Color = C.Card,
    radius: Dp = Radius.Card,
    rowPadding: Dp = Space.Md,
    rowAlignment: Alignment.Vertical = Alignment.Top,
) {
    AppCard(modifier, padding = 0.dp, containerColor = containerColor, radius = radius) {
        items.forEachIndexed { i, (label, value) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.Lg, vertical = rowPadding), verticalAlignment = rowAlignment) {
                Text(label, style = Type.Body, color = C.Muted)
                Spacer(Modifier.width(Space.Lg))
                Text(value, style = Type.BodyMedium, color = C.Text, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            if (i < items.lastIndex) HairlineDivider()
        }
    }
}

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

@Composable
fun AppDialog(
    title: String?,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    confirmColor: Color = C.Accent,
    dismissLabel: String? = "Cancel",
    compactTitle: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Dialog(onDismissRequest = onDismiss) {
        // Proton's ProtonAlert is a stock Material 3 AlertDialog: platform dialog transition, no custom scale/alpha.
        val shape = RoundedCornerShape(Radius.Sheet)
        Column(
            Modifier.fillMaxWidth().widthIn(min = 280.dp, max = 560.dp).clip(shape).background(C.Card).padding(Space.Xxl),
        ) {
            if (title != null) {
                // ProtonAlert titles are headline (20 bold); ProtonBasicAlert choice dialogs use body1Bold (16 bold).
                Text(title, style = if (compactTitle) Type.BodyBold else Type.Heading, color = C.Text)
                Spacer(Modifier.height(if (compactTitle) Space.Md else Space.Lg))
            }
            content()
            Spacer(Modifier.height(Space.Xxl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Sm, Alignment.End)) {
                if (dismissLabel != null) TextAction(dismissLabel, onDismiss)
                if (confirmLabel != null) TextAction(confirmLabel, onConfirm, color = confirmColor)
            }
        }
    }
}

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

@Composable
fun AppMenuChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = Type.Body, color = if (selected) C.Accent else C.Text) },
        trailingIcon = { if (selected) Icon(ProtonIcons.Checkmark, null, tint = C.Accent, modifier = Modifier.size(Sizes.Icon)) },
        onClick = onClick,
    )
}

@Composable
fun AppMenuHeader(title: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(title, style = Type.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = C.Text) },
        leadingIcon = { Icon(ProtonIcons.ArrowLeft, null, tint = C.Text, modifier = Modifier.size(Sizes.Icon)) },
        onClick = onClick,
    )
}

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
