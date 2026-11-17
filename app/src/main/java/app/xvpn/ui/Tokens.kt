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
    val Badge get() = if (Palette.spark) 6.dp else 4.dp
    val Small get() = if (Palette.spark) 12.dp else 8.dp
    val Control get() = if (Palette.spark) 16.dp else 8.dp
    val Card get() = if (Palette.spark) 24.dp else 12.dp
    val Sheet get() = if (Palette.spark) 32.dp else 28.dp
    val Button get() = if (Palette.spark) 28.dp else 8.dp
    val Pill = 16.dp
}

object Glass {
    val Blur = 24.dp
}

object Sizes {
    val Control = 48.dp
    val Field = 56.dp
    val Button get() = if (Palette.spark) 52.dp else 48.dp
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

    val SpeedRing = 170.dp
    val SpeedChart = 96.dp
    val LogMin = 170.dp
    val LogMax = 260.dp
}

object Elevation {
    val Menu = 3.dp
}

object Type {
    private val Font = FontFamily.SansSerif
    private fun style(size: Int, weight: FontWeight, line: Int, spacingEm: Float = 0f) = TextStyle(
        fontFamily = Font, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = spacingEm.em,
    )

    val Display get() = if (Palette.spark) style(30, FontWeight.Medium, 36, -0.01f) else style(30, FontWeight.Bold, 36)
    val Headline get() = TextStyle(
        fontFamily = Font, fontSize = 28.sp, lineHeight = 34.sp,
        fontWeight = if (Palette.spark) FontWeight.Medium else FontWeight.Bold,
        letterSpacing = if (Palette.spark) (-0.3).sp else 0.45.sp,
    )
    val Heading = style(20, FontWeight.Bold, 24, 0.01f)
    val Title get() = if (Palette.spark) style(22, FontWeight.Medium, 28, -0.01f) else style(22, FontWeight.SemiBold, 28)
    val Subtitle = style(18, FontWeight.SemiBold, 24)
    val Body = style(16, FontWeight.Normal, 24, 0.03f)
    val BodyMedium = style(16, FontWeight.Medium, 24, 0.03f)
    val Small = style(14, FontWeight.Normal, 20, 0.02f)
    val SmallMedium = style(14, FontWeight.Medium, 20, 0.02f)
    val Section = style(14, FontWeight.Medium, 20, 0.02f)
    val Caption = style(12, FontWeight.Normal, 16, 0.03f)
    val Label = style(12, FontWeight.Medium, 16, 0.03f)
    val Button get() = style(16, if (Palette.spark) FontWeight.Medium else FontWeight.Normal, 24, 0.03f)
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
