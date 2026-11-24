package codes.gibber.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** The approved design's colours, from the Gibber Codes design system. */
object Look {
    val Page = Color(0xFF101012)
    val Home = Color(0xFF141317)
    val Cream = Color(0xFFF7F1EA)
    val Sage = Color(0xFFBABF95)
    val Lilac = Color(0xFFC39CDC)
    val Muted = Color(0xFFA39EA6)
    val Dim = Color(0xFF6D6872)
    val Line = Color(0xFF2C2B31)
    val Folder = Color(0xF01A191D)

    /**
     * An app's one-colour icon in cream, like the design's line icons: the monochrome layer Android 13
     * apps give for themed icons. Null when the app has none (its normal icon is used then).
     */
    fun monoIcon(context: Context, app: AppEntry, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < 33) return null
        return try {
            val d = context.packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))
            val mono = (d as? AdaptiveIconDrawable)?.monochrome?.mutate() ?: return null
            mono.colorFilter = PorterDuffColorFilter(Cream.toArgb(), PorterDuff.Mode.SRC_IN)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            // The glyph sits in the middle of a much bigger layer; draw the layer twice the size so the glyph fills the icon.
            mono.setBounds(-size / 2, -size / 2, size + size / 2, size + size / 2)
            mono.draw(Canvas(bmp))
            bmp
        } catch (e: Exception) {
            null
        }
    }
}

/** The round button at the top right of the Dev page, with the design's gear. */
@Composable
fun GearButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, Look.Line, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Canvas(Modifier.size(20.dp)) {
            val u = size.width / 24f
            val stroke = Stroke(width = 1.6f * u, cap = StrokeCap.Round)
            val c = Offset(12 * u, 12 * u)
            drawCircle(Look.Cream, radius = 3 * u, center = c, style = stroke)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val inner = if (i % 2 == 0) 7f else 6.5f
                val outer = if (i % 2 == 0) 10f else 9.5f
                drawLine(
                    Look.Cream,
                    Offset(c.x + inner * u * cos(a).toFloat(), c.y + inner * u * sin(a).toFloat()),
                    Offset(c.x + outer * u * cos(a).toFloat(), c.y + outer * u * sin(a).toFloat()),
                    strokeWidth = 1.6f * u,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** The design's folder with a note in it, for a group of apps. */
@Composable
fun FolderGlyph(size: Dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = Stroke(width = 1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path().apply {
            moveTo(3 * u, 6 * u); lineTo(9 * u, 6 * u); lineTo(11 * u, 8 * u); lineTo(21 * u, 8 * u)
            lineTo(21 * u, 19 * u); lineTo(3 * u, 19 * u); close()
        }
        drawPath(p, Look.Cream, style = stroke)
        val note = Path().apply { moveTo(10 * u, 16 * u); lineTo(10 * u, 11 * u); lineTo(15 * u, 10 * u); lineTo(15 * u, 15 * u) }
        drawPath(note, Look.Cream, style = stroke)
        drawCircle(Look.Cream, radius = 1.5f * u, center = Offset(8.5f * u, 16 * u), style = stroke)
        drawCircle(Look.Cream, radius = 1.5f * u, center = Offset(13.5f * u, 15 * u), style = stroke)
    }
}
