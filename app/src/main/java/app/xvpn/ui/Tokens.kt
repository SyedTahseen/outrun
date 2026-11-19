package app.xvpn.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object Space {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
    val Huge = 40.dp

    val Gutter = Lg
}

object Radius {
    val Badge = 4.dp
    val Small = 8.dp
    val Control = 8.dp
    val Card = 12.dp
    val Sheet = 28.dp
    val Pill = 16.dp
}

object Sizes {
    val Control = 48.dp
    // Proton's filter chips are Material 3 Buttons with the default 40dp min height (ButtonDefaults.MinHeight).
    val Chip = 40.dp
    val Field = 56.dp
    val Button = 48.dp
    val Row = 64.dp
    val ServerRow = 64.dp
    val TopBar = 64.dp
    val TopBarExpanded = 112.dp
    val Dock = 80.dp
    val DockPillWidth = 64.dp
    val DockPillHeight = 32.dp

    val MaxContent = 800.dp
    val Dot = 8.dp
    val IconSm = 16.dp
    val Icon = 24.dp
    val IconLg = 32.dp
    val IconHero = 40.dp
    val Tile = 64.dp
    val FlagArea = 30.dp
    val FlagWidth = 30.dp
    val FlagHeight = 20.dp
    val StatusOverlay = 200.dp
    val StatusMaxWidth = 480.dp
    val ListTopOffset = 68.dp
    val ListGradient = 100.dp
    val CardRowMin = 42.dp
    val SettingRowMin = 56.dp
    val Border = 1.dp
    val Spinner = 2.dp

    val SpeedRing = 170.dp
    val SpeedChart = 96.dp
    val LogMin = 170.dp
    val LogMax = 260.dp
}

object Elevation {
    val Menu = 3.dp
    // Proton uses ButtonDefaults.buttonElevation() (Material 3), which is 0dp at rest.
    val Button = 0.dp
}

object Type {
    private val Font = FontFamily.SansSerif
    private fun style(size: Int, weight: FontWeight, line: Int, spacingEm: Float = 0f) = TextStyle(
        fontFamily = Font, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = spacingEm.em,
    )

    val Headline = TextStyle(
        fontFamily = Font, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, letterSpacing = 0.45.sp,
    )
    val Heading = style(20, FontWeight.Bold, 24, 0.01f)
    val Subheadline = style(20, FontWeight.Normal, 24, 0.01f)
    val Body = style(16, FontWeight.Normal, 24, 0.03f)
    val BodyBold = style(16, FontWeight.Bold, 24, 0.03f)
    val BodyMedium = style(16, FontWeight.Medium, 24, 0.03f)
    val Small = style(14, FontWeight.Normal, 20, 0.02f)
    val SmallMedium = style(14, FontWeight.Medium, 20, 0.02f)
    val Section = style(14, FontWeight.Medium, 20, 0.02f)
    val Caption = style(12, FontWeight.Normal, 16, 0.03f)
    val Label = style(12, FontWeight.Medium, 16, 0.03f)
    val Button = style(16, FontWeight.Normal, 24, 0.03f)
    val ButtonSmall = style(16, FontWeight.Medium, 24, 0.03f)
    val Overline = style(10, FontWeight.Normal, 16, 0.03f)
    val OverlineMedium = style(10, FontWeight.Medium, 16, 0.03f)
    val LogLine = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 12.sp, letterSpacing = 0.sp)
    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp)
}

@Composable
fun largeScreenContentPadding(): Dp {
    val width = LocalConfiguration.current.screenWidthDp.dp
    val extra = when {
        width >= 840.dp -> 128.dp
        width >= 600.dp -> 56.dp
        else -> 0.dp
    }
    val maxWidthPadding = (width - Sizes.MaxContent) / 2 - Space.Gutter
    return if (maxWidthPadding > extra) maxWidthPadding else extra
}
