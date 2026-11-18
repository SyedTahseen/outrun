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

@Composable
internal fun DesignScale(content: @Composable () -> Unit) = content()

object AppTheme {
    const val SYSTEM = "system"
    const val LIGHT = "light"
    const val DARK = "dark"

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

internal fun Context.appVersionName(): String? =
    runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

@Composable
internal fun ReportScrolled(scrolled: Boolean, onScrolledChange: (Boolean) -> Unit) {
    LaunchedEffect(scrolled) { onScrolledChange(scrolled) }
    DisposableEffect(Unit) { onDispose { onScrolledChange(false) } }
}
