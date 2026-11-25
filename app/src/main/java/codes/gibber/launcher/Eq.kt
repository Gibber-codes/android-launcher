package codes.gibber.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The launcher's bass boost: a switch and a strength. It shapes the phone's own sound before it
 * leaves, so it reaches whatever plays it (a speaker, two linked speakers, headphones), unlike a
 * speaker's own equalizer. Android's BassBoost effect is missing on many phones, so this lifts the
 * low bands of the phone's equalizer instead, and moves the whole curve down so its middle stays
 * at 0 dB: turning it on doesn't make the music louder. It runs on the phone's whole output
 * (session 0) and on each player that hands the launcher its sound; Android doesn't promise
 * either takes effect.
 */
object Eq {
    /** The most the bass is lifted, in dB, at full strength. */
    const val TOP_DB = 8f
    /** Full lift up to here, in hertz, easing to none by [EASE_TO]. */
    private const val FULL_TO = 120
    private const val EASE_TO = 500

    private val bySession = mutableMapOf<Int, Equalizer>()
    private var started = false
    private var bands = 0
    private var range = -1500..1500
    private var centres = IntArray(0)

    var on by mutableStateOf(false)
    /** 0 to 1. */
    var strength by mutableFloatStateOf(0.5f)
    var problem by mutableStateOf<String?>(null)
    var ready by mutableIntStateOf(0)

    fun start(context: Context) {
        if (started) return
        started = true
        val sp = context.getSharedPreferences("eq", Context.MODE_PRIVATE)
        on = sp.getBoolean("bass", false)
        strength = sp.getFloat("strength", 0.5f).coerceIn(0f, 1f)
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

    /** How much one pitch is lifted, in dB: all of it in the bass, easing off by 500 Hz. */
    private fun liftAt(hz: Int): Float {
        val top = TOP_DB * strength
        if (hz <= FULL_TO) return top
        if (hz >= EASE_TO) return 0f
        return top * ((ln(EASE_TO.toFloat()) - ln(hz.toFloat())) / (ln(EASE_TO.toFloat()) - ln(FULL_TO.toFloat())))
    }

    /** What each of the phone's bands is set to, in millibels, with the middle kept at 0 dB. */
    private fun bandLevels(): List<Int> {
        if (bands == 0) return emptyList()
        val raw = centres.map { liftAt(it) }
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

    fun turn(context: Context, value: Boolean) {
        on = value
        applyAll()
        save(context)
    }

    fun setStrength(context: Context, value: Float) {
        strength = value.coerceIn(0f, 1f)
        applyAll()
        save(context)
    }

    private fun save(context: Context) {
        context.getSharedPreferences("eq", Context.MODE_PRIVATE).edit()
            .putBoolean("bass", on)
            .putFloat("strength", strength)
            .apply()
    }
}

/** The bass boost as the music screen shows it: the switch, then how strong. */
@Composable
fun BassPanel() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Eq.start(context) }
    Eq.ready
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Head("Bass boost", Modifier.weight(1f))
            PillSwitch(Eq.on) { Eq.turn(context, it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Strength", color = if (Eq.on) Color.White else Dark.W40, fontSize = 13.sp)
            Box(
                Modifier
                    .weight(1f)
                    .height(28.dp)
                    .pointerInput(Unit) { detectTapGestures { Eq.setStrength(context, it.x / size.width) } }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ -> Eq.setStrength(context, change.position.x / size.width) }
                    },
                contentAlignment = Alignment.CenterStart,
            ) { Bar(Eq.strength, Modifier.fillMaxWidth()) }
            Text(
                "+${(Eq.TOP_DB * Eq.strength).roundToInt()} dB",
                color = if (Eq.on) Color.White else Dark.W40,
                fontFamily = Dark.Mono,
                fontSize = 11.sp,
            )
        }
        Text("Shaped on the phone, so it reaches any speaker or headphones. Volume stays even.", color = Dark.W40, fontSize = 11.sp)
        Eq.problem?.let { Text(it, color = Dark.Research, fontSize = 11.sp) }
    }
}
