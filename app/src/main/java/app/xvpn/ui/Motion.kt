package app.xvpn.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Timings and easings shared by every transition, so the whole app moves with one personality. */
object Motion {
    /** Material "emphasized decelerate": things arrive fast and settle softly. */
    val Enter = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Material "emphasized accelerate": things leave quickly and get out of the way. */
    val Exit = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val Short = 150
    const val Medium = 280
    const val Long = 380

    /** Page slides move only a fraction of the width: the eye reads direction without a full-screen wipe. */
    const val SlideFraction = 6
}

/**
 * The app has no MaterialTheme, so `clickable` fell back to Compose's flat debug highlight (a grey flash, no ripple).
 * Providing a real ripple here fixes press feedback for every existing `clickable` in one place.
 */
@Composable
internal fun WithRipple(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIndication provides ripple(color = Color.White), content = content)
}

/**
 * Press feedback in one modifier: the element springs down while the finger is on it and bounces back on release,
 * a ripple is drawn clipped to [shape], and an optional haptic tick confirms the tap.
 *
 * Put it BEFORE `.background(...)` / `.border(...)` so the whole element scales together with its fill.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.bounceClick(
    shape: Shape = RectangleShape,
    scaleTo: Float = 0.96f,
    haptic: HapticFeedbackType? = null,
    ripple: Boolean = true,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleTo else 1f,
        // underdamped on the way back = the little "pop" on release
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    val feedback = LocalHapticFeedback.current
    val indication = if (ripple) LocalIndication.current else null
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(shape)
        .combinedClickable(
            interactionSource = source, indication = indication, enabled = enabled,
            onLongClick = onLongClick?.let { lc -> { feedback.performHapticFeedback(HapticFeedbackType.LongPress); lc() } },
            onClick = { haptic?.let { feedback.performHapticFeedback(it) }; onClick() },
        )
}

/**
 * "Open / close" transition for screens that go one level deeper (Settings sub-pages, Statistics).
 * Values read from Proton VPN's resources (androidx fragment open/close animators): the incoming screen scales
 * 0.85 -> 1 over 300 ms on the fast-out-extra-slow-in curve with a 50 ms fade, the outgoing one just fades.
 * Going back is the mirror image: the leaving screen shrinks to 0.85 while fading out.
 */
internal fun openCloseTransition(forward: Boolean): ContentTransform =
    if (forward) {
        (fadeIn(tween(Motion.Medium, delayMillis = 50)) + scaleIn(tween(300, easing = Motion.Enter), initialScale = 0.85f))
            .togetherWith(fadeOut(tween(100)))
    } else {
        fadeIn(tween(Motion.Medium, delayMillis = 50))
            .togetherWith(fadeOut(tween(200)) + scaleOut(tween(300, easing = Motion.Enter), targetScale = 0.85f))
    }
