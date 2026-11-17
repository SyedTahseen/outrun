package app.xvpn.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Backdrop used by Spark's frosted glass. Two layers are recorded behind the glass:
 * the ambient colour glow (whole screen) and, on Home, the world map. Each glass
 * surface draws a blurred copy of what is behind it, cropped to its own bounds.
 */
@Stable
class Backdrop(val ambient: GraphicsLayer, val map: GraphicsLayer) {
    var ambientOrigin by mutableStateOf(Offset.Zero)
    var mapOrigin by mutableStateOf(Offset.Zero)
    var ambientActive by mutableStateOf(false)
    var mapActive by mutableStateOf(false)
}

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun BackdropHost(content: @Composable () -> Unit) {
    val ambient = rememberGraphicsLayer()
    val map = rememberGraphicsLayer()
    val backdrop = remember(ambient, map) { Backdrop(ambient, map) }
    CompositionLocalProvider(LocalBackdrop provides backdrop, content = content)
}

/** Records this node's content into the shared backdrop (Spark only). */
@Composable
fun Modifier.backdropSource(isMap: Boolean = false): Modifier {
    val b = LocalBackdrop.current
    if (b == null || !Palette.spark) return this
    DisposableEffect(b, isMap) {
        if (isMap) b.mapActive = true else b.ambientActive = true
        onDispose { if (isMap) b.mapActive = false else b.ambientActive = false }
    }
    val layer = if (isMap) b.map else b.ambient
    return this
        .onGloballyPositioned { c ->
            val p = c.positionInRoot()
            if (isMap) b.mapOrigin = p else b.ambientOrigin = p
        }
        .drawWithContent {
            layer.record(this, layoutDirection, IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                this@drawWithContent.drawContent()
            }
            drawLayer(layer)
        }
}

/** Flat Spark page background. Recorded as the backdrop so glass has something to blur; no gradients. */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().backdropSource().background(C.Bg))
}

/**
 * Frosted glass: blurred backdrop (Android 12+), translucent white gradient fill and a
 * hairline gradient edge. Falls back to the fill + edge alone below Android 12.
 * Apply after the shape clip.
 */
@Composable
fun Modifier.glass(shape: Shape, strong: Boolean = false, fullBorder: Boolean = true): Modifier {
    val backdrop = LocalBackdrop.current
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return this
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawBehind {
            val w = size.width.roundToInt()
            val h = size.height.roundToInt()
            val shown = canBlur && backdrop != null && backdrop.ambientActive && w > 0 && h > 0
            if (shown && backdrop != null) {
                val r = Glass.Blur.toPx()
                layer.renderEffect = BlurEffect(r, r, TileMode.Clamp)
                layer.record(this, layoutDirection, IntSize(w, h)) {
                    val a = backdrop.ambientOrigin - origin
                    translate(a.x, a.y) { drawLayer(backdrop.ambient) }
                    if (backdrop.mapActive) {
                        val m = backdrop.mapOrigin - origin
                        translate(m.x, m.y) { drawLayer(backdrop.map) }
                    }
                }
                drawLayer(layer)
            }
            if (!shown) drawRect(C.Card.copy(alpha = 0.92f))
            if (strong) drawRect(C.Bg.copy(alpha = if (shown) 0.4f else 0.6f))
            drawRect(Brush.verticalGradient(listOf(C.GlassFillTop, C.GlassFillBottom)))
            if (!fullBorder) {
                drawRect(C.GlassEdgeTop, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            }
        }
        .then(
            if (fullBorder) Modifier.border(
                1.dp,
                Brush.verticalGradient(listOf(C.GlassEdgeTop, C.GlassEdgeBottom)),
                shape,
            ) else Modifier,
        )
}

/** Fades the bottom of a node to transparent (used so the map melts into the glass cards). */
fun Modifier.bottomFade(from: Float = 0.52f, to: Float = 0.74f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Black, from to Color.Black, to to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

/** Card-like surface: glass in Spark, flat `Card` fill elsewhere. */
@Composable
fun Modifier.cardSurface(shape: Shape): Modifier =
    if (Palette.spark) glass(shape) else background(C.Card)

/** Dialog surface (dialogs live in their own window, so no live blur): opaque card + glass edge. */
fun Modifier.dialogSurface(shape: Shape): Modifier =
    if (Palette.spark) background(C.Card).border(
        1.dp, Brush.verticalGradient(listOf(C.GlassEdgeTop, C.GlassEdgeBottom)), shape,
    ) else background(C.Card)
