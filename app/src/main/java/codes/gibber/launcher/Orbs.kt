package codes.gibber.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The design's background: the night, three gradient circles, and frosted glass over them. The
 * design is drawn on a 390 px wide phone; `s` turns its pixels into this screen's.
 *
 * The circles never stop. Over about 44 seconds the bottom and top ones turn until their pink points
 * face while the top one drifts down to overlap the bottom one, then they go back, and slower sways
 * of their own keep them moving at the turns. The big one comes down from above the screen to fill
 * about its top third, always with its bright side above the screen.
 */
@Composable
fun OrbBackground(modifier: Modifier = Modifier) {
    val seconds by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1_000_000_000f }
    }
    Canvas(modifier.fillMaxSize()) {
        // A gradient of no size throws, so draw nothing until the screen has one.
        if (size.width < 2f || size.height < 2f) return@Canvas
        val t = seconds
        val s = size.width / 390f
        val w = size.width
        val h = size.height
        fun wave(period: Float, phase: Float = 0f) = sin(2f * PI.toFloat() * t / period + phase)
        // 0 when apart, 1 when they meet: a smooth swing, never a hold.
        val meet = (1f - cos(2f * PI.toFloat() * t / 44f)) / 2f

        drawRect(Dark.Night)

        // The bottom circle.
        drawOrb(
            center = Offset(w / 2f + 6f * s * wave(29f), h - 172f * s),
            d = 280f * s,
            angle = 90f - 140f * meet + 9f * wave(17f),
            s = s,
            glow = 0.78f + 0.22f * wave(10f),
            screen = false,
        )
        // The big one: hidden above the screen, then down until its edge is a third of the way down.
        val hidden = -240f * s
        val lowest = h / 3f - 220f * s
        drawOrb(
            center = Offset(w / 2f + 10f * s * wave(31f, 1f), hidden + (lowest - hidden) * meet),
            d = 440f * s,
            angle = 270f + 8f * wave(19f),
            s = s,
            glow = 0.78f + 0.22f * wave(10f, 2f),
            screen = true,
        )
        // The smaller top one, swaying side to side as it drifts.
        drawOrb(
            center = Offset(w / 2f + 14f * s * wave(22f), (140f + 330f * meet) * s),
            d = 196f * s,
            angle = 270f - 140f * meet + 9f * wave(13f, 1.3f),
            s = s,
            glow = 0.78f + 0.22f * wave(10f, 4f),
            screen = true,
        )

        // The glass: the night at 74% over the circles, a little darker, with a faint sheen.
        drawRect(Dark.Night.copy(alpha = 0.78f))
        drawRect(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.03f),
                0.38f to Color.Transparent,
                0.72f to Color.Transparent,
                1f to Color.White.copy(alpha = 0.01f),
                start = Offset.Zero,
                end = Offset(w, h),
            )
        )
    }
}

private val White = Color.White

/** Stops for a ring mask, kept in order and inside 0..1 however small the circle is. */
private fun ringStops(vararg stops: Pair<Float, Color>): Array<Pair<Float, Color>> {
    var last = 0f
    return stops.map { (f, c) ->
        val v = max(last, f.coerceIn(0f, 1f))
        last = v
        v to c
    }.toTypedArray()
}

