package app.xvpn.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun IconAction(icon: ImageVector, desc: String, modifier: Modifier = Modifier, tint: Color = C.Text, onClick: () -> Unit) {
    val color by animateColorAsState(tint, tween(Motion.Short), label = "iconTint")
    Box(modifier.size(Sizes.Control).pressable(CircleShape, Press.Icon, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = color, modifier = Modifier.size(Sizes.Icon))
    }
}

enum class ButtonStyle { Primary, Secondary, Outline, OutlineAccent, OutlineCard }
enum class ButtonSize { Large, Compact }

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
    val outlinedStyle = style == ButtonStyle.Outline || style == ButtonStyle.OutlineAccent || style == ButtonStyle.OutlineCard
    val onCardOutline = style == ButtonStyle.OutlineCard
    val container by animateColorAsState(
        when {
            !enabled && style == ButtonStyle.Primary -> C.PrimaryDisabled

            !enabled && style == ButtonStyle.Secondary -> C.PrimaryDisabled
            style == ButtonStyle.Primary -> C.Primary
            style == ButtonStyle.Secondary -> C.Control
            onCardOutline -> C.Card
            outlinedStyle -> C.Bg
            else -> Color.Transparent
        },
        tween(Motion.Medium), label = "buttonContainer",
    )
    val large = size == ButtonSize.Large
    val outlined = outlinedStyle
    val content = when {
        !enabled && onCardOutline -> C.TextHint
        !enabled && outlined -> if (style == ButtonStyle.Outline) C.Disabled else C.PrimaryDisabled
        !enabled -> C.OnPrimary.copy(alpha = 0.5f)
        style == ButtonStyle.Outline || onCardOutline -> C.Text
        style == ButtonStyle.OutlineAccent -> C.Accent
        else -> C.OnPrimary
    }
    val borderColor = when {
        onCardOutline -> C.Border
        !enabled -> if (style == ButtonStyle.Outline) C.Disabled else C.PrimaryDisabled

        style == ButtonStyle.OutlineAccent -> C.Primary
        else -> C.Text
    }
    CompositionLocalProvider(LocalContentColor provides content) {
    Box(
        modifier
            .height(if (large) Sizes.Button else Sizes.Control)
            .pressable(shape, Press.Control, enabled) { tick(); onClick() }
            .background(container)
            .then(if (outlined) Modifier.border(Sizes.Border, borderColor, shape) else Modifier)
            .padding(horizontal = if (large) Space.Lg else Space.Xxl),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Type.Button, color = content, maxLines = 1)
        if (trailingIcon != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Icon(trailingIcon, null, tint = content, modifier = Modifier.size(Sizes.Icon))
        }
    }
    }
}

@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = C.Accent, enabled: Boolean = true) {
    Box(
        modifier.heightIn(min = Sizes.Control)
            .pressable(RoundedCornerShape(Radius.Badge), Press.Control, enabled, onClick = onClick)
            .padding(horizontal = Space.Md),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.ButtonSmall, color = if (enabled) color else C.Disabled, maxLines = 1) }
}

@Composable
fun CardAction(label: String, onClick: () -> Unit, color: Color = C.Accent, enabled: Boolean = true) {
    Box(
        Modifier.fillMaxWidth().height(Sizes.Field).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Type.ButtonSmall, color = if (enabled) color else C.Disabled) }
}

@Composable
fun SelectableBox(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    height: Dp = Sizes.Control,
    content: @Composable BoxScope.() -> Unit,
) {
    val tick = rememberTick()
    val bg by animateColorAsState(if (selected) C.Primary else C.Control, tween(Motion.Medium), label = "selectBg")
    Box(
        modifier.height(height)
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
    SelectableBox(selected, onClick, modifier, enabled, onLongClick, height = Sizes.Chip) {
        val base = if (selected) C.OnPrimary else C.Text
        Text(
            label, style = Type.Small, maxLines = 1,
            color = if (enabled) base else base.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = Space.Lg),
        )
    }
}

@Composable
fun AppSwitch(checked: Boolean, onChange: ((Boolean) -> Unit)?) {
    val tick = rememberTick()
    Switch(
        checked = checked, onCheckedChange = onChange?.let { cb -> { v: Boolean -> tick(); cb(v) } },
        colors = SwitchDefaults.colors(
            checkedThumbColor = C.OnPrimary, checkedTrackColor = C.Primary, checkedBorderColor = C.Primary,
            uncheckedThumbColor = C.Muted, uncheckedTrackColor = C.Disabled, uncheckedBorderColor = C.Disabled,
        ),
    )
}

@Composable
fun AppRadio(selected: Boolean) {
    RadioButton(
        selected = selected, onClick = null,
        colors = RadioButtonDefaults.colors(selectedColor = C.Primary, unselectedColor = C.TextHint),
    )
}

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
    isError: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (isError) C.Red else if (focused) C.Primary else Color.Transparent, tween(Motion.Short), label = "fieldBorder")

    val borderWidth = if (focused) Sizes.Spinner else Sizes.Border
    val shape = RoundedCornerShape(Radius.Control)
    Row(
        modifier.fillMaxWidth()

            .then(if (singleLine) Modifier.heightIn(min = Sizes.Control) else Modifier.height(height))
            .clip(shape).background(if (onCard) C.Bg else C.Card).border(borderWidth, border, shape)
            .padding(start = Space.Lg, end = if (trailing != null) Space.Xs else Space.Lg, top = if (singleLine) 0.dp else Space.Md, bottom = if (singleLine) 0.dp else Space.Md),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
    ) {
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            textStyle = Type.Body.copy(color = C.Text), cursorBrush = SolidColor(if (isError) C.Red else C.Text),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = Type.Body, color = C.TextHint)
                    inner()
                }
            },
        )
        trailing?.invoke()
    }
}
