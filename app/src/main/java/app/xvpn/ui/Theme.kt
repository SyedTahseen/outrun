package app.xvpn.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import app.xvpn.data.Store
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb

/** Root of every activity: scales the layout to the device, then applies the theme. */
@Composable
fun OutrunApp(content: @Composable () -> Unit) {
    DesignScale { OutrunTheme(content) }
}

/** Material theme built from the app's own tokens, so any stock Material component that slips in looks like the rest of the app. */
@Composable
fun OutrunTheme(content: @Composable () -> Unit) {
    val dark = AppTheme.isDark(isSystemInDarkTheme())   // Settings > Theme: Light / Dark / Device theme
    Palette.dark = dark
    // Re-apply the system bars when the user changes the theme while the app is open.
    val activity = LocalContext.current as? ComponentActivity
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(dark) { if (firstRun[0]) firstRun[0] = false else activity?.applySystemBars(dark) }
    val scheme = if (dark) {
        darkColorScheme(
            primary = C.Primary, onPrimary = C.OnPrimary,
            secondary = C.Accent, onSecondary = C.OnPrimary,
            background = C.Bg, onBackground = C.Text,
            surface = C.Card, onSurface = C.Text,
            surfaceVariant = C.Control, onSurfaceVariant = C.Muted,
            surfaceContainer = C.Card, surfaceContainerHigh = C.Card, surfaceContainerHighest = C.Card,
            outline = C.Outline, outlineVariant = C.Border,
            error = C.Red, onError = C.OnPrimary,
        )
    } else {
        lightColorScheme(
            primary = C.Primary, onPrimary = C.OnPrimary,
            secondary = C.Accent, onSecondary = C.OnPrimary,
            background = C.Bg, onBackground = C.Text,
            surface = C.Card, onSurface = C.Text,
            surfaceVariant = C.Control, onSurfaceVariant = C.Muted,
            surfaceContainer = C.Card, surfaceContainerHigh = C.Card, surfaceContainerHighest = C.Card,
            outline = C.Outline, outlineVariant = C.Border,
            error = C.Red, onError = C.OnPrimary,
        )
    }
    val typography = Typography(
        displaySmall = Type.Display, headlineMedium = Type.Headline, titleLarge = Type.Title, titleMedium = Type.Subtitle,
        bodyLarge = Type.Body, bodyMedium = Type.Small, bodySmall = Type.Caption,
        labelLarge = Type.ButtonSmall, labelMedium = Type.Label, labelSmall = Type.Caption,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(Radius.Badge), small = RoundedCornerShape(Radius.Small),
        medium = RoundedCornerShape(Radius.Control), large = RoundedCornerShape(Radius.Card),
        extraLarge = RoundedCornerShape(Radius.Sheet),
    )
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
        AppIndication {
            CompositionLocalProvider(
                LocalContentColor provides C.Text,
                LocalTextSelectionColors provides TextSelectionColors(handleColor = C.Accent, backgroundColor = C.Accent.copy(alpha = 0.3f)),
                content = content,
            )
        }
    }
}

/**
 * Proton VPN lays out in real dp and honours the system font scale; it does not rescale density to a reference width.
 * This used to force "shortest side = 360dp" and fontScale = 1, which made every size differ from Proton on any
 * device that is not 360dp wide. Kept as a pass-through so the call site (and the history) stays obvious.
 */
@Composable
internal fun DesignScale(content: @Composable () -> Unit) = content()

/** The user's theme choice (Settings > Theme), mirroring Proton's ThemeType: Device theme / Light / Dark. */
object AppTheme {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    /** Snapshot state, so changing it recomposes the whole app at once, without recreating the activity. */
    var mode by mutableStateOf(SYSTEM)
        private set

    fun load(ctx: Context) { mode = Store(ctx).themeMode }

    fun set(ctx: Context, value: String) {
        Store(ctx).themeMode = value
        mode = value
    }

    fun isDark(systemDark: Boolean): Boolean = when (mode) { LIGHT -> false; DARK -> true; else -> systemDark }
}

private fun Context.systemDark() =
    (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

/** Transparent system bars drawn over the app background; shared by every activity. */
internal fun ComponentActivity.setUpEdgeToEdge(navigationBarFromBg: Boolean = false) {
    AppTheme.load(this)
    val dark = AppTheme.isDark(systemDark())
    Palette.dark = dark
    applySystemBars(dark, navigationBarFromBg)
}

internal fun ComponentActivity.applySystemBars(dark: Boolean, navigationBarFromBg: Boolean = false) {
    val navigationBarColor = if (navigationBarFromBg) C.Bg.toArgb() else android.graphics.Color.TRANSPARENT
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
        navigationBarStyle = SystemBarStyle.auto(navigationBarColor, navigationBarColor) { dark },
    )
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(C.Bg.toArgb()))
}

/** Version name from the manifest, or null if it can't be read. */
internal fun Context.appVersionName(): String? =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

/** Tells the parent whether the page is scrolled (it tints the status strip), and resets that when the page leaves. */
@Composable
internal fun ReportScrolled(scrolled: Boolean, onScrolledChange: (Boolean) -> Unit) {
    LaunchedEffect(scrolled) { onScrolledChange(scrolled) }
    DisposableEffect(Unit) { onDispose { onScrolledChange(false) } }
}
