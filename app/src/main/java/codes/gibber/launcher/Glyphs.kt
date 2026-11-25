package codes.gibber.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

/** The design's line icons, drawn on a 24-unit square like its SVGs. */
enum class Glyphs { Back, Forward, Down, Search, Gear, Play, Pause, Prev, Next, Mic, Bulb, Speaker, Check, Plus, Code, Swatch, Move, Merge, Remove, Trash }

@Composable
fun Glyph(g: Glyphs, size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawGlyph(g, color) }
}

fun DrawScope.drawGlyph(g: Glyphs, color: Color) {
    val u = size.minDimension / 24f
    val line = Stroke(width = 1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(vararg pts: Float, close: Boolean = false) = Path().apply {
        moveTo(pts[0] * u, pts[1] * u)
        var i = 2
        while (i < pts.size) { lineTo(pts[i] * u, pts[i + 1] * u); i += 2 }
        if (close) close()
    }
    fun stroke(path: Path) = drawPath(path, color, style = line)
    fun fill(path: Path) = drawPath(path, color, style = Fill)
    when (g) {
        Glyphs.Back -> stroke(p(15f, 6f, 9f, 12f, 15f, 18f))
        Glyphs.Forward -> stroke(p(9f, 6f, 15f, 12f, 9f, 18f))
        Glyphs.Down -> stroke(p(6f, 9f, 12f, 15f, 18f, 9f))
        Glyphs.Search -> {
            drawCircle(color, radius = 6.5f * u, center = Offset(11f * u, 11f * u), style = line)
            stroke(p(16f, 16f, 20f, 20f))
        }
        Glyphs.Gear -> {
            val c = Offset(12 * u, 12 * u)
            drawCircle(color, radius = 3 * u, center = c, style = line)
            drawCircle(color, radius = 7 * u, center = c, style = line)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                drawLine(
                    color,
                    Offset(c.x + 7 * u * cos(a).toFloat(), c.y + 7 * u * sin(a).toFloat()),
                    Offset(c.x + 10 * u * cos(a).toFloat(), c.y + 10 * u * sin(a).toFloat()),
                    strokeWidth = 1.7f * u,
                    cap = StrokeCap.Round,
                )
            }
        }
        Glyphs.Play -> fill(p(8f, 5f, 19f, 12f, 8f, 19f, close = true))
        Glyphs.Pause -> {
            drawRect(color, Offset(7 * u, 5 * u), Size(3.5f * u, 14 * u))
            drawRect(color, Offset(13.5f * u, 5 * u), Size(3.5f * u, 14 * u))
        }
        Glyphs.Prev -> {
            fill(p(18f, 6f, 9f, 12f, 18f, 18f, close = true))
            drawRect(color, Offset(6 * u, 6 * u), Size(2 * u, 12 * u))
        }
        Glyphs.Next -> {
            fill(p(6f, 6f, 15f, 12f, 6f, 18f, close = true))
            drawRect(color, Offset(16 * u, 6 * u), Size(2 * u, 12 * u))
        }
        Glyphs.Mic -> {
            drawRoundRect(color, Offset(9 * u, 3 * u), Size(6 * u, 11 * u), androidx.compose.ui.geometry.CornerRadius(3 * u), style = line)
            drawArc(color, 0f, 180f, false, Offset(5.5f * u, 7 * u), Size(13 * u, 11 * u), style = line)
            stroke(p(12f, 18f, 12f, 21f))
        }
        Glyphs.Bulb -> {
            stroke(p(9f, 18f, 15f, 18f))
            stroke(p(10f, 21f, 14f, 21f))
            val bulb = Path().apply {
                addArc(Rect(6 * u, 3 * u, 18 * u, 15 * u), 145f, 250f)
                lineTo(15 * u, 16 * u)
                lineTo(9 * u, 16 * u)
                close()
            }
            stroke(bulb)
        }
        Glyphs.Speaker -> {
            drawRoundRect(color, Offset(6 * u, 3 * u), Size(12 * u, 18 * u), androidx.compose.ui.geometry.CornerRadius(2 * u), style = line)
            drawCircle(color, radius = 3.5f * u, center = Offset(12 * u, 14 * u), style = line)
            drawCircle(color, radius = 1f * u, center = Offset(12 * u, 7 * u))
        }
        Glyphs.Check -> stroke(p(5f, 12.5f, 10f, 17f, 19f, 7f))
        Glyphs.Plus -> { stroke(p(12f, 5f, 12f, 19f)); stroke(p(5f, 12f, 19f, 12f)) }
        Glyphs.Code -> { stroke(p(9f, 7f, 4f, 12f, 9f, 17f)); stroke(p(15f, 7f, 20f, 12f, 15f, 17f)) }
        Glyphs.Swatch -> {
            drawCircle(color, radius = 3.2f * u, center = Offset(8 * u, 9 * u), style = line)
            drawCircle(color, radius = 3.2f * u, center = Offset(16 * u, 9 * u), style = line)
            drawCircle(color, radius = 3.2f * u, center = Offset(12 * u, 16 * u), style = line)
        }
        Glyphs.Move -> { stroke(p(4f, 12f, 20f, 12f)); stroke(p(14f, 6f, 20f, 12f, 14f, 18f)) }
        Glyphs.Merge -> {
            drawRect(color, Offset(4 * u, 4 * u), Size(7 * u, 7 * u), style = line)
            drawRect(color, Offset(13 * u, 13 * u), Size(7 * u, 7 * u), style = line)
            stroke(p(11f, 7.5f, 16.5f, 7.5f, 16.5f, 13f))
        }
        Glyphs.Remove -> stroke(p(5f, 12f, 19f, 12f))
        Glyphs.Trash -> {
            stroke(p(4f, 7f, 20f, 7f))
            stroke(p(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f))
            stroke(p(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f))
        }
    }
}
