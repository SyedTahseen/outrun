package app.xvpn.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

@Composable
fun OutrunApp(content: @Composable () -> Unit) {
    DesignScale { OutrunTheme(content) }
}

@Composable
fun OutrunTheme(content: @Composable () -> Unit) {
    val dark = AppTheme.isDark(isSystemInDarkTheme())
    Palette.dark = dark
    val activity = LocalContext.current as? ComponentActivity
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(dark) { if (firstRun[0]) firstRun[0] = false else activity?.applySystemBars(dark) }
    // Mirrors Proton core's ProtonColors.toMaterial3ThemeColors() (surface/background = backgroundNorm,
    // accents = brandNorm, outline = brandNorm, scrim = blenderNorm). Menus/dialogs keep backgroundSecondary.
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = C.Primary, onPrimary = C.OnPrimary,
        secondary = C.Primary, onSecondary = C.OnPrimary,
        tertiary = C.BrandDarken20, onTertiary = C.OnPrimary,
        background = C.Bg, onBackground = C.Text,
        surface = C.Bg, onSurface = C.Text,
        surfaceVariant = C.Bg, onSurfaceVariant = C.Text,
        surfaceContainer = C.Card, surfaceContainerHigh = C.Card, surfaceContainerHighest = C.Card,
        outline = C.Primary, outlineVariant = C.Primary,
        error = C.Red, onError = C.Inverted,
        scrim = C.Scrim,
    )
    // Same mapping as Proton core's ProtonTypography.toMaterial3ThemeTypography().
    val typography = Typography(
        displayLarge = Type.Heading, displayMedium = Type.Heading, displaySmall = Type.Heading,
        headlineLarge = Type.Subheadline, headlineMedium = Type.Subheadline, headlineSmall = Type.Subheadline,
        titleLarge = Type.BodyMedium, titleMedium = Type.BodyMedium, titleSmall = Type.BodyMedium,
        bodyLarge = Type.Body, bodyMedium = Type.Body, bodySmall = Type.Body,
        labelLarge = Type.Label, labelMedium = Type.Label, labelSmall = Type.Label,
    )
    // Proton's ProtonTheme3 does not pass shapes to Material 3, so M3 components use the stock M3 shapes.
    val shapes = Shapes()
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
 * Display size: scales every dp and sp in the app by [AppTheme.displayScale]. The system font-size
 * setting is kept on top of it. Windows (dialogs, menus, sheets) inherit LocalDensity, so they scale too.
 */
@Composable
internal fun DesignScale(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val scale = AppTheme.displayScale
    val density = remember(base, scale) { Density(base.density * scale, base.fontScale) }
    CompositionLocalProvider(LocalDensity provides density, content = content)
}

/** Screen width in app dp, i.e. with the display-size scale taken into account. */
@Composable
internal fun appScreenWidthDp(): Float = LocalConfiguration.current.screenWidthDp / AppTheme.displayScale

object AppTheme {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

    var mode by mutableStateOf(SYSTEM)
        private set

    /** Display size is shown as a percentage and changes in [DISPLAY_STEP] steps. */
    const val DISPLAY_MIN = 80
    const val DISPLAY_MAX = 130
    const val DISPLAY_STEP = 5

    var accent by mutableStateOf(Accents.Default)
        private set
    var displayScale by mutableStateOf(1f)
        private set

    fun load(ctx: Context) {
        val s = Store(ctx)
        mode = s.themeMode
        accent = Color(s.accentColor)
        Palette.setAccent(accent)
        displayScale = s.displayScale.coerceIn(DISPLAY_MIN / 100f, DISPLAY_MAX / 100f)
    }

    fun setAccent(ctx: Context, value: Color) {
        Store(ctx).accentColor = value.toArgb()
        accent = value
        Palette.setAccent(value)
    }

    fun setDisplayScale(ctx: Context, value: Float) {
        Store(ctx).displayScale = value
        displayScale = value
    }

    fun set(ctx: Context, value: String) {
        Store(ctx).themeMode = value
        mode = value
    }

    fun isDark(systemDark: Boolean): Boolean = when (mode) { LIGHT -> false; DARK -> true; else -> systemDark }
}

private fun Context.systemDark() =
    (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

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
    // Same as Proton's enableEdgeToEdgeVpn(): no forced scrim behind the 3-button navigation bar.
    if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(C.Bg.toArgb()))
}

internal fun Context.appVersionName(): String? =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

@Composable
internal fun ReportScrolled(scrolled: Boolean, onScrolledChange: (Boolean) -> Unit) {
    LaunchedEffect(scrolled) { onScrolledChange(scrolled) }
    DisposableEffect(Unit) { onDispose { onScrolledChange(false) } }
}