/** One gradient circle, its bright point at `angle` degrees (0 is right, 90 is down). */
private fun DrawScope.drawOrb(center: Offset, d: Float, angle: Float, s: Float, glow: Float, screen: Boolean) {
    val r = d / 2f
    val a = Math.toRadians(angle.toDouble())
    val dir = Offset(cos(a).toFloat(), sin(a).toFloat())
    val light = center + dir * r
    val bounds = Rect(center, r + 2f)
    val clip = Path().apply { addOval(Rect(center, r)) }
    val canvas = drawContext.canvas

    canvas.saveLayer(bounds, Paint().apply { if (screen) blendMode = BlendMode.Screen })
    clipPath(clip) {
        // The body: lit from its bright point, the night everywhere else.
        drawCircle(
            Brush.radialGradient(
                0f to lerp(White, Dark.Mix, 0.7f),
                0.07f to lerp(Dark.Night, Dark.Mix, 0.6f),
                0.22f to lerp(Dark.Night, Dark.Blue, 0.35f),
                0.55f to Dark.Night,
                center = light,
                radius = d * 1.1f,
            ),
            radius = r,
            center = center,
            alpha = 0.95f,
        )
        val warm = Math.toRadians(angle + 137.5)
        drawCircle(
            Brush.radialGradient(
                0f to Dark.Red.copy(alpha = 0.18f),
                0.7f to Color.Transparent,
                center = center + Offset(cos(warm).toFloat(), sin(warm).toFloat()) * (0.38f * d),
                radius = 0.55f * d,
            ),
            radius = r,
            center = center,
            alpha = 0.95f,
        )

        // The inner arc: blue, the mix, red, fanned around the bright point, in a band inside the edge.
        canvas.saveLayer(bounds, Paint().apply { blendMode = BlendMode.Screen; alpha = 0.85f })
        rotate(angle, center) {
            drawCircle(
                Brush.sweepGradient(
                    0f to Dark.Mix,
                    0.125f to Dark.Red,
                    0.25f to Color.Transparent,
                    0.75f to Color.Transparent,
                    0.875f to Dark.Blue,
                    1f to Dark.Mix,
                    center = center,
                ),
                radius = r,
                center = center,
            )
        }
        drawCircle(
            Brush.radialGradient(
                *ringStops(
                    (r - 110f * s) / r to Color.Transparent,
                    (r - 60.5f * s) / r to Color.Black.copy(alpha = 0.35f),
                    (r - 14f * s) / r to Color.Black,
                    (r - 6f * s) / r to Color.Transparent,
                    1f to Color.Transparent,
                ),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
            blendMode = BlendMode.DstIn,
        )
        canvas.restore()

        // The rim, and its blurred glow, which breathes.
        rim(center, r + 14f * s, angle, glow) {
            ringStops(
                (r + 14f * s - 40f * s) / (r + 14f * s) to Color.Transparent,
                (r + 14f * s - 22f * s) / (r + 14f * s) to Color.Black.copy(alpha = 0.7f),
                1f to Color.Transparent,
            )
        }
        rim(center, r, angle, 1f) {
            ringStops(
                (r - 10f * s) / r to Color.Transparent,
                (r - 10f * s + 1f) / r to Color.Black,
                (r - 1f) / r to Color.Black,
                1f to Color.Transparent,
            )
        }

        flares(center, r, d, dir, angle)
    }
    canvas.restore()
}

/** The rim's colours around the circle: red before the bright point, white-pink at it, blue after. */
private fun rimBrush(center: Offset) = Brush.sweepGradient(
    0f to lerp(White, Dark.Mix, 0.7f),
    40f / 360f to Dark.Blue,
    60f / 360f to Dark.Blue,
    90f / 360f to Dark.Blue.copy(alpha = 0.1f),
    180f / 360f to Dark.Blue.copy(alpha = 0.06f),
    270f / 360f to Dark.Red.copy(alpha = 0.1f),
    300f / 360f to Dark.Red,
    320f / 360f to Dark.Red,
    1f to lerp(White, Dark.Mix, 0.7f),
    center = center,
)

private fun DrawScope.rim(center: Offset, radius: Float, angle: Float, alpha: Float, mask: () -> Array<Pair<Float, Color>>) {
    val canvas = drawContext.canvas
    canvas.saveLayer(Rect(center, radius + 2f), Paint().apply { this.alpha = alpha.coerceIn(0f, 1f) })
    rotate(angle, center) { drawCircle(rimBrush(center), radius = radius, center = center) }
    drawCircle(
        Brush.radialGradient(*mask(), center = center, radius = radius),
        radius = radius,
        center = center,
        blendMode = BlendMode.DstIn,
    )
    canvas.restore()
}

/** The lens flare: a bright source at the lit point and faint discs and rings along the line through the middle. */
private fun DrawScope.flares(center: Offset, r: Float, d: Float, dir: Offset, angle: Float) {
    fun at(t: Float) = center + dir * (r * (1f - t))
    fun fade(t: Float) = (1f - abs(t) * 0.17f).coerceIn(0f, 1f)

    // The source.
    drawCircle(
        Brush.radialGradient(
            0f to White,
            0.05f to lerp(White, Dark.Mix, 0.4f),
            0.16f to Dark.Mix.copy(alpha = 0.45f),
            0.45f to Dark.Blue.copy(alpha = 0.14f),
            1f to Color.Transparent,
            center = at(0f),
            radius = 0.23f * d,
        ),
        radius = 0.23f * d,
        center = at(0f),
        blendMode = BlendMode.Screen,
    )

    fun disc(t: Float, sizePct: Float, rimW: Float) {
        val rr = sizePct * d / 2f
        val c = at(t)
        drawCircle(
            Brush.radialGradient(
                0f to Dark.Mix.copy(alpha = 0.09f),
                0.78f to Dark.Blue.copy(alpha = 0.2f),
                0.96f to Dark.Mix.copy(alpha = 0.3f),
                1f to Color.Transparent,
                center = c,
                radius = rr,
            ),
            radius = rr,
            center = c,
            alpha = fade(t),
            blendMode = BlendMode.Screen,
        )
        if (rimW > 0f) ring(c, rr, rimW, angle, fade(t))
    }

    disc(0.3f, 0.09f, 5f)
    disc(2.65f, 0.12f, 5f)
    disc(1.25f, 0.07f, 4f)
    disc(-0.28f, 0.06f, 4f)
    ring(at(2.3f), 0.15f * d, 5f, angle, fade(2.3f))
    ring(at(0.9f), 0.08f * d, 6f, angle, fade(0.9f))

    // A pale red hexagon past the middle.
    val hc = at(1.6f)
    val hr = 0.11f * d
    val hex = Path().apply {
        for (i in 0 until 6) {
            val ang = Math.toRadians(i * 60.0)
            val p = Offset(hc.x + hr * cos(ang).toFloat(), hc.y + hr * 0.866f * sin(ang).toFloat() / 0.866f)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(hex, Dark.Red.copy(alpha = 0.16f), alpha = fade(1.6f), blendMode = BlendMode.Screen)
}

/** A thin ring in the rim's colours. `w` is its width per thousand of the circle. */
private fun DrawScope.ring(c: Offset, radius: Float, w: Float, angle: Float, alpha: Float) {
    val width = max(1.5f, radius * 2f * w / 1000f * 4f)
    rotate(angle, c) {
        drawCircle(
            rimBrush(c),
            radius = radius - width / 2f,
            center = c,
            alpha = alpha * 0.8f,
            style = Stroke(width),
            blendMode = BlendMode.Screen,
        )
    }
}
