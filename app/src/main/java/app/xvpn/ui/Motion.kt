@file:OptIn(ExperimentalFoundationApi::class)

package app.xvpn.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize

/** Durations (ms), easings and press feedback. Every animation in the app picks from here. */
object Motion {
    const val Fast = 120        // exits, fades out
    const val Short = 200       // color and small state changes
    const val Medium = 300      // layout, screen and sheet transitions
    const val Long = 450        // hero moments (speed test, count-up)
    const val Overlap = 90      // delay before an entering screen fades in, so it doesn't clash with the leaving one
    const val Stagger = 70      // delay between pieces that arrive one after another

    val Standard = FastOutSlowInEasing
    val Enter = LinearOutSlowInEasing
    val Exit = FastOutLinearInEasing
}

/** How far a pressed element shrinks. Icons shrink most, large surfaces least. */
object Press {
    // Proton VPN gives pressed feedback with the standard Material ripple only; it never scales pressed elements.
    // Kept as tokens (at 1f) so every call site stays valid; raise a value to bring the shrink effect back.
    const val Icon = 1f
    const val Control = 1f
    const val Card = 1f
}

@Composable
fun AppIndication(content: @Composable () -> Unit) {
    val indication = remember { ripple() }   // default colour = LocalContentColor (white on dark, near-black on light)
    CompositionLocalProvider(LocalIndication provides indication, content = content)
}

@Composable
fun rememberTick(strong: Boolean = false): () -> Unit {
    // Proton VPN adds no custom haptics to taps, selections or switches (only the custom-DNS list reorders with a tick),
    // so the tick is a no-op. Re-enable by restoring LocalHapticFeedback here.
    return remember(strong) { {} }
}

fun Modifier.pressable(
    shape: Shape = RectangleShape,
    pressedScale: Float = Press.Control,
    enabled: Boolean = true,
    ripple: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .combinedClickable(
            interactionSource = source,
            indication = if (ripple) LocalIndication.current else null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

/**
 * Screen / page / tab changes. Proton VPN uses Navigation Compose's default for every top-level and settings
 * destination (BaseNav.NavigationTransition.DefaultFade): a plain 700 ms cross-fade, no sliding.
 * Signature kept so the call sites in MainActivity and MenuScreen are unchanged; [forward] and [distance] are ignored.
 */
@Suppress("UNUSED_PARAMETER")
fun axisTransition(forward: Boolean, distance: Float = 0f): ContentTransform = ContentTransform(
    fadeIn(tween(700)), fadeOut(tween(700)), 0f, SizeTransform(clip = false),
)

/**
 * Opening / closing the log page. Proton's log is its own Activity, so it uses the platform activity transition (a short slide
 * from the end edge with a fade), not the 700 ms cross-fade of the in-app settings pages.
 */
fun activityTransition(opening: Boolean): ContentTransform {
    val spec = tween<androidx.compose.ui.unit.IntOffset>(Motion.Medium, easing = Motion.Standard)
    return if (opening) ContentTransform(
        slideInHorizontally(spec) { it / 4 } + fadeIn(tween(Motion.Medium, easing = Motion.Enter)),
        slideOutHorizontally(spec) { -it / 8 } + fadeOut(tween(Motion.Medium)),
        1f, SizeTransform(clip = false),
    ) else ContentTransform(
        slideInHorizontally(spec) { -it / 8 } + fadeIn(tween(Motion.Medium, easing = Motion.Enter)),
        slideOutHorizontally(spec) { it / 4 } + fadeOut(tween(Motion.Medium)),
        0f, SizeTransform(clip = false),
    )
}

fun fadeTransition(): ContentTransform = ContentTransform(
    fadeIn(tween(Motion.Short, delayMillis = Motion.Overlap / 2, easing = Motion.Enter)),
    fadeOut(tween(Motion.Fast)),
    0f,
    SizeTransform(clip = false),
)

/**
 * Home status changes. Proton (rememberVpnStateAnimationProgress): a 500 ms fade only for Connecting -> Connected,
 * every other change snaps, and the layout never animates its size.
 */
fun statusTransition(slowFade: Boolean): ContentTransform = ContentTransform(
    if (slowFade) fadeIn(tween(500)) else EnterTransition.None,
    ExitTransition.None,
    0f,
    SizeTransform(clip = false) { _, _ -> snap() },
)

fun numberTransition(up: Boolean): ContentTransform {
    val dir = if (up) 1 else -1
    return ContentTransform(
        slideInVertically(tween(Motion.Short, easing = Motion.Standard)) { it * dir / 2 } + fadeIn(tween(Motion.Short)),
        slideOutVertically(tween(Motion.Short, easing = Motion.Standard)) { -it * dir / 2 } + fadeOut(tween(Motion.Fast)),
        0f,
        SizeTransform(clip = false),
    )
}
