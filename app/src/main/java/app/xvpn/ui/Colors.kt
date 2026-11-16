package app.xvpn.ui

import androidx.compose.ui.graphics.Color

/**
 * The one color palette of the app. Nothing outside this file should contain a color literal.
 *
 * Surfaces, from back to front:  Bg  ->  Card  ->  Control  (a Card holds Controls; Bg sits behind everything).
 * Primary is a *fill* (buttons, selected chips, switches); Accent is the same hue as *text / icons / lines* on dark.
 */
object C {
    // Surfaces
    val Bg = Color(0xFF1C1B24)          // screen background
    val Card = Color(0xFF282731)        // cards, bars, menus, dialogs, sheets
    val CardOverlay = Color(0xEB282731) // Card over the map (92 %)
    val Control = Color(0xFF282731)     // unselected chips, secondary buttons: same color as Card
    val Border = Color(0xFF3A3944)      // 1dp hairlines: card borders, dividers, chart grid
    val Divider = Color(0x0AFFFFFF)     // hairlines between rows (4 % white: subtle on both Bg and Card)
    val Outline = Color(0xFF4F4D5C)     // border of outlined buttons
    val Track = Color(0xFF2A2933)       // faint guide lines on dark (gauge track)
    val Scrim = Color(0x66000000)       // dark chip over the map

    // Brand
    val Primary = Color(0xFF6D4AFF)     // filled controls
    val Accent = Color(0xFF8B6CF6)      // accent text, icons, strokes
    val AccentMid = Color(0xFF3B3175)   // tinted tile (top of gradient), inactive chart bars
    val AccentDeep = Color(0xFF1B2250)  // tinted tile (bottom of gradient)
    val NavPill = Color(0xFF4A3F8A)     // selected tab indicator
    val Blue = Color(0xFF2F7BF0)        // speed-gauge tip

    // Content
    val Text = Color(0xFFF2F2F7)
    val TextDim = Color(0xFFC4C3CB)     // gauge numbers
    val Muted = Color(0xFF9A98A8)       // secondary text and icons
    val OnPrimary = Color(0xFFFFFFFF)

    // Status
    val Green = Color(0xFF2DF5C0)       // connected, ok, download
    val Red = Color(0xFFF0728C)         // disconnected, error, upload, destructive
    val Amber = Color(0xFFFFB74D)       // warning, favorite
    val Neutral = Color(0xFFA6A3B2)     // in-progress

    // World map
    val Land = Color(0xFF292734)
    val LandActive = Color(0xFF4A4657)
    val LandLine = Color(0xFF5A5866)
}
