package app.xvpn.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
 * Corner radii, mirroring ProtonShapes in proton-core (small 4, medium 8, large 12); dialogs/sheets use the Material 3
 * AlertDialog / ModalBottomSheet default (28dp); the nav indicator is the Material 3 full pill.
 */
object Radius {
    val Badge = 4.dp    // flags, small tags (shapes.small)
    val Small = 8.dp
    val Control = 8.dp  // buttons, chips, inputs, menus (shapes.medium)
    val Card = 12.dp    // cards, tiles (ProtonShapes.large = ExtraLargeCornerRadius = 12dp)
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
    val TopBar = 64.dp      // Material 3 TopAppBar / collapsed MediumTopAppBar
    val TopBarExpanded = 112.dp // expanded MediumTopAppBar
    val Dock = 80.dp        // bottom navigation (Material 3 NavigationBar, as BottomBarView)
    val DockPillWidth = 64.dp   // selected-tab indicator (Material 3)
    val DockPillHeight = 32.dp

    val MaxContent = 800.dp // widest page content (Proton ProtonTheme.MaxContentWidth)
    val Dot = 8.dp          // active-server dot (server_load_dot_size)
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
    // proton-core ProtonTypography: every style carries letter spacing, and body/caption line heights are 24/20/16.
    private fun style(size: Int, weight: FontWeight, line: Int, spacingEm: Float = 0f) = TextStyle(
        fontFamily = Font, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = spacingEm.em,
    )

    val Display = style(30, FontWeight.Bold, 36)        // connection state (Outrun's own)
    val Headline = TextStyle(                           // [PC] hero: 28sp W700, 34sp line, 0.45sp tracking
        fontFamily = Font, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, letterSpacing = 0.45.sp,
    )
    val Heading = style(20, FontWeight.Bold, 24, 0.01f)    // [PC] headline: dialog titles, "Protected" status
    val Title = style(22, FontWeight.SemiBold, 28)      // sub-page titles, key figures (no proton-core equivalent)
    val Subtitle = style(18, FontWeight.SemiBold, 24)   // card titles, dialog titles (no proton-core equivalent)
    val Body = style(16, FontWeight.Normal, 24, 0.03f)         // [PC] body1Regular
    val BodyMedium = style(16, FontWeight.Medium, 24, 0.03f)   // [PC] body1Medium
    val Small = style(14, FontWeight.Normal, 20, 0.02f)        // [PC] body2Regular
    val SmallMedium = style(14, FontWeight.Medium, 20, 0.02f)  // [PC] body2Medium
    val Section = style(14, FontWeight.Medium, 20, 0.02f)      // accent section headings (body2Medium)
    val Caption = style(12, FontWeight.Normal, 16, 0.03f)      // [PC] captionRegular
    val Label = style(12, FontWeight.Medium, 16, 0.03f)        // [PC] captionMedium
    val Button = style(16, FontWeight.Normal, 24, 0.03f)       // [PV] ProtonButton style = defaultUnspecified = body1Regular
    val ButtonSmall = style(16, FontWeight.Medium, 24, 0.03f)  // chips, compact buttons, text actions
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp)
}
