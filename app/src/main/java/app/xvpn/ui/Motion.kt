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
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

object Motion {
    const val Fast = 120
    const val Short = 200
    const val Medium = 300
    const val Long = 450
    const val Overlap = 90
    const val Stagger = 70

    val Standard = FastOutSlowInEasing
    val Enter = LinearOutSlowInEasing
    val Exit = FastOutLinearInEasing
}

object Press {
    const val Icon = 1f
    const val Control = 1f
    const val Card = 1f
}

@Composable
fun AppIndication(content: @Composable () -> Unit) {
    val indication = remember { ripple() }
    CompositionLocalProvider(LocalIndication provides indication, content = content)
}

@Composable
fun rememberTick(strong: Boolean = false): () -> Unit {
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

fun axisTransition(): ContentTransform = ContentTransform(
    fadeIn(tween(700)), fadeOut(tween(700)), 0f, SizeTransform(clip = false),
)

/** Proton's addToGraphWithSlideAnim: a vertical slide, 400ms, for panels such as the connection details. */
fun panelTransition(entering: Boolean): ContentTransform = if (entering) {
    ContentTransform(slideInVertically(tween(400)) { it }, ExitTransition.None, 1f, SizeTransform(clip = false))
} else {
    ContentTransform(EnterTransition.None, slideOutVertically(tween(400)) { it }, -1f, SizeTransform(clip = false))
}

fun fadeTransition(): ContentTransform = ContentTransform(
    fadeIn(tween(Motion.Short, delayMillis = Motion.Overlap / 2, easing = Motion.Enter)),
    fadeOut(tween(Motion.Fast)),
    0f,
    SizeTransform(clip = false),
)

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
