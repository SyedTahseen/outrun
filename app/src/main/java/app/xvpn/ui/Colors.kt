package app.xvpn.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object C {
    val Bg = Color(0xFF1C1B24)
    val Card = Color(0xFF282731)
    val CardBorder = Color(0xFF3A3944)
    val Accent = Color(0xFF8B6CF6)
    val Button = Color(0xFF6D4AFF)
    val ButtonGrey = Color(0xFF3A3846)
    val AccentDeep = Color(0xFF1B2250)
    val AccentMid = Color(0xFF3B3175)
    val NavPill = Color(0xFF4A3F8A)
    val Text = Color(0xFFF2F2F7)
    val Muted = Color(0xFF9A98A8)
    val Green = Color(0xFF2DF5C0)
    val Red = Color(0xFFF0728C)
    val Amber = Color(0xFFFFB74D)
    val Neutral = Color(0xFFA6A3B2)   // connecting state: wash, dot and spinner are grey, not amber
    // map colours sampled from the design: other countries / the country you are in / borders
    val Land = Color(0xFF292734)
    val LandActive = Color(0xFF4A4657)
    val LandLine = Color(0xFF5A5866)
}

/** Corner radii measured from the design. Use these instead of hardcoding numbers. */
object Radius {
    val Badge = 4.dp    // flag tiles
    val Small = 8.dp    // buttons, location pill, chips, inputs, inner boxes
    val Card = 16.dp    // cards, panels, dialogs, menus
    val Pill = 16.dp    // selected nav-bar pill (32dp tall, fully round)
}
