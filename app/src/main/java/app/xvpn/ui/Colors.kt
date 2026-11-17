package app.xvpn.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/** Which palette [C] resolves to. Set once per composition root in [OutrunTheme] from the system setting; the activity is recreated when it changes. */
object Palette {
    // Snapshot state: every composable / draw block that reads a C.* colour re-runs when the theme changes.
    private val state = mutableStateOf(true)
    var dark: Boolean
        get() = state.value
        set(v) { if (state.value != v) state.value = v }
}

private fun pick(dark: Long, light: Long) = Color(if (Palette.dark) dark else light)

/**
 * The one color palette of the app. Nothing outside this file should contain a color literal.
 * Every entry resolves to proton-core `ProtonColors.Dark` or `ProtonColors.Light` (plus the VPN app's `vpnGreen`).
 *
 * Surfaces, from back to front:  Bg  ->  Card  ->  Control  (a Card holds Controls; Bg sits behind everything).
 * Primary is a *fill* (buttons, selected chips, switches); Accent is the same hue as *text / icons / lines* (lighter on dark).
 * Tags: [PC] proton-core ProtonColors, [PV] Proton VPN app, [own] Outrun-specific (no Proton equivalent).
 */
object C {
    // Surfaces
    val Bg get() = pick(0xFF1C1B24, 0xFFFFFFFF)          // [PC] backgroundNorm (dark shade10 BalticSea / light shade0 white)
    val Card get() = pick(0xFF292733, 0xFFF5F4F2)        // [PC] backgroundSecondary (dark shade15 Bastille / light shade10 Carrara)
    val Header get() = Card                              // header and status bar once the page is scrolled
    val CardOverlay get() = Card.copy(alpha = 0.86f)     // [PV] backgroundSecondary @ 0.86 over the map (VpnStatusView)
    val Control get() = pick(0xFF3B3747, 0xFFEAE7E4)     // [PC] interactionWeakNorm = shade20
    val Border get() = pick(0xFF3B3747, 0xFFEAE7E4)      // [PC] separatorNorm = shade20
    val Divider get() = pick(0x0AFFFFFF, 0x0A000000)     // [own] hairlines between rows
    val Outline get() = pick(0xFF4F4D5C, 0xFFD1CFCD)     // [own] border of outlined buttons (light: shade40)
    val Track get() = pick(0xFF2A2933, 0xFFEAE7E4)       // [own] faint guide lines (gauge track)
    val Scrim get() = Color(0x66000000)                  // dark chip over the map

    // Brand
    val Primary = Color(0xFF6D4AFF)                      // [PC] interactionNorm = brandNorm: filled controls, checked switch, selected radio
    val Accent get() = pick(0xFF8A6EFF, 0xFF6D4AFF)      // [PC] textAccent = brandLighten20 (dark) / brandNorm (light)
    val AccentMid get() = pick(0xFF3B3175, 0xFFE4DDFF)   // [own] tinted tile (top of gradient), inactive chart bars
    val AccentDeep get() = pick(0xFF1B2250, 0xFFCFC5FF)  // [own] tinted tile (bottom of gradient)
    val NavPill get() = Accent.copy(alpha = 0.32f).compositeOver(Card) // [PV] BottomBarView: textAccent @ 0.32 over the bar
    val Blue = Color(0xFF2F7BF0)                         // [own] speed-gauge tip

    // Content
    val Text get() = pick(0xFFFFFFFF, 0xFF0C0C14)        // [PC] textNorm = shade100
    val TextDim get() = pick(0xFFC4C3CB, 0xFF4A4658)     // [own] gauge numbers
    val Muted get() = pick(0xFFA7A4B5, 0xFF706D6B)       // [PC] textWeak / iconWeak = shade80
    val Shade50 get() = pick(0xFF6D697D, 0xFF999693)     // [PC] textHint / iconHint = shade60 (name kept for call sites)
    val OnPrimary = Color(0xFFFFFFFF)                    // white on a Primary fill, both themes
    val PrimaryDisabled get() = pick(0xFF372580, 0xFFC4B7FF) // [PC] interactionDisabled: brandDarken40 (dark) / brandLighten40 (light)
    val Disabled get() = pick(0xFF5B576B, 0xFFC2BFBC)    // [PC] textDisabled / iconDisabled = shade50
    val Inverted get() = pick(0xFF0C0C14, 0xFFFFFFFF)    // [PC] textInverted = shade0

    // Status
    val Green get() = pick(0xFF2CFFCC, 0xFF1C9C7C)       // [PV] vpnGreen
    val Red get() = pick(0xFFF08FA4, 0xFFCC2D4F)         // [PC] notificationError (Mauvelous / Pomegranate)
    val Amber get() = pick(0xFFFFB84D, 0xFFE65200)       // [PC] notificationWarning (TexasRose / Sunglow)
    val SuccessSolid get() = pick(0xFF4AB89A, 0xFF007B58) // [PC] notificationSuccess (PuertoRico / Apple): snackbar fill
    val Neutral get() = Muted                            // [PV] connecting pin = iconWeak

    /** Status overlay gradient alphas: [PV] VpnStatusView.getOverlayGradientTop. */
    val OverlayConnected get() = if (Palette.dark) 0.5f else 0.2f
    val OverlayDisconnected get() = if (Palette.dark) 0.5f else 0.24f

    // World map: [PV] HomeMap MapConfig
    val Land get() = pick(0xFF292733, 0xFFF1EEEB)       // country = shade15
    val LandActive get() = pick(0xFF4A4658, 0xFFD1CFCD)  // selected / connecting / connected = shade40
    val LandLine get() = pick(0xFF5B576B, 0xFFC2BFBC)    // border = shade50
}
