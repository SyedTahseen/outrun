package app.xvpn.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The one color palette of the app. Nothing outside this file should contain a color literal.
 *
 * Surfaces, from back to front:  Bg  ->  Card  ->  Control  (a Card holds Controls; Bg sits behind everything).
 * Primary is a *fill* (buttons, selected chips, switches); Accent is the same hue as *text / icons / lines* on dark.
 */
object C {
    // Surfaces
    // Values marked [PV] are taken from Proton VPN's own resources (tv* colors in res/values/colors.xml, VpnColors.kt,
    // VpnStatusView.kt). The rest are unverifiable from the open-source repo (they live in the proton-core library).
    val Bg = Color(0xFF1C1B24)          // [PV] backgroundNorm (tvBackground): screen background, sheets
    val Card = Color(0xFF292733)        // [PV] backgroundSecondary (tvCardBackground): cards, bottom bar, menus, dialogs
    val Header = Card                   // header and status bar once the page is scrolled (same as Card)
    val CardOverlay = Color(0xDB292733) // [PV] backgroundSecondary @ 0.86 over the map (VpnStatusView)
    val Control = Color(0xFF3B3747)     // [PC] interactionWeakNorm = shade20 (BlackCurrant);  unselected chips, secondary buttons, pills
    val Border = Color(0xFF3B3747)      // [PC] separatorNorm = shade20;  1dp hairlines: card borders, dividers, chart grid
    val Divider = Color(0x0AFFFFFF)     // hairlines between rows (4 % white: subtle on both Bg and Card)
    val Outline = Color(0xFF4F4D5C)     // border of outlined buttons
    val Track = Color(0xFF2A2933)       // faint guide lines on dark (gauge track)
    val Scrim = Color(0x66000000)       // dark chip over the map

    // Brand
    val Primary = Color(0xFF6D4AFF)     // [PV] interactionNorm (tvAccent): filled controls, checked switch, selected radio
    val Accent = Color(0xFF8A6EFF)      // [PV] textAccent / brandNorm on dark (tvAccentLighten): accent text, icons, section headings
    val AccentMid = Color(0xFF3B3175)   // tinted tile (top of gradient), inactive chart bars
    val AccentDeep = Color(0xFF1B2250)  // tinted tile (bottom of gradient)
    val NavPill = Accent.copy(alpha = 0.32f).compositeOver(Card) // [PV] BottomBarView: textAccent @ 0.32 over the bar
    val Blue = Color(0xFF2F7BF0)        // speed-gauge tip

    // Content
    val Text = Color(0xFFFFFFFF)            // textNorm (white on dark)
    val TextDim = Color(0xFFC4C3CB)     // gauge numbers
    val Muted = Color(0xFFA7A4B5)       // [PV] textWeak / shade80 (tvInMaintenance): secondary text and icons
    val Shade50 = Color(0xFF6D697D)     // [PC] shade60 (Dolphin) = textHint/iconHint; [PV] tvButtonBg. (name kept for call sites): hints, placeholder, unchecked switch track, unselected radio
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryDisabled = Color(0xFF372580) // [PV] interactionDisabled = brandDarken40: disabled solid button fill
    val Disabled = Color(0xFF5B576B)    // [PC] shade50 (Smoky) = textDisabled / iconDisabled

    // Status
    val Green = Color(0xFF2CFFCC)       // [PV] vpnGreen (dark): connected, ok, download
    val Red = Color(0xFFF08FA4)         // [PC] notificationError (dark, Mauvelous), used by VpnStatusView/ProtonSnackbar: disconnected, error, upload, destructive
    val Amber = Color(0xFFFFB84D)       // [PC] notificationWarning (dark, TexasRose): warning, favorite
    val Neutral = Color(0xFFA6A3B2)     // in-progress

    // World map
    val Land = Card
    val LandActive = Color(0xFF4A4658) // [PC] shade40 (GunPowder) / interactionWeakPressed
    val LandLine = Color(0xFF5A5866)
}
