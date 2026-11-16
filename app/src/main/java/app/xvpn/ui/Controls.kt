package app.xvpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Interaction rules shared by everything below:
//  - Buttons, chips, icon buttons and cards shrink slightly when pressed (see Press) and ripple.
//  - Full-width list rows only ripple; scaling a row that touches both screen edges would look broken.
//  - A light haptic tick confirms a selection change; a strong one confirms a primary action.
//  - Color changes always animate (Motion.Short for small controls, Motion.Medium for large surfaces).

/** Round 44dp icon button: header actions, row actions, favorites. */
@Composable
fun IconAction(icon: ImageVector, desc: String, modifier: Modifier = Modifier, tint: Color = C.Text, onClick: () -> Unit) {
    val color by animateColorAsState(tint, tween(Motion.Short), label = "iconTint")
    Box(modifier.size(Sizes.Control).pressable(CircleShape, Press.Icon, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = color, modifier = Modifier.size(Sizes.Icon))
    }
}

enum class ButtonStyle { Primary, Secondary, Outline, OutlineAccent }
enum class ButtonSize { Large, Compact }

/**
 * The app's button. Primary = purple fill, Secondary = grey fill, Outline = border only.
 * Give it Modifier.fillMaxWidth() for a full-width button; a [trailingIcon] only makes sense then.
 */
@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    size: ButtonSize = ButtonSize.Large,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
) {
    val tick = rememberTick(strong = style == ButtonStyle.Primary)
    val shape = RoundedCornerShape(Radius.Control)
    val container by animateColorAsState(
        when (style) { ButtonStyle.Primary -> C.Primary; ButtonStyle.Secondary -> C.Control; ButtonStyle.Outline, ButtonStyle.OutlineAccent -> Color.Transparent },
        tween(Motion.Medium), label = "buttonContainer",
    )
    val large = size == ButtonSize.Large
    val outlined = style == ButtonStyle.Outline || style == ButtonStyle.OutlineAccent
    val content = when (style) { ButtonStyle.Outline -> C.Text; ButtonStyle.OutlineAccent -> C.Accent; else -> C.OnPrimary }
    Box(
        modifier.alpha(if (enabled) 1f else 0.4f)
            .height(if (large) Sizes.Button else Sizes.Control)
            .pressable(shape, Press.Control, enabled) { tick(); onClick() }
            .background(container)
            .then(if (outlined) Modifier.border(if (style == ButtonStyle.OutlineAccent) 1.5.dp else 1.dp, if (style == ButtonStyle.OutlineAccent) C.Accent else C.Outline, shape) else Modifier)
            .padding(horizontal = if (large) Space.Xxl else Space.Xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = if (large) Type.Button else Type.ButtonSmall, color = content, maxLines = 1)
        if (trailingIcon != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Icon(trailingIcon, null, tint = content, modifier = Modifier.size(Sizes.Icon))
        }
    }
}

/** Colored text button for dialogs, headers and in-card links. */
@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.Accent, enabled: Boolean = true) {
    Box(
        modifier.heightIn(min = Sizes.Control)
            .pressable(RoundedCornerShape(Radius.Control), Press.Control, enabled, onClick = onClick)
            .padding(horizontal = Space.Md),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.ButtonSmall, color = if (enabled) color else C.Muted, maxLines = 1) }
}

/** Full-width text action at the bottom of a card ("Run test", "Open settings"). Rows only ripple. */
@Composable
fun CardAction(label: String, onClick: () -> Unit, color: Color = C.Accent, enabled: Boolean = true) {
    Box(
        Modifier.fillMaxWidth().height(Sizes.Field).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.ButtonSmall, color = if (enabled) color else C.Muted) }
}

/** A filled box that turns purple when selected: the base of chips, segmented controls and the transmit-mode picker. */
@Composable
fun SelectableBox(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val tick = rememberTick()
    val bg by animateColorAsState(if (selected) C.Primary else C.Control, tween(Motion.Medium), label = "selectBg")
    Box(
        modifier.height(Sizes.Control)
            .pressable(RoundedCornerShape(Radius.Control), Press.Control, enabled, onLongClick = onLongClick) { if (!selected) tick(); onClick() }
            .background(bg),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun SelectChip(
    label: String, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    SelectableBox(selected, onClick, modifier, enabled, onLongClick) {
        Text(
            label, style = Type.ButtonSmall, maxLines = 1,
            color = if (enabled) C.OnPrimary else C.OnPrimary.copy(alpha = 0.35f),
            modifier = Modifier.padding(horizontal = Space.Xl),
        )
    }
}

/** Equal-width options in a row; exactly one is selected. */
@Composable
fun Segmented(options: List<String>, selected: Int, enabled: List<Boolean> = options.map { true }, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.Sm)) {
        options.forEachIndexed { i, label ->
            SelectChip(label, i == selected, Modifier.weight(1f), enabled[i]) { onSelect(i) }
        }
    }
}

@Composable
fun AppSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val tick = rememberTick()
    Switch(
        checked = checked, onCheckedChange = { tick(); onChange(it) },
        colors = SwitchDefaults.colors(
            checkedThumbColor = C.OnPrimary, checkedTrackColor = C.Accent, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = C.Muted, uncheckedTrackColor = C.Border, uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Radio indicator: the ring recolors, the dot springs in. */
@Composable
fun AppRadio(selected: Boolean) {
    val ring by animateColorAsState(if (selected) C.Accent else C.Muted, tween(Motion.Short), label = "radioRing")
    val dot by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (selected) spring(dampingRatio = 0.45f, stiffness = 420f) else tween(Motion.Fast),
        label = "radioDot",
    )
    Box(Modifier.size(Sizes.Icon - 2.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(10.dp)
                .graphicsLayer { scaleX = dot; scaleY = dot; alpha = dot.coerceIn(0f, 1f) }
                .clip(CircleShape).background(C.Accent),
        )
    }
}

/**
 * Text input on a rounded container. The border lights up while focused.
 * [onCard] = the field sits on a Card (dialogs), so it uses the darker Bg as its fill instead.
 * Single-line fields are [Sizes.Field] tall; multi-line fields take [height].
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    height: Dp = Sizes.Field * 2,
    onCard: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) C.Accent else Color.Transparent, tween(Motion.Short), label = "fieldBorder")
    val shape = RoundedCornerShape(Radius.Control)
    Row(
        modifier.fillMaxWidth()
            .then(if (singleLine) Modifier.heightIn(min = Sizes.Field) else Modifier.height(height))
            .clip(shape).background(if (onCard) C.Bg else C.Card).border(1.dp, border, shape)
            .padding(start = Space.Lg, end = if (trailing != null) Space.Xs else Space.Lg, top = if (singleLine) 0.dp else Space.Md, bottom = if (singleLine) 0.dp else Space.Md),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            textStyle = Type.Body.copy(color = C.Text), cursorBrush = SolidColor(C.Accent),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = Type.Body, color = C.Muted)
                    inner()
                }
            },
        )
        trailing?.invoke()
    }
}
