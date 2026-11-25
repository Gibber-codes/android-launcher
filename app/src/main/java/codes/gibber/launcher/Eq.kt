package codes.gibber.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The launcher's own equalizer: seven sliders from 60 Hz to 15 kHz. Android gives a phone a few
 * bands of its own (often five); each of those follows the sliders' curve at its own pitch. The
 * curve is moved up or down so its middle stays at 0 dB, so turning it on doesn't change how loud
 * the music is. It runs on the phone's whole output (session 0) and on each player that hands the
 * launcher its sound; Android doesn't promise either takes effect.
 */
object Eq {
    /** The sliders' pitches, in hertz. */
    val PITCHES = intArrayOf(60, 150, 400, 1000, 2400, 6000, 15000)
    val NAMES = listOf("60", "150", "400", "1k", "2.4k", "6k", "15k")
    const val TOP_DB = 6

    val PRESETS = linkedMapOf(
        "Bass boost" to listOf(5, 4, 2, 0, 0, 0, 0),
        "Vocal" to listOf(-2, -1, 1, 3, 3, 1, 0),
        "Flat" to listOf(0, 0, 0, 0, 0, 0, 0),
    )

    private val bySession = mutableMapOf<Int, Equalizer>()
    private var started = false
    private var bands = 0
    private var range = -1500..1500
    private var centres = IntArray(0)

    var on by mutableStateOf(false)
    /** The seven sliders, in dB. */
    val levels = mutableStateListOf(0, 0, 0, 0, 0, 0, 0)
    /** "Custom", or the preset in use. */
    var preset by mutableStateOf("Custom")
    private var custom = List(7) { 0 }
    var problem by mutableStateOf<String?>(null)
    var ready by mutableIntStateOf(0)

    fun start(context: Context) {
        if (started) return
        started = true
        val sp = context.getSharedPreferences("eq", Context.MODE_PRIVATE)
        on = sp.getBoolean("on", false)
        custom = sp.getString("custom", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 7 } ?: List(7) { 0 }
        preset = sp.getString("preset", "Custom") ?: "Custom"
        (PRESETS[preset] ?: custom).forEachIndexed { i, v -> levels[i] = v }
        attach(0, "the whole phone")
        applyAll()
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(context.applicationContext, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
                if (session <= 0) return
                if (intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) {
                    attach(session, intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "an app")
                    applyAll()
                } else {
                    bySession.remove(session)?.release()
                }
            }
        }, filter, ContextCompat.RECEIVER_EXPORTED)
        ready++
    }

    private fun attach(session: Int, from: String) {
        try {
            bySession.remove(session)?.release()
            val e = Equalizer(1000, session)
            bySession[session] = e
            if (bands == 0) {
                bands = e.numberOfBands.toInt()
                val r = e.bandLevelRange
                range = r[0].toInt()..r[1].toInt()
                centres = IntArray(bands) { e.getCenterFreq(it.toShort()) / 1000 }
            }
        } catch (t: Throwable) {
            problem = "Android wouldn't give the launcher an equalizer for $from."
        }
    }

    /** The sliders' curve at one pitch, in dB, read straight across in octaves. */
    private fun curveAt(hz: Int): Float {
        val x = ln(hz.coerceIn(PITCHES.first(), PITCHES.last()).toFloat())
        for (i in 0 until PITCHES.size - 1) {
            val a = ln(PITCHES[i].toFloat())
            val b = ln(PITCHES[i + 1].toFloat())
            if (x <= b) return levels[i] + (levels[i + 1] - levels[i]) * ((x - a) / (b - a))
        }
        return levels.last().toFloat()
    }

    /** What each of the phone's bands is set to, in millibels, with the middle kept at 0 dB. */
    private fun bandLevels(): List<Int> {
        if (bands == 0) return emptyList()
        val raw = centres.map { curveAt(it) }
        val middle = raw.average().toFloat()
        return raw.map { ((it - middle) * 100f).roundToInt().coerceIn(range) }
    }

    private fun applyAll() {
        val mb = bandLevels()
        for (e in bySession.values) {
            try {
                mb.forEachIndexed { b, v -> e.setBandLevel(b.toShort(), v.toShort()) }
                e.enabled = on
            } catch (_: Throwable) { }
        }
    }

    fun set(context: Context, band: Int, db: Int) {
        if (band !in levels.indices) return
        levels[band] = db.coerceIn(-TOP_DB, TOP_DB)
        preset = "Custom"
        custom = levels.toList()
        applyAll()
        save(context)
    }

    fun pick(context: Context, name: String) {
        preset = name
        (PRESETS[name] ?: custom).forEachIndexed { i, v -> levels[i] = v }
        applyAll()
        save(context)
    }

    fun turn(context: Context, value: Boolean) {
        on = value
        applyAll()
        save(context)
    }

    private fun save(context: Context) {
        context.getSharedPreferences("eq", Context.MODE_PRIVATE).edit()
            .putBoolean("on", on)
            .putString("preset", preset)
            .putString("custom", custom.joinToString(","))
            .apply()
    }
}

