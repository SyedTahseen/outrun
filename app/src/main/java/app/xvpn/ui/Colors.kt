package app.xvpn.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

object Palette {
    private val state = mutableStateOf(true)
    private val sparkState = mutableStateOf(false)
    var dark: Boolean
        get() = state.value
        set(v) { if (state.value != v) state.value = v }

    /** Spark theme: always dark, monochrome, frosted-glass surfaces. */
    var spark: Boolean
        get() = sparkState.value
        set(v) { if (sparkState.value != v) sparkState.value = v }
}

private fun pick(dark: Long, light: Long, spark: Long = dark) =
    Color(if (Palette.spark) spark else if (Palette.dark) dark else light)

object C {
    val Bg get() = pick(0xFF1C1B24, 0xFFFFFFFF, 0xFF18191B)
    val Card get() = pick(0xFF292733, 0xFFF5F4F2, 0xFF2A2B2D)
    val Header get() = Card
    val CardOverlay get() = if (Palette.spark) Bg.copy(alpha = 0.62f) else Card.copy(alpha = 0.86f)
    val Control get() = pick(0xFF3B3747, 0xFFEAE7E4, 0x1FFFFFFF)
    val Border get() = pick(0xFF3B3747, 0xFFEAE7E4, 0x24FFFFFF)
    val Divider get() = pick(0x0AFFFFFF, 0x0A000000, 0x14FFFFFF)
    val Outline get() = pick(0xFF4F4D5C, 0xFFD1CFCD, 0x3DFFFFFF)
    val Track get() = pick(0xFF2A2933, 0xFFEAE7E4, 0x1AFFFFFF)
    val Scrim get() = Color(0x66000000)

    /** Page-level surface that lets the Spark ambient backdrop show through. */
    val Surface get() = if (Palette.spark) Bg.copy(alpha = 0f) else Bg

    val Primary get() = if (Palette.spark) Color(0xFFFFFFFF) else Color(0xFF6D4AFF)
    val Accent get() = pick(0xFF8A6EFF, 0xFF6D4AFF, 0xFFF2F3F5)
    val AccentMid get() = pick(0xFF3B3175, 0xFFE4DDFF, 0xFF3A3B3E)
    val AccentDeep get() = pick(0xFF1B2250, 0xFFCFC5FF, 0xFF232426)
    val NavPill get() =
        if (Palette.spark) Color.White.copy(alpha = 0.14f).compositeOver(Card)
        else Accent.copy(alpha = 0.32f).compositeOver(Card)
    val Blue = Color(0xFF2F7BF0)

    val Text get() = pick(0xFFFFFFFF, 0xFF0C0C14, 0xFFF7F8FA)
    val TextDim get() = pick(0xFFC4C3CB, 0xFF4A4658, 0xFFD2D3D6)
    val Muted get() = pick(0xFFA7A4B5, 0xFF706D6B, 0xFFA6A7AC)
    val Shade50 get() = pick(0xFF6D697D, 0xFF999693, 0xFF6F7075)
    val OnPrimary get() = if (Palette.spark) Color(0xFF18191B) else Color(0xFFFFFFFF)
    val PrimaryDisabled get() = pick(0xFF372580, 0xFFC4B7FF, 0xFF6B6C70)
    val Disabled get() = pick(0xFF5B576B, 0xFFC2BFBC, 0xFF55565A)
    val Inverted get() = pick(0xFF0C0C14, 0xFFFFFFFF, 0xFF18191B)

    val Green get() = pick(0xFF2CFFCC, 0xFF1C9C7C, 0xFF4CD987)
    val Red get() = pick(0xFFF08FA4, 0xFFCC2D4F, 0xFFFF7A85)
    val Amber get() = pick(0xFFFFB84D, 0xFFE65200, 0xFFFFB84D)
    val SuccessSolid get() = pick(0xFF4AB89A, 0xFF007B58, 0xFF3FA878)
    val Neutral get() = Muted

    val OverlayConnected get() = if (Palette.dark) 0.5f else 0.2f
    val OverlayDisconnected get() = if (Palette.dark) 0.5f else 0.24f

    val Land get() = pick(0xFF292733, 0xFFF1EEEB, 0xFF26272A)
    val LandActive get() = pick(0xFF4A4658, 0xFFD1CFCD, 0xFF3D3E42)
    val LandLine get() = pick(0xFF5B576B, 0xFFC2BFBC, 0xFF4A4B4F)

    // ---- Spark glass (sampled from the reference screenshot) ----
    /** Glass fill: ~11% white at the top fading to ~7% (card #2A2B2D over bg #18191B). */
    val GlassTop = Color(0x1CFFFFFF)
    val GlassBottom = Color(0x12FFFFFF)
    /** Hairline edge: bright top-left, soft bottom-right. */
    val GlassEdgeStart = Color(0x3DFFFFFF)
    val GlassEdgeMid = Color(0x0FFFFFFF)
    val GlassEdgeEnd = Color(0x1FFFFFFF)
    /** Colour glows that sit behind the glass (warm top-right, cool left). */
    val AmbientWarm = Color(0xFF6B4A3C)
    val AmbientCool = Color(0xFF3A4A7A)
    val AmbientDeep = Color(0xFF2B2F45)
}
