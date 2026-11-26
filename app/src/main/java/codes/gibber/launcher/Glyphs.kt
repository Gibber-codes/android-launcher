package codes.gibber.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
enum class Glyphs { Back, Forward, Down, Search, Gear, Play, Pause, Prev, Next, Mic, Bulb, Xtreme, Earbuds, Volume, Bluetooth, Check, Plus, Code, Swatch, Move, Merge, Remove, Trash }

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
        // The JBL Xtreme 5 face on: a pill body, its two strap posts, the - > + strip, a row of grille, the foot.
        Glyphs.Xtreme -> {
            drawRoundRect(color, Offset(2 * u, 7.5f * u), Size(20 * u, 10.5f * u), CornerRadius(5.25f * u), style = line)
            stroke(p(5f, 8f, 5f, 5.2f, 7.6f, 5.2f, 7.6f, 7.5f))
            stroke(p(19f, 8f, 19f, 5.2f, 16.4f, 5.2f, 16.4f, 7.5f))
            val fine = Stroke(width = 1.1f * u, cap = StrokeCap.Round)
            drawPath(p(8.8f, 10.5f, 10.2f, 10.5f), color, style = fine)
            fill(p(11.3f, 9.6f, 12.9f, 10.5f, 11.3f, 11.4f, close = true))
            drawPath(p(14f, 10.5f, 15.6f, 10.5f), color, style = fine)
            drawPath(p(14.8f, 9.7f, 14.8f, 11.3f), color, style = fine)
            for (x in listOf(6.5f, 9.25f, 12f, 14.75f, 17.5f)) drawCircle(color, radius = 0.75f * u, center = Offset(x * u, 14.4f * u))
            stroke(p(8.5f, 18f, 9f, 20.2f, 15f, 20.2f, 15.5f, 18f))
        }
        // One JBL Endurance Peak 3 from the side: a chunky bud with its ear tip, the hook arching over and down behind.
        Glyphs.Earbuds -> {
            drawRoundRect(color, Offset(4.5f * u, 12 * u), Size(10 * u, 8.5f * u), CornerRadius(4.25f * u), style = line)
            drawRoundRect(color, Offset(2 * u, 14.3f * u), Size(2.5f * u, 4 * u), CornerRadius(1.2f * u), style = line)
            drawCircle(color, radius = 0.9f * u, center = Offset(10 * u, 16.25f * u))
            stroke(Path().apply {
                moveTo(10.5f * u, 12.1f * u)
                cubicTo(9.5f * u, 5.5f * u, 14 * u, 2.5f * u, 18 * u, 4.5f * u)
                cubicTo(22 * u, 6.5f * u, 21.5f * u, 12.5f * u, 17.5f * u, 17 * u)
            })
        }
        // A speaker cone and two waves, for the volume.
        Glyphs.Volume -> {
            stroke(p(4f, 9.5f, 8f, 9.5f, 12.5f, 5.5f, 12.5f, 18.5f, 8f, 14.5f, 4f, 14.5f, close = true))
            drawArc(color, -45f, 90f, false, Offset(8.5f * u, 8 * u), Size(8 * u, 8 * u), style = line)
            drawArc(color, -45f, 90f, false, Offset(5 * u, 4.5f * u), Size(15 * u, 15 * u), style = line)
        }
        // The Bluetooth rune: a spine with two arrowheads to its right, crossed at the left.
        Glyphs.Bluetooth -> stroke(p(7f, 7.5f, 17f, 16.5f, 12f, 21f, 12f, 3f, 17f, 7.5f, 7f, 16.5f))
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
