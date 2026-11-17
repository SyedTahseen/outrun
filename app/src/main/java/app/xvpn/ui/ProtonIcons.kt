package app.xvpn.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import app.xvpn.R

/**
* Proton's own icon set (vector drawables copied from proton-core `presentation` module, `ic_proton_*`).
* Same type as the Material icons they replace, so every `Icon(...)` / `IconAction(...)` call site is unchanged.
* Colors are applied by the call site's tint; the drawables are white.
*/
object ProtonIcons {
    val ArrowDown: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_arrow_down)
    val ArrowDownArrowUp: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_arrow_down_arrow_up)
    val ArrowLeft: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_arrow_left)
    val ArrowOutSquare: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_arrow_out_square)
    val ArrowRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_arrow_right)
    val ArrowUp: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_arrow_up)
    val ArrowUpFromSquare: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_arrow_up_from_square)
    val ArrowsLeftRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_arrows_left_right)
    val ArrowsRotate: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_arrows_rotate)
    val ArrowsSwapRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_arrows_swap_right)
    val Bell: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_bell)
    val Bolt: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_bolt)
    val Bug: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_bug)
    val ChartLine: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_chart_line)
    val Checkmark: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_checkmark)
    val ChevronDown: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_chevron_down)
    val ChevronRight: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_chevron_right)
    val Code: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_code)
    val Cross: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_cross)
    val Earth: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_earth)
    val ExclamationCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_exclamation_circle)
    val File: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_file)
    val InfoCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_info_circle)
    val Language: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_language)
    val LifeRing: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_life_ring)
    val LinesLongToSmall: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_lines_long_to_small)
    val Link: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_link)
    val Lock: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_lock)
    val LockOpen: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_lock_open)
    val Magnifier: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_magnifier)
    val MinusCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_minus_circle)
    val Mobile: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_mobile)
    val Pen: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_pen)
    val Plus: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_plus)
    val PlusCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_plus_circle)
    val PowerOff: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_power_off)
    val QrCode: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_qr_code)
    val Servers: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_servers)
    val Shield: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_shield)
    val Sliders: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_sliders)
    val SquaresInSquare: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_squares_in_square)
    val Star: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_star)
    val StarFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_star_filled)
    val ThreeDotsVertical: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_three_dots_vertical)
    val Trash: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_trash)
    val WindowTerminal: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_proton_window_terminal)
}
