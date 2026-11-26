package codes.gibber.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.sqrt

/** The design's easing for the head's rise and the app list's slide, cubic-bezier(.2,.8,.2,1). */
val LogoEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val CssEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/** What moves the logo: its clock, the head's rise and turn, the last page flick and where the eye looks. */
@Stable
class LogoState {
    var time by mutableFloatStateOf(0f)
    var flickAt by mutableFloatStateOf(-10f)
    val rise = Animatable(0f)
    val eye = Animatable(Offset.Zero, Offset.VectorConverter)
    internal var geo: LogoGeo? = null
    internal var scope: CoroutineScope? = null
    private var looking: Job? = null

    /** The head's turn about its vertical axis, in radians; set once a frame, so it changes only while turning. */
    internal var yaw by mutableFloatStateOf(0f)
    private var yawFrom = 0f
    private var yawTo = 0f
    private val head = HeadPose()

    /**
     * A page change: the flicker starts, and the head turns on to its next corner from wherever it is.
     * `dir` is which way: -1 swings the front corner left (a swipe to the next page, right to left), +1
     * swings it right (back to the page before), so the head follows the finger.
     */
    internal fun flick(dir: Int) {
        yawFrom = turnNow()
        yawTo += Quarter * dir
        if (yawTo >= FullTurn) { yawTo -= FullTurn; yawFrom -= FullTurn }
        if (yawTo <= -FullTurn) { yawTo += FullTurn; yawFrom += FullTurn }
        flickAt = time
    }

    /** The turn eases out over 0.5 s from the flicker, its first two steps held so it glitches with it. */
    internal fun turnNow(): Float {
        var u = ((time - flickAt) / TurnSeconds).coerceIn(0f, 1f)
        if (u < 0.3f) u = (u / 0.15f).toInt() * 0.15f
        val v = 1f - u
        return yawFrom + (yawTo - yawFrom) * (1f - v * v * v)
    }

    /**
     * The head's edges for this frame, built once a frame for every copy. It keeps its isometric view
     * all the way up, and spins three whole turns about its upright axis as it rises (back again as it
     * sinks): fast at first, settling with the rise's own easing, while the colours glitch (`spinning`).
     */
    internal fun headEdges(): Path = head.edges(totalYaw(), 0f)

    /** The head's whole turn this frame, in radians: its corner turns plus the spin up or down. */
    internal fun totalYaw(): Float = yaw + FullTurn * RiseTurns * rise.value

    /** True while the head rises or sinks, when red and blue split and flicker hard. */
    internal val spinning: Boolean get() = rise.isRunning

    /**
     * Whether the body's red and blue have swapped sides. They swap each time the head passes a corner
     * (half way between two rest positions), so after an odd number of corners they rest swapped, in step
     * with the head's own red and blue (`headOffX`). Written only when it changes, so the body is redrawn
     * only at a swap.
     */
    internal var bodyFlipped by mutableStateOf(false)

    /** Called once a frame, after `yaw` is set. */
    internal fun trackFlip() {
        val flipped = round(totalYaw() / Quarter).toInt() % 2 != 0
        if (flipped != bodyFlipped) bodyFlipped = flipped
    }

    /** The head's lift in logo units: the slow bob, plus the rise toward the top. */
    internal fun headY(): Float {
        var p = (time / 3.6f) % 2f
        if (p > 1f) p = 2f - p
        return -12f * CssEaseInOut.transform(p) - rise.value * (geo?.riseUnits ?: 0f)
    }

    /** When the eye last glitched back to the centre: red and blue flicker apart for a moment from then. */
    var eyeGlitchAt by mutableFloatStateOf(-10f)
    private var lastLook = -100f

    /** Snaps the pupil onto a point on the screen, holds it there a second, then glitches back. */
    fun look(at: Offset) {
        val g = geo ?: return
        val s = scope ?: return
        val dx = at.x - g.x(256f)
        val dy = at.y - g.y(145.18f - 46f + headY())
        val len = hypot(dx, dy)
        if (len < 1f) return
        val reach = min(1f, len / (120f * g.k))
        // The eye is wide and short, so the pupil travels further sideways than up and down.
        val target = Offset(dx / len * 12f * reach, dy / len * 3f * reach)
        lastLook = time
        looking?.cancel()
        looking = s.launch {
            eye.animateTo(target, tween(90, easing = FastOutSlowInEasing))
            delay(1_000)
            glitchBack()
        }
    }

    /** Back to the centre in three held jumps, overshooting once, while red and blue flicker. */
    private suspend fun glitchBack() {
        val from = eye.value
        eyeGlitchAt = time
        eye.snapTo(from * -0.35f)
        delay(60)
        eye.snapTo(from * 0.4f)
        delay(60)
        eye.snapTo(Offset.Zero)
    }

