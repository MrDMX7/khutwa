package com.dmx.khutwa.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Khutwa mark: a walking figure on a wine tile.
 *
 * Drawn rather than shipped as a bitmap so it stays sharp at every size, and
 * kept to five strokes plus a head — the version with sneakers and a natural
 * gait was clearer at 200px but lost its silhouette in the navigation bar,
 * which is where this is seen a hundred times a day. Geometry is authored on a
 * 100×100 grid and scaled.
 */
private const val GRID = 100f

private fun DrawScope.walker(scale: Float, ink: Color, strokeWidth: Float = 8f) {
    fun p(x: Float, y: Float) = Offset(x * scale, y * scale)
    val stroke = Stroke(
        width = strokeWidth * scale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    drawCircle(ink, radius = 7f * scale, center = p(54f, 24f))
    fun line(vararg pts: Pair<Float, Float>) {
        val path = Path().apply {
            moveTo(pts[0].first * scale, pts[0].second * scale)
            for (i in 1 until pts.size) lineTo(pts[i].first * scale, pts[i].second * scale)
        }
        drawPath(path, ink, style = stroke)
    }
    line(50f to 36f, 46f to 58f)                 // torso
    line(46f to 58f, 60f to 74f, 58f to 80f)     // front leg
    line(46f to 58f, 36f to 70f, 28f to 76f)     // back leg
    line(50f to 42f, 64f to 50f)                 // front arm
    line(50f to 42f, 38f to 52f)                 // back arm
}

/**
 * The mark on its rounded wine tile — the app icon, and the quiet anchor in
 * every screen header.
 */
@Composable
fun KhutwaMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val scale = this.size.minDimension / GRID
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF5E2138), Color(0xFF9C3352)),
                start = Offset(0f, this.size.height),
                end = Offset(this.size.width, 0f),
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f * scale),
            size = Size(this.size.width, this.size.height),
        )
        walker(scale, Color(0xFFFBF8F7))
    }
}

/** The figure alone, in one colour — for empty states and inline use. */
@Composable
fun KhutwaGlyph(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        walker(this.size.minDimension / GRID, tint)
    }
}
