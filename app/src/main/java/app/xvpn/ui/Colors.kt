package app.xvpn.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

object Palette {
    private val state = mutableStateOf(true)
    var dark: Boolean
        get() = state.value
        set(v) { if (state.value != v) state.value = v }

    private val blackState = mutableStateOf(false)
    /** Black theme: the dark palette with an AMOLED black background and neutral greys. Only meaningful while [dark]. */
    var black: Boolean
        get() = blackState.value
        set(v) { if (blackState.value != v) blackState.value = v }

    private val accentState = mutableStateOf(AccentSet.of(Accents.Default))
    /** Every accent-dependent colour in [C] is derived from this one set. */
    var accentSet: AccentSet
        get() = accentState.value
        private set(v) { accentState.value = v }
    fun setAccent(base: Color) { if (accentSet.primary != base) accentSet = AccentSet.of(base) }
}

/** A user-selectable accent colour. */
class AccentPreset(val id: String, val name: String, val color: Color)

object Accents {
    val Default = Color(0xFF6D4AFF)
    val Presets = listOf(
        AccentPreset("purple", "Purple", Default),
        AccentPreset("blue", "Blue", Color(0xFF2563EB)),
        AccentPreset("teal", "Teal", Color(0xFF0F766E)),
        AccentPreset("green", "Green", Color(0xFF15803D)),
        AccentPreset("orange", "Orange", Color(0xFFC2410C)),
        AccentPreset("pink", "Pink", Color(0xFFBE185D)),
    )
    fun nameOf(c: Color) = Presets.firstOrNull { it.color == c }?.name ?: "Custom"
}

/**
 * All the shades the UI needs, derived from one brand colour. For the default purple the original
 * hand-picked values are kept exactly; other colours use the same recipe (mix with white / darken).
 */
class AccentSet(
    val primary: Color,      // buttons, switches, selected boxes (same in light & dark)
    val accentDark: Color,   // text/icons accent on dark backgrounds
    val darken20: Color,
    val midDark: Color,
    val deepDark: Color,
    val tint: Color,         // pale tint used on light backgrounds / disabled solid
) {
    companion object {
        fun of(p: Color): AccentSet {
            if (p == Accents.Default) return AccentSet(
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

/** [black] defaults to the dark value, so only the colours that differ in the black theme need a third argument. */
private fun pick(dark: Long, light: Long, black: Long = dark) =
    Color(if (!Palette.dark) light else if (Palette.black) black else dark)

object C {
    val Bg get() = pick(0xFF1C1B24, 0xFFFFFFFF, 0xFF000000)
    // Proton backgroundDeep: shade0 (Cinder) in dark, shade15 (Pampas) in light. Used by the connection details panel.
    val Deep get() = pick(0xFF0C0C14, 0xFFF1EEEB, 0xFF000000)
    val Card get() = pick(0xFF292733, 0xFFF5F4F2, 0xFF1D1D1D)
    val Header get() = Card
    val CardOverlay get() = Card.copy(alpha = 0.86f)
    val Control get() = pick(0xFF3B3747, 0xFFEAE7E4, 0xFF2E2E2E)
    val Border get() = pick(0xFF3B3747, 0xFFEAE7E4, 0xFF2E2E2E)
    val Outline get() = pick(0xFF4A4658, 0xFFD1CFCD, 0xFF3D3D3D)
    val Scrim get() = pick(0x85000000, 0x7A17181C)

    val Primary get() = Palette.accentSet.primary
    val Accent get() = if (Palette.dark) Palette.accentSet.accentDark else Palette.accentSet.primary
    val BrandDarken20 get() = Palette.accentSet.darken20
    val AccentMid get() = if (Palette.dark) Palette.accentSet.midDark else Palette.accentSet.tint
    val AccentDeep get() = if (Palette.dark) Palette.accentSet.deepDark else Palette.accentSet.tint
    val NavPill get() = Accent.copy(alpha = 0.32f).compositeOver(Card)
    val Blue = Color(0xFF2F7BF0)

    val Text get() = pick(0xFFFFFFFF, 0xFF0C0C14)
    val Muted get() = pick(0xFFA7A4B5, 0xFF706D6B, 0xFFA6A6A6)
    val TextHint get() = pick(0xFF6D697D, 0xFF999693, 0xFF6B6B6B)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryDisabled get() = AccentMid
    val SolidDisabled get() = Palette.accentSet.tint
    val Disabled get() = pick(0xFF5B576B, 0xFFC2BFBC, 0xFF555555)
    val Inverted get() = pick(0xFF0C0C14, 0xFFFFFFFF)

    val Green get() = pick(0xFF2CFFCC, 0xFF1C9C7C)
    val Red get() = pick(0xFFF08FA4, 0xFFCC2D4F)
    val Amber get() = pick(0xFFFFB84D, 0xFFE65200)
    val SuccessSolid get() = pick(0xFF4AB89A, 0xFF007B58)
    val Neutral get() = Muted

    val OverlayConnected get() = if (Palette.dark) 0.5f else 0.2f
    val OverlayDisconnected get() = if (Palette.dark) 0.5f else 0.24f

    val Land get() = pick(0xFF292733, 0xFFF1EEEB, 0xFF1D1D1D)
    val LandActive get() = pick(0xFF4A4658, 0xFFD1CFCD, 0xFF3D3D3D)
    val LandLine get() = pick(0xFF5B576B, 0xFFC2BFBC, 0xFF555555)
}