    /**
     * Every 9 to 20 seconds, if nothing has touched the eye for a while, it glances about on its own: two
     * to four looks at random spots, then back to the centre. A tap takes over at once. Runs only while the
     * logo does.
     */
    internal suspend fun idleGlances() {
        while (true) {
            delay(Random.nextLong(9_000, 20_000))
            if (looking?.isActive == true || time - lastLook in 0f..6f) continue
            val s = scope ?: continue
            val glance = s.launch {
                repeat(Random.nextInt(2, 5)) {
                    val a = Random.nextFloat() * FullTurn
                    val reach = 0.5f + Random.nextFloat() * 0.5f
                    eye.animateTo(Offset(cos(a) * 12f * reach, sin(a) * 3f * reach), tween(160, easing = FastOutSlowInEasing))
                    delay(Random.nextLong(350, 900))
                }
                eye.animateTo(Offset.Zero, tween(260, easing = FastOutSlowInEasing))
            }
            looking = glance
            glance.join()
        }
    }
}

@Composable
fun rememberLogoState(): LogoState = remember { LogoState() }

/** Watches taps for the eye without taking them, so the pages and apps get every touch as before. */
fun Modifier.logoTaps(state: LogoState, enabled: Boolean): Modifier = pointerInput(state, enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            if (!change.pressed) {
                if (change.uptimeMillis - down.uptimeMillis < 400) state.look(change.position)
                break
            }
        }
    }
}

/**
 * The home screen's background: the GibberCodes logo along the bottom under a slight dark glass, as
 * in the design. Its red and blue copies jump together in small steps and flicker apart when the page
 * changes; the head bobs, turns one corner with each flicker (its way set by the swipe's), spins three
 * fast, glitching turns as it rises toward the top while the app list is open, and its eye locks onto a tap
 * for a second, glitches back, and now and then glances about on its own. Each moving part is its own layer,
 * drawn once, so a frame only moves layers; only the head's edges are drawn again, and only while it
 * turns. The clock runs only while the launcher is in front and nothing covers home; otherwise it stands still.
 */
@Composable
fun LogoBackground(state: LogoState, page: Int, raised: Boolean, covered: Boolean, modifier: Modifier = Modifier) {
    var resumed by remember { mutableStateOf(true) }
    val view = LocalView.current
    DisposableEffect(view) {
        val lifecycle = view.findViewTreeLifecycleOwner()?.lifecycle
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val running = resumed && !covered
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                var t = state.time + ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                // 1260 s holds a whole number of every loop (0.7 and 7.2 s), so the wrap can't be seen.
                if (t > 1260f) { t -= 1260f; state.flickAt -= 1260f }
                state.time = t
                state.yaw = state.turnNow()
                state.trackFlip()
                last = now
            }
        }
    }
    LaunchedEffect(running) {
        if (running) state.idleGlances()
    }
    LaunchedEffect(raised) {
        state.rise.animateTo(if (raised) 1f else 0f, tween(550, easing = LogoEasing))
    }
    val lastPage = remember { intArrayOf(page) }
    LaunchedEffect(page) {
        if (page != lastPage[0]) state.flick(if (page > lastPage[0]) -1 else 1)
        lastPage[0] = page
    }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val pad = with(LocalDensity.current) { 16.dp.toPx() }
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val geo = remember(w, h, pad) { LogoGeo(w, h, pad) }
        SideEffect {
            state.geo = geo
            state.scope = scope
        }
        if (w >= 2f && h >= 2f) LogoLayers(geo, state)
    }
}

@Composable
private fun LogoLayers(geo: LogoGeo, state: LogoState) {
    Box(Modifier.fillMaxSize()) {
        LogoCopy(geo, state, Ink.Red, glow = true)
        LogoCopy(geo, state, Ink.Blue, glow = true)
        Box(Modifier.fillMaxSize().clip(geo.leftHalf)) {
            LogoCopy(geo, state, Ink.Blue)
            LogoCopy(geo, state, Ink.Red)
        }
        Box(Modifier.fillMaxSize().clip(geo.rightHalf)) {
            LogoCopy(geo, state, Ink.Red)
            LogoCopy(geo, state, Ink.Blue)
        }
        LogoCopy(geo, state, Ink.White)

        // The slight dark glass: the design's night at 38% and brightness .8, with its faint sheen.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Dark.Night.copy(alpha = 0.5f))
            drawRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.03f),
                    0.38f to Color.Transparent,
                    0.72f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.01f),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
            )
        }
    }
}

