package app.xvpn.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lays the UI out as if the shortest screen side were 360dp, so the design scales with the device. */
@Composable
internal fun DesignScale(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val cfg = LocalConfiguration.current
    val scale = minOf(cfg.screenWidthDp, cfg.screenHeightDp) / 360f
    CompositionLocalProvider(
        LocalDensity provides Density(d.density * scale, fontScale = 1f),
        content = content,
    )
}

/** Transparent system bars drawn over the app background; shared by every activity. */
internal fun ComponentActivity.setUpEdgeToEdge(navigationBarColor: Int = android.graphics.Color.TRANSPARENT) {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(navigationBarColor),
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

/** The 44dp title row that sits under the status bar on every screen. */
@Composable
internal fun HeaderRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
internal fun HeaderIcon(icon: ImageVector, desc: String, tint: Color = C.Text, onClick: () -> Unit) {
    val color by animateColorAsState(tint, tween(Motion.Short + 50), label = "headerIconTint")
    Box(Modifier.size(44.dp).pressable(RoundedCornerShape(22.dp), 0.86f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = color, modifier = Modifier.size(26.dp))
    }
}

/** Back arrow with an optional title: the standard header of every settings sub-page. */
@Composable
internal fun BackBar(title: String? = null, onBack: () -> Unit) {
    Spacer(Modifier.height(20.dp))
    HeaderRow {
        HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
        if (title != null) {
            Spacer(Modifier.width(6.dp))
            Text(title, color = C.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Accent-colored heading above a group of rows; [first] uses the tighter spacing for the top of a page. */
@Composable
internal fun Section(label: String, first: Boolean = false) {
    Text(
        label, color = C.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = if (first) 12.dp else 28.dp, bottom = 8.dp),
    )
}