/** The equalizer as the music screen shows it: the switch, presets and seven sliders. */
@Composable
fun EqualizerPanel() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Eq.start(context) }
    Eq.ready
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Head("Equalizer", Modifier.weight(1f))
            PillSwitch(Eq.on) { Eq.turn(context, it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("Custom") + Eq.PRESETS.keys).forEach { name ->
                Text(
                    name,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .frost(hi = Eq.preset == name)
                        .pointerInput(name) { detectTapGestures { Eq.pick(context, name) } }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Eq.levels.forEachIndexed { i, db ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (db > 0) "+$db" else "$db", color = Dark.W62, fontFamily = Dark.Mono, fontSize = 10.sp)
                    EqSlider(db) { Eq.set(context, i, it) }
                    Text(Eq.NAMES[i], color = Color.White, fontFamily = Dark.Mono, fontSize = 10.sp)
                }
            }
        }
        Text("Volume stays even when you move the sliders", color = Dark.W40, fontSize = 11.sp)
        Eq.problem?.let { Text(it, color = Dark.Research, fontSize = 11.sp) }
    }
}

/** One upright slider: a thin track, the 0 dB line, a red-to-blue fill and a white knob. */
@Composable
private fun EqSlider(db: Int, onChange: (Int) -> Unit) {
    fun dbAt(y: Float, h: Float): Int =
        (Eq.TOP_DB - (y / h) * 2 * Eq.TOP_DB).roundToInt().coerceIn(-Eq.TOP_DB, Eq.TOP_DB)
    Box(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .pointerInput(Unit) { detectTapGestures { onChange(dbAt(it.y, size.height.toFloat())) } }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ -> onChange(dbAt(change.position.y, size.height.toFloat())) }
            }
            .drawBehind {
                val cx = size.width / 2f
                val mid = size.height / 2f
                val y = mid - (db / Eq.TOP_DB.toFloat()) * mid
                drawLine(Color.White.copy(alpha = 0.16f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 2.dp.toPx())
                drawLine(Color.White.copy(alpha = 0.3f), Offset(size.width * 0.3f, mid), Offset(size.width * 0.7f, mid), strokeWidth = 1.dp.toPx())
                val top = minOf(y, mid)
                val h = kotlin.math.abs(mid - y)
                if (h > 0f) {
                    drawRect(
                        Brush.verticalGradient(listOf(Dark.Red, Dark.Blue), startY = top, endY = top + h),
                        topLeft = Offset(cx - 2.dp.toPx(), top),
                        size = Size(4.dp.toPx(), h),
                    )
                }
                drawCircle(Color.Black.copy(alpha = 0.3f), radius = 10.dp.toPx(), center = Offset(cx, y + 1.dp.toPx()))
                drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(cx, y))
            },
    )
}