/** One colour of the logo: where it sits, and whether it jumps and which way it flickers. */
private enum class Ink(val color: Color, val dx: Float, val brickDx: Float, val jumps: Boolean, val flick: Float) {
    Blue(Color(0xFF1A8CFF), 13f, -7.15f, true, -1f),
    Red(Color(0xFFFF1F5A), -13f, 7.15f, true, 1f),
    White(Color.White, 0f, 0f, false, 0f),
}

private val JumpX = floatArrayOf(0f, -2.5f, 1.5f, -1.5f, 2f)
private val JumpY = floatArrayOf(0f, 1f, -1f, 0f, 1f)
private val FlickX = floatArrayOf(-6f, 3f, -4f, 0f)
private val FlickY = floatArrayOf(1f, -1f, 0f, 0f)

/** The design's `step-end` keyframes: 0.7 s of jumps, and a 0.45 s flicker after a page change. */
private fun jumpStep(t: Float) = ((t % 0.7f) / 0.7f * 5f).toInt().coerceIn(0, 4)

private fun flickStep(state: LogoState): Int {
    val e = (state.time - state.flickAt) / 0.45f
    return when { e < 0f || e > 1f -> 3; e < 0.3f -> 0; e < 0.6f -> 1; e < 0.9f -> 2; else -> 3 }
}

/** While the head spins up or down, red and blue jump apart through the flicker's steps 24 times a second. */
private fun spinStep(state: LogoState) = (state.time * 24f).toInt() % 3

private fun copyX(state: LogoState, ink: Ink): Float {
    if (!ink.jumps) return 0f
    val spin = if (state.spinning) FlickX[spinStep(state)] * SpinSplit * ink.flick else 0f
    return JumpX[jumpStep(state.time)] + FlickX[flickStep(state)] * ink.flick + spin
}

private fun copyY(state: LogoState, ink: Ink): Float {
    if (!ink.jumps) return 0f
    val spin = if (state.spinning) FlickY[spinStep(state)] * SpinSplit * ink.flick else 0f
    return JumpY[jumpStep(state.time)] + FlickY[flickStep(state)] * ink.flick + spin
}

/**
 * Where a colour's head sits around the white one, in logo units: its offset turns with the head, at
 * twice the head's turn, so red and blue go round the white as the head spins and have swapped sides at
 * each corner, just as the body's do. The circle is seen at the camera's angle, so it is flattened.
 */
private fun headOffX(state: LogoState, ink: Ink): Float = ink.dx * cos(2f * state.totalYaw())

private fun headOffY(state: LogoState, ink: Ink): Float = ink.dx * sin(2f * state.totalYaw()) * HeadSinE

/** For 0.3 s after the eye glitches back, its red and blue pupils flicker apart through the flicker's steps. */
private fun eyeStep(state: LogoState): Int {
    val e = state.time - state.eyeGlitchAt
    return if (e < 0f || e > 0.3f) 3 else (e / 0.075f).toInt().coerceIn(0, 3)
}

private fun eyeFlickX(state: LogoState, ink: Ink): Float = FlickX[eyeStep(state)] * ink.flick * 0.7f

private fun eyeFlickY(state: LogoState, ink: Ink): Float = FlickY[eyeStep(state)] * ink.flick * 0.7f

