package app.xvpn.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Spacing scale (4dp grid). Use these for every padding, gap and spacer. */
object Space {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
    val Huge = 40.dp

    /** Left/right margin of every screen: Proton screen_padding_horizontal = 16dp. */
    val Gutter = Lg
}

/**
 * Corner radii, mirroring ProtonTheme.shapes: small 4, medium 8, large 16; dialogs/sheets use the Material 3
 * AlertDialog / ModalBottomSheet default (28dp); the nav indicator is the Material 3 full pill.
 */
object Radius {
    val Badge = 4.dp    // flags, small tags (shapes.small)
    val Small = 8.dp
    val Control = 8.dp  // buttons, chips, inputs, menus (shapes.medium)
    val Card = 16.dp    // cards, tiles (shapes.large)
    val Sheet = 28.dp   // dialogs, sheets (Material 3 default, as used by ProtonAlert)
    val Pill = 16.dp    // selected-tab indicator: fully round
}

/** Fixed dimensions shared by components. */
object Sizes {
    val Control = 48.dp     // header row, icon buttons, chips, segmented controls, compact buttons (48dp touch target)
    val Field = 56.dp       // text fields, card actions (16dp padding + 24dp line, as ProtonOutlinedTextField)
    val Button = 48.dp      // large buttons (Proton ButtonDefaults min height)
    val Row = 64.dp         // minimum height of a list row (ConnectIntentBlankRow heightIn(min = 64.dp))
    val ServerRow = 64.dp
    val Dock = 80.dp        // bottom navigation (Material 3 NavigationBar, as BottomBarView)
    val DockPillWidth = 64.dp   // selected-tab indicator (Material 3)
    val DockPillHeight = 32.dp

    val IconSm = 16.dp      // inline glyphs next to small text
    val Icon = 24.dp        // default icon: rows, menus, header actions
    val IconLg = 32.dp
    val IconHero = 40.dp    // status icons on the home screen
    val Tile = 64.dp        // tinted icon tile on settings pages
}

/** Shadow depth. The UI is flat; only surfaces that float above the page get a shadow. */
object Elevation {
    val Menu = 3.dp   // Material 3 DropdownMenu default, which Proton does not override
}

/** The type scale. Colors are applied at the call site. */
object Type {
    private val Font = FontFamily.Default
    private fun style(size: Int, weight: FontWeight, line: Int) = TextStyle(
        fontFamily = Font, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = 0.sp,
    )

    val Display = style(30, FontWeight.Bold, 36)        // connection state
    val Headline = style(28, FontWeight.Bold, 34)       // screen titles (Proton "hero": 28sp expanded title)
    val Title = style(22, FontWeight.SemiBold, 28)      // sub-page titles, key figures
    val Subtitle = style(18, FontWeight.SemiBold, 24)   // card titles, dialog titles
    val Body = style(16, FontWeight.Normal, 22)         // default text
    val BodyMedium = style(16, FontWeight.Medium, 22)   // values, emphasised rows
    val Small = style(14, FontWeight.Normal, 20)        // descriptions, captions of rows
    val SmallMedium = style(14, FontWeight.Medium, 20)
    val Section = style(14, FontWeight.Medium, 20)      // accent section headings (body2Medium)
    val Caption = style(12, FontWeight.Normal, 16)      // hints, units, footnotes
    val Label = style(12, FontWeight.Medium, 16)        // tab labels, chart labels
    val Button = style(16, FontWeight.Medium, 24)       // large buttons
    val ButtonSmall = style(16, FontWeight.Medium, 22)  // chips, compact buttons, text actions
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp)
}
