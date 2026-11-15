package app.xvpn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Thin overlay scrollbar that fades in while you scroll and fades out ~1.2 s after you stop.
 * Same behaviour as v2rayNG's Scrollbar.kt, minus the Material theme / lifecycle dependencies.
 */
private const val FADE_OUT_DELAY_MS = 1200L
private const val FADE_ANIM_MS = 300

@Composable
private fun rememberScrollbarAlpha(key: Any, position: () -> Any): State<Float> {
    val alpha = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        var first = true
        snapshotFlow(position).collectLatest {
            // don't flash the bar just because the screen opened
            if (first) { first = false; return@collectLatest }
            alpha.animateTo(1f, tween(80))
            delay(FADE_OUT_DELAY_MS)
            alpha.animateTo(0f, tween(FADE_ANIM_MS))
        }
    }
    return alpha.asState()
}

private fun DrawScope.drawThumb(offset: Float, length: Float, alpha: Float) {
    val thickness = 4.dp.toPx()
    val padding = 2.dp.toPx()
    val x = if (layoutDirection == LayoutDirection.Rtl) padding else size.width - thickness - padding
    drawRoundRect(
        color = C.Accent.copy(alpha = 0.7f * alpha),
        topLeft = Offset(x, offset),
        size = Size(thickness, length),
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
}

private fun thumbFor(viewport: Float, content: Float, scrolled: Float, minLength: Float): Pair<Float, Float>? {
    if (viewport <= 0f || content <= viewport) return null
    val length = (viewport * viewport / content).coerceAtLeast(minLength).coerceAtMost(viewport * 0.5f)
    val offset = (scrolled / (content - viewport)).coerceIn(0f, 1f) * (viewport - length)
    return offset to length
}

fun Modifier.verticalScrollbar(state: ScrollState): Modifier = composed {
    val alpha = rememberScrollbarAlpha(state) { state.value }
    drawWithContent {
        drawContent()
        if (alpha.value <= 0f) return@drawWithContent
        val viewport = size.height
        val (offset, length) = thumbFor(viewport, viewport + state.maxValue, state.value.toFloat(), 24.dp.toPx())
            ?: return@drawWithContent
        drawThumb(offset, length, alpha.value)
    }
}

fun Modifier.verticalScrollbar(state: LazyListState): Modifier = composed {
    val alpha = rememberScrollbarAlpha(state) { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
    drawWithContent {
        drawContent()
        val layout = state.layoutInfo
        val visible = layout.visibleItemsInfo
        if (alpha.value <= 0f || visible.isEmpty()) return@drawWithContent
        val viewport = layout.viewportSize.height.toFloat()
        val averageItem = visible.sumOf { it.size } / visible.size.toFloat()
        val scrolled = visible.first().index * averageItem + layout.viewportStartOffset - visible.first().offset
        val content = averageItem * layout.totalItemsCount
        val (offset, length) = thumbFor(viewport, content, scrolled, 24.dp.toPx()) ?: return@drawWithContent
        drawThumb(offset, length, alpha.value)
    }
}
