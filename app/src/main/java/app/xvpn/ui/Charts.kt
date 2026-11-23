package app.xvpn.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

internal fun DrawScope.drawChartGuides() {
    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
    for (f in listOf(0f, 0.5f, 1f)) {
        val y = size.height - f * (size.height - 2.dp.toPx()) - 1.dp.toPx()
        drawLine(C.Border, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dash)
    }
}

internal fun smoothPath(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val p0 = points[i - 1]
        val p1 = points[i]
        val mx = (p0.x + p1.x) / 2
        cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
    }
}