/** One copy of the logo: the base and bricks over its head, the pupil inside the head, each a layer. */
@Composable
private fun LogoCopy(geo: LogoGeo, state: LogoState, ink: Ink, glow: Boolean = false) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = copyX(state, ink) * geo.k
                translationY = copyY(state, ink) * geo.k
            }
            .drawWithCache {
                val thick = Stroke(18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val thin = Stroke(6f, cap = StrokeCap.Round)
                val glows = GlowStrokes()
                onDrawWithContent {
                    // In the design the head is drawn first and the base over it.
                    drawContent()
                    // Swapped, the body's red and blue sit on each other's side, bricks shifted the other way too.
                    val side = if (state.bodyFlipped) -1f else 1f
                    inLogo(geo, ink.dx * side) {
                        if (glow) {
                            drawGlow(LogoArt.base, ink.color, glows.thickWide, glows.thickNear)
                            translate(left = ink.brickDx * side) { drawGlow(LogoArt.bricks, ink.color, glows.thinWide, glows.thinNear) }
                        } else {
                            drawPath(LogoArt.base, ink.color, style = thick)
                            translate(left = ink.brickDx * side) { drawPath(LogoArt.bricks, ink.color, style = thin) }
                        }
                    }
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // The head is drawn at the copy's own offset; this moves it round the white one instead.
                    translationX = (headOffX(state, ink) - ink.dx) * geo.k
                    translationY = (state.headY() + headOffY(state, ink)) * geo.k
                }
                .drawWithCache {
                    val thick = Stroke(18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val glows = GlowStrokes()
                    onDrawBehind {
                        // Reads the turn and the tilt, so only this layer is drawn again when they change.
                        val edges = state.headEdges()
                        inLogo(geo, ink.dx) {
                            translate(top = -46f) {
                                // The eye floats in front: no edge shows through it.
                                clipPath(LogoArt.eye, ClipOp.Difference) {
                                    if (glow) drawGlow(edges, ink.color, glows.thickWide, glows.thickNear)
                                    else drawPath(edges, ink.color, style = thick)
                                }
                                if (glow) drawGlow(LogoArt.eye, ink.color, glows.thickWide, glows.thickNear)
                                else drawPath(LogoArt.eye, ink.color, style = thick)
                            }
                        }
                    }
                }
        ) {
            if (!glow) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val e = state.eye.value
                            // The pupil keeps its tighter share of the head's offset as that turns.
                            translationX = (e.x + (PupilPull - 1f) * (headOffX(state, ink) - ink.dx) + eyeFlickX(state, ink)) * geo.k
                            translationY = (e.y + (PupilPull - 1f) * headOffY(state, ink) + eyeFlickY(state, ink)) * geo.k
                        }
                        .drawWithCache {
                            onDrawBehind {
                                inLogo(geo, ink.dx * PupilPull) { translate(top = -46f) { drawPath(LogoArt.pupil, ink.color) } }
                            }
                        }
                )
            }
        }
    }
}

