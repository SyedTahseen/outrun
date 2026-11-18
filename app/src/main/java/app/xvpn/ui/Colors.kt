package app.xvpn.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

object Palette {
    private val state = mutableStateOf(true)
    var dark: Boolean
        get() = state.value
        set(v) { if (state.value != v) state.value = v }
}

private fun pick(dark: Long, light: Long) = Color(if (Palette.dark) dark else light)

object C {
    val Bg get() = pick(0xFF1C1B24, 0xFFFFFFFF)
    val Card get() = pick(0xFF292733, 0xFFF5F4F2)
    val Header get() = Card
    val CardOverlay get() = Card.copy(alpha = 0.86f)
    val Control get() = pick(0xFF3B3747, 0xFFEAE7E4)
    val Border get() = pick(0xFF3B3747, 0xFFEAE7E4)
    val Outline get() = pick(0xFF4A4658, 0xFFD1CFCD)
    val Scrim get() = pick(0x85000000, 0x7A17181C)

    val Primary = Color(0xFF6D4AFF)
    val Accent get() = pick(0xFF8A6EFF, 0xFF6D4AFF)
    val BrandDarken20 = Color(0xFF4D34B3)
    val AccentMid get() = pick(0xFF372580, 0xFFC4B7FF)
    val AccentDeep get() = pick(0xFF271B54, 0xFFC4B7FF)
    val NavPill get() = Accent.copy(alpha = 0.32f).compositeOver(Card)
    val Blue = Color(0xFF2F7BF0)

    val Text get() = pick(0xFFFFFFFF, 0xFF0C0C14)
    val Muted get() = pick(0xFFA7A4B5, 0xFF706D6B)
    val TextHint get() = pick(0xFF6D697D, 0xFF999693)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryDisabled get() = pick(0xFF372580, 0xFFC4B7FF)
    val SolidDisabled = Color(0xFFC4B7FF)
    val Disabled get() = pick(0xFF5B576B, 0xFFC2BFBC)
    val Inverted get() = pick(0xFF0C0C14, 0xFFFFFFFF)

    val Green get() = pick(0xFF2CFFCC, 0xFF1C9C7C)
    val Red get() = pick(0xFFF08FA4, 0xFFCC2D4F)
    val Amber get() = pick(0xFFFFB84D, 0xFFE65200)
    val SuccessSolid get() = pick(0xFF4AB89A, 0xFF007B58)
    val Neutral get() = Muted

    val OverlayConnected get() = if (Palette.dark) 0.5f else 0.2f
    val OverlayDisconnected get() = if (Palette.dark) 0.5f else 0.24f

    val Land get() = pick(0xFF292733, 0xFFF1EEEB)
    val LandActive get() = pick(0xFF4A4658, 0xFFD1CFCD)
    val LandLine get() = pick(0xFF5B576B, 0xFFC2BFBC)
}
