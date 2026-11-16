@file:OptIn(ExperimentalFoundationApi::class)

package app.xvpn.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
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

object Motion {
    const val Short = 150
    const val Medium = 300
    val Standard = FastOutSlowInEasing
}

@Composable
fun AppIndication(content: @Composable () -> Unit) {
    val indication = remember { ripple(color = Color.White) }
    CompositionLocalProvider(LocalIndication provides indication, content = content)
}

@Composable
fun rememberTick(strong: Boolean = false): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic, strong) {
        { haptic.performHapticFeedback(if (strong) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove) }
    }
}

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

fun axisTransition(forward: Boolean, distance: Float = 0.12f): ContentTransform {
    val dir = if (forward) 1 else -1
    val enter = slideInHorizontally(tween(Motion.Medium, easing = Motion.Standard)) { (it * distance).toInt() * dir } +
        fadeIn(tween(Motion.Medium - 90, delayMillis = 90, easing = LinearOutSlowInEasing))
    val exit = slideOutHorizontally(tween(Motion.Medium, easing = Motion.Standard)) { -(it * distance).toInt() * dir } +
        fadeOut(tween(90, easing = FastOutLinearInEasing))
    return ContentTransform(enter, exit, 0f, SizeTransform(clip = false))
}

fun fadeTransition(): ContentTransform = ContentTransform(
    fadeIn(tween(Motion.Short + 50, delayMillis = 60, easing = LinearOutSlowInEasing)),
    fadeOut(tween(Motion.Short - 40)),
    0f,
    SizeTransform(clip = false),
)

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

fun numberTransition(up: Boolean): ContentTransform {
    val dir = if (up) 1 else -1
    return ContentTransform(
        slideInVertically(tween(Motion.Short + 50, easing = Motion.Standard)) { it * dir / 2 } + fadeIn(tween(Motion.Short + 50)),
        slideOutVertically(tween(Motion.Short + 50, easing = Motion.Standard)) { -it * dir / 2 } + fadeOut(tween(Motion.Short)),
        0f,
        SizeTransform(clip = false),
    )
}
