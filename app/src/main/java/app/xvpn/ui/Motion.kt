package app.xvpn.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Shared timings / easings so every transition in the app has the same feel. */
internal object Motion {
    /** Material "emphasized decelerate": quick start, soft landing. Used for things entering the screen. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val Fast = 150
    const val Medium = 300
    const val Slow = 400
}

/**
 * Drop-in replacement for `clickable` / `combinedClickable` that adds press feedback: the element shrinks a touch while
 * the finger is down and springs back with a small overshoot on release. The ripple is kept.
 *
 * Put it FIRST among the decorating modifiers (right after the size modifiers) and the `background` / `border` /
 * `padding` after it, so the whole visual scales together. Pass [shape] to clip the ripple to rounded corners.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceClick(
    pressedScale: Float = 0.97f,
    shape: Shape? = null,
    enabled: Boolean = true,
    ripple: Boolean = true,
    haptic: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = if (pressed) tween(90, easing = LinearOutSlowInEasing)
        else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .combinedClickable(
            interactionSource = source,
            indication = if (ripple) LocalIndication.current else null,
            enabled = enabled,
            onLongClick = onLongClick,
            onClick = {
                if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        )
}

/** Fades in the first time it is composed (set [distance] > 0 for a small rise). [delayMs] lets a column of blocks arrive one after another. */
fun Modifier.enterRise(delayMs: Int = 0, distance: Dp = 0.dp, durationMs: Int = Motion.Medium): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(1f, tween(durationMs, easing = Motion.Emphasized))
    }
    val px = with(LocalDensity.current) { distance.toPx() }
    this.graphicsLayer { alpha = progress.value; translationY = (1f - progress.value) * px }
}

/** Fades in the first time it is composed. */
fun Modifier.enterFade(delayMs: Int = 0, durationMs: Int = Motion.Medium): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(1f, tween(durationMs))
    }
    this.graphicsLayer { alpha = progress.value }
}

/** Springs in from slightly smaller (dialogs, icons that appear). */
fun Modifier.popIn(from: Float = 0.88f): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f)) }
    this.graphicsLayer {
        val s = from + (1f - from) * progress.value
        scaleX = s; scaleY = s
        alpha = (progress.value * 2.5f).coerceIn(0f, 1f)
    }
}

/** A quick horizontal wiggle every time [trigger] changes (used to say "that input is invalid"). */
fun Modifier.shake(trigger: Int): Modifier = composed {
    val x = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) for (t in floatArrayOf(-9f, 8f, -6f, 4f, -2f, 0f)) x.animateTo(t, tween(50))
    }
    this.graphicsLayer { translationX = x.value * density }
}

/** Cross-fades whenever [target] changes (text, small blocks). */
@Composable
fun <T> FadeSwap(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
        label = "fadeSwap",
    ) { content(it) }
}
