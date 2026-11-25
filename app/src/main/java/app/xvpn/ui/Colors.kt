package app.xvpn.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

object Palette {
    private val state = mutableStateOf(true)
    var dark: Boolean
        get() = state.value
        set(v) { if (state.value != v) state.value = v }

    private val midnightState = mutableStateOf(false)
    var midnight: Boolean
        get() = midnightState.value
        set(v) { if (midnightState.value != v) midnightState.value = v }

    private val accentState = mutableStateOf(AccentSet.of(Accents.Default))

    var accentSet: AccentSet
        get() = accentState.value
        private set(v) { accentState.value = v }
    fun setAccent(base: Color) { if (accentSet.primary != base) accentSet = AccentSet.of(base) }
}

class AccentPreset(val id: String, val name: String, val color: Color)

object Accents {
    val Blue = Color(0xFF2563EB)
    val Purple = Color(0xFF6D4AFF)
    val Default = Blue
    val Presets = listOf(
        AccentPreset("blue", "Blue", Blue),
        AccentPreset("purple", "Purple", Purple),
        AccentPreset("teal", "Teal", Color(0xFF0F766E)),
        AccentPreset("green", "Green", Color(0xFF15803D)),
        AccentPreset("orange", "Orange", Color(0xFFC2410C)),
        AccentPreset("pink", "Pink", Color(0xFFBE185D)),
    )
}

class AccentSet(
    val primary: Color,
    val accentDark: Color,
    val darken20: Color,
    val midDark: Color,
    val deepDark: Color,
    val tint: Color,
) {
    companion object {
        fun of(p: Color): AccentSet {
            if (p == Accents.Purple) return AccentSet(
                p, Color(0xFF8A6EFF), Color(0xFF4D34B3), Color(0xFF372580), Color(0xFF271B54), Color(0xFFC4B7FF),
            )
            fun mix(a: Color, b: Color, t: Float) = Color(
                a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f,
            )
            fun scale(a: Color, k: Float) = Color(a.red * k, a.green * k, a.blue * k, 1f)
            return AccentSet(p, mix(p, Color.White, 0.20f), scale(p, 0.70f), scale(p, 0.50f), scale(p, 0.34f), mix(p, Color.White, 0.60f))
        }
    }
}

private fun pick(dark: Long, light: Long, midnight: Long = dark) =
    Color(if (Palette.midnight) midnight else if (Palette.dark) dark else light)

object C {
    val Bg get() = pick(0xFF1C1B24, 0xFFFFFFFF, 0xFF131315)

    val Deep get() = pick(0xFF0C0C14, 0xFFF1EEEB, 0xFF0A0A0B)
    val Card get() = pick(0xFF292733, 0xFFF5F4F2, 0xFF252628)
    val Header get() = Card
    val CardOverlay get() = Card.copy(alpha = 0.86f)
    val Control get() = pick(0xFF3B3747, 0xFFEAE7E4, 0xFF343539)
    val Border get() = pick(0xFF3B3747, 0xFFEAE7E4, 0xFF343539)
    val Outline get() = pick(0xFF4A4658, 0xFFD1CFCD, 0xFF45464B)
    val Scrim get() = pick(0x85000000, 0x7A17181C)

    val Primary get() = Palette.accentSet.primary
    val Accent get() = if (Palette.dark) Palette.accentSet.accentDark else Palette.accentSet.primary
    val BrandDarken20 get() = Palette.accentSet.darken20
    val AccentMid get() = if (Palette.dark) Palette.accentSet.midDark else Palette.accentSet.tint
    val AccentDeep get() = if (Palette.dark) Palette.accentSet.deepDark else Palette.accentSet.tint
    val NavPill get() = Accent.copy(alpha = 0.32f).compositeOver(Card)
    val Blue = Color(0xFF2F7BF0)

    val Text get() = pick(0xFFFFFFFF, 0xFF0C0C14)
    val Muted get() = pick(0xFFA7A4B5, 0xFF706D6B, 0xFFA3A4A9)
    val TextHint get() = pick(0xFF6D697D, 0xFF999693, 0xFF6C6D72)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryDisabled get() = AccentMid
    val Disabled get() = pick(0xFF5B576B, 0xFFC2BFBC, 0xFF56575C)
    val Inverted get() = pick(0xFF0C0C14, 0xFFFFFFFF)

    val Green get() = pick(0xFF2CFFCC, 0xFF1C9C7C)
    val Red get() = pick(0xFFF08FA4, 0xFFCC2D4F)
    val Amber get() = pick(0xFFFFB84D, 0xFFE65200)
    val SuccessSolid get() = pick(0xFF4AB89A, 0xFF007B58)
    val Neutral get() = Muted

    val OverlayConnected get() = if (Palette.dark) 0.5f else 0.2f
    val OverlayDisconnected get() = if (Palette.dark) 0.5f else 0.24f

    val Land get() = pick(0xFF292733, 0xFFF1EEEB, 0xFF252628)
    val LandActive get() = pick(0xFF4A4658, 0xFFD1CFCD, 0xFF45464B)
    val LandLine get() = pick(0xFF5B576B, 0xFFC2BFBC, 0xFF56575C)
}