/** The design's glow is a 6 px blur; two wider, faint strokes behind give nearly the same look for free. */
private class GlowStrokes {
    val thickWide = Stroke(32f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thickNear = Stroke(24f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thinWide = Stroke(18f, cap = StrokeCap.Round)
    val thinNear = Stroke(11f, cap = StrokeCap.Round)
}

private fun DrawScope.drawGlow(path: Path, color: Color, wide: Stroke, near: Stroke) {
    drawPath(path, color, alpha = 0.07f, style = wide)
    drawPath(path, color, alpha = 0.12f, style = near)
}

/** Draws in the design's own units (its viewBox is 55 -26 402 526), shifted `dx` like its copies. */
private fun DrawScope.inLogo(geo: LogoGeo, dx: Float, block: DrawScope.() -> Unit) {
    withTransform({
        translate(geo.originX + dx * geo.k, geo.originY)
        scale(geo.k, geo.k, Offset.Zero)
    }, block)
}

/** Where the logo sits: 16 dp in from the sides and the bottom, as wide as that leaves. */
internal class LogoGeo(w: Float, h: Float, pad: Float) {
    val k = min((w - 2f * pad) / 402f, (h - 2f * pad) / 526f)
    val originX = (w - 402f * k) / 2f - 55f * k
    val originY = h - pad - 526f * k + 26f * k
    fun x(u: Float) = originX + u * k
    fun y(u: Float) = originY + u * k

    /** How far the head rises: its top ends near the top, where the design's ends on its 844 tall phone. */
    val riseUnits = max(0f, (y(-17f) - 0.077f * h) / k)
    val leftHalf: Shape = Half(x(256f), left = true)
    val rightHalf: Shape = Half(x(256f), left = false)
}

private const val Quarter = 1.5707964f
private const val FullTurn = 6.2831855f
private const val TurnSeconds = 0.5f
/** Whole turns the head spins while it rises or sinks: fast enough to blur, and it lands on the same view. */
private const val RiseTurns = 3f
/** How much harder than a page flicker red and blue split while the head spins. */
private const val SpinSplit = 1.6f
/** The red and blue pupils sit this share of their copy's offset from the white one, tighter than the rest. */
private const val PupilPull = 0.4f

/**
 * The head as a square pyramid, seen from the design's isometric camera. Its base corners sit on a
 * circle of radius 76.56 at yaw + i quarter turns, its apex above; the camera looks down 35.26 degrees
 * (sin = 44.2 / 76.56, so the base's half-depth is tan 30 of its half-width), and the tilt lowers that.
 * It turns and tilts about the eye's centre (256, 145.18), so at no turn and no tilt it projects onto
 * the design's own points: front (256, 209.8), right (332.56, 165.6), back (256, 121.4),
 * left (179.44, 165.6), apex (256, 38). An edge is drawn when a face it borders faces the camera.
 */
private class HeadPose {
    private val x = FloatArray(5)
    private val y = FloatArray(5)
    private val facing = BooleanArray(4)
    private val path = Path()
    private var lastYaw = Float.NaN
    private var lastTilt = Float.NaN

    fun edges(yaw: Float, tilt: Float): Path {
        if (yaw == lastYaw && tilt == lastTilt) return path
        lastYaw = yaw
        lastTilt = tilt
        val s = HeadR * sin(yaw)
        val c = HeadR * cos(yaw)
        val ca = cos(HeadElevation - tilt)
        val sa = sin(HeadElevation - tilt)
        corner(0, s, c, ca, sa)
        corner(1, c, -s, ca, sa)
        corner(2, -s, -c, ca, sa)
        corner(3, -c, s, ca, sa)
        x[4] = 256f
        y[4] = 145.18f - HeadApexY * ca
        // Screen y points down, so a face toward the camera winds the negative way.
        for (i in 0..3) facing[i] = cross(i, (i + 1) % 4, 4) < 0f
        val base = cross(1, 0, 3) < 0f
        path.reset()
        for (i in 0..3) {
            if (facing[(i + 3) % 4] || facing[i]) line(i, 4)
            if (facing[i] || base) line(i, (i + 1) % 4)
        }
        return path
    }

    private fun corner(i: Int, bx: Float, bz: Float, ca: Float, sa: Float) {
        x[i] = 256f + bx
        y[i] = 145.18f - (HeadBaseY * ca - bz * sa)
    }

    private fun cross(a: Int, b: Int, c: Int): Float =
        (x[b] - x[a]) * (y[c] - y[a]) - (y[b] - y[a]) * (x[c] - x[a])

    private fun line(a: Int, b: Int) {
        path.moveTo(x[a], y[a])
        path.lineTo(x[b], y[b])
    }
}

private const val HeadR = 76.56f
private const val HeadSinE = 44.2f / 76.56f
private val HeadCosE = sqrt(1f - HeadSinE * HeadSinE)
private val HeadElevation = asin(HeadSinE)
/** The apex's and the base's heights above and below the eye's centre, before the camera shortens them. */
private val HeadApexY = (127.6f - 20.42f) / HeadCosE
private val HeadBaseY = -20.42f / HeadCosE

/** The design clips each colour to one side of the logo's middle. */
private class Half(val split: Float, val left: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(if (left) Rect(0f, 0f, split, size.height) else Rect(split, 0f, size.width, size.height))
}

/** The design's logo paths, parsed once. */
private object LogoArt {
    private fun parse(vararg d: String): Path = PathParser().parsePathString(d.joinToString(" ")).toPath()

    /** The head's eye; its edges are built each frame by [HeadPose]. */
    val eye = parse("M202,145.18 A57.37,57.37 0 0 1 310,145.18 A57.37,57.37 0 0 1 202,145.18 Z")
    val pupil = parse("M256,115.54 Q268.92,145.18 256,174.82 Q243.08,145.18 256,115.54 Z")
    val base = parse(
        "M179.44,221.6 L256,265.8",
        "M256,265.8 L332.56,221.6",
        "M332.56,221.6 L256,177.4",
        "M179.44,221.6 L256,177.4",
        "M179.44,221.6 L82,384",
        "M82,384 L256,484.46",
        "M256,484.46 L430,384",
        "M430,384 L332.56,221.6",
        "M256,265.8 L256,484.46",
    )
    val bricks = parse(
        "M155.08,262.2 L256,320.47",
        "M130.72,302.8 L256,375.13",
        "M106.36,343.4 L256,429.79",
        "M204.96,236.33 L188.72,281.62",
        "M230.48,251.07 L222.36,301.04",
        "M171.9,271.91 L151.6,314.86",
        "M205.54,291.33 L193.36,338.97",
        "M239.18,310.76 L235.12,363.08",
        "M172.48,326.91 L156.24,372.2",
        "M214.24,351.02 L206.12,401",
        "M131.3,357.8 L111,400.74",
        "M181.18,386.6 L169,434.23",
        "M231.06,415.4 L227,467.72",
        "M356.92,262.2 L256,320.47",
        "M381.28,302.8 L256,375.13",
        "M405.64,343.4 L256,429.79",
        "M319.8,228.97 L340.1,271.91",
        "M294.28,243.7 L306.46,291.33",
        "M268.76,258.43 L272.82,310.76",
        "M323.28,281.62 L339.52,326.91",
        "M289.64,301.04 L297.76,351.02",
        "M360.4,314.86 L380.7,357.8",
        "M318.64,338.97 L330.82,386.6",
        "M276.88,363.08 L280.94,415.4",
        "M355.76,372.2 L372,417.49",
        "M305.88,401 L314,450.97",
    )
}
