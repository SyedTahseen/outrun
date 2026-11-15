@file:OptIn(ExperimentalFoundationApi::class)

package app.xvpn.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Shared motion language. Every duration / easing in the app comes from here so screens, lists and buttons
 * all move the same way (this is what makes v2rayNG feel "native": Material standard easing, short fades,
 * ripples on everything tappable, springs for small feedback).
 */
object Motion {
    const val Short = 150
    const val Medium = 300
    const val Long = 420
    val Standard = FastOutSlowInEasing
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/** Gives every `clickable` in the app a Material ripple (without a MaterialTheme the app had no touch feedback at all). */
@Composable
fun AppIndication(content: @Composable () -> Unit) {
    val indication = remember { ripple(color = Color.White) }
    CompositionLocalProvider(LocalIndication provides indication, content = content)
}

/** Light "tick" haptic. Honours the user's touch-feedback system setting. */
@Composable
fun rememberTick(strong: Boolean = false): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic, strong) {
        { haptic.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove) }
    }
}

/**
 * Tappable surface with ripple (clipped to [shape]) and a springy press-down scale.
 * Put it BEFORE `.background(...)` / `.border(...)` so the whole button scales and the ripple draws over the fill:
 * `Modifier.pressable(shape) { ... }.background(color)`.
 */
fun Modifier.pressable(
    shape: Shape = RectangleShape,
    pressedScale: Float = 0.97f,
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

/** Fade + short slide that fades the old screen out first, then brings the new one in (Material "shared axis X"). */
fun axisTransition(forward: Boolean, distance: Float = 0.12f): ContentTransform {
    val dir = if (forward) 1 else -1
    val enter = slideInHorizontally(tween(Motion.Medium, easing = Motion.Standard)) { (it * distance).toInt() * dir } +
        fadeIn(tween(Motion.Medium - 90, delayMillis = 90, easing = LinearOutSlowInEasing))
    val exit = slideOutHorizontally(tween(Motion.Medium, easing = Motion.Standard)) { -(it * distance).toInt() * dir } +
        fadeOut(tween(90, easing = FastOutLinearInEasing))
    return ContentTransform(enter, exit, 0f, SizeTransform(clip = false))
}

/** Plain cross-fade with a smooth size change. For text / label swaps. */
fun fadeTransition(): ContentTransform = ContentTransform(
    fadeIn(tween(Motion.Short + 50, delayMillis = 60, easing = LinearOutSlowInEasing)),
    fadeOut(tween(Motion.Short - 40)),
    0f,
    SizeTransform(clip = false),
)

/** Connection-state swap on Home: new content rises in slightly while the old one fades away. */
fun statusTransition(): ContentTransform {
    val enter = fadeIn(tween(240, delayMillis = 90, easing = LinearOutSlowInEasing)) +
        slideInVertically(tween(Motion.Medium, delayMillis = 60, easing = Motion.Standard)) { it / 10 } +
        scaleIn(tween(Motion.Medium, delayMillis = 60, easing = Motion.Standard), initialScale = 0.96f)
    val exit = fadeOut(tween(90))
    return ContentTransform(
        enter, exit, 0f,
        SizeTransform(clip = false) { _, _ -> tween<IntSize>(Motion.Medium, easing = Motion.Standard) },
    )
}

/** Counter / badge change: the new number slides in from below when it grows, from above when it shrinks. */
fun numberTransition(up: Boolean): ContentTransform {
    val dir = if (up) 1 else -1
    return ContentTransform(
        slideInVertically(tween(Motion.Short + 50, easing = Motion.Standard)) { it * dir / 2 } + fadeIn(tween(Motion.Short + 50)),
        slideOutVertically(tween(Motion.Short + 50, easing = Motion.Standard)) { -it * dir / 2 } + fadeOut(tween(Motion.Short)),
        0f,
        SizeTransform(clip = false),
    )
}

/** Staggered entrance: content fades in and floats up a few dp, [index] * 60 ms after the screen opens. */
@Composable
fun Reveal(index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        progress.animateTo(1f, tween(Motion.Long, easing = Motion.Standard))
    }
    Column(
        Modifier.fillMaxWidth().graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 20.dp.toPx()
        },
    ) { content() }
}
