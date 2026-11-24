package codes.gibber.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * A test of an equalizer the launcher runs itself: one on the phone's whole output (session 0),
 * and one on each player that hands the launcher its sound (the audio effect broadcast). Android
 * doesn't promise either takes effect, so the card says what it caught and the ear decides.
 */
object Eq {
    private val bySession = mutableMapOf<Int, Equalizer>()
    private var started = false
    private var bands = 0
    private var range = -1500..1500
    private var centres = IntArray(0)

    var on by mutableStateOf(false)
    var levels = mutableStateListOf<Int>()
    /** What handed us its sound, newest first, and the whole phone when that took. */
    val caught = mutableStateListOf<String>()
    var problem by mutableStateOf<String?>(null)
    var ready by mutableIntStateOf(0)

    fun start(context: Context) {
        if (started) return
        started = true
        val sp = context.getSharedPreferences("eq", Context.MODE_PRIVATE)
        on = sp.getBoolean("on", false)
        attach(0, "the whole phone")
        val saved = sp.getString("levels", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
        levels.clear()
        repeat(bands) { levels.add(saved?.getOrNull(it) ?: 0) }
        applyAll()
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(context.applicationContext, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val session = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
                if (session <= 0) return
                val from = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "an app"
                if (intent.action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION) {
                    attach(session, from)
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
            val name = when (from) {
                "com.spotify.music" -> "Spotify"
                else -> from
            }
            caught.remove(name)
            caught.add(0, name)
        } catch (t: Throwable) {
            problem = "Android wouldn't give the launcher an equalizer for ${if (session == 0) "the whole phone" else from}: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    private fun applyAll() {
        for (e in bySession.values) {
            try {
                for (b in 0 until minOf(bands, levels.size)) e.setBandLevel(b.toShort(), levels[b].toShort())
                e.enabled = on
            } catch (_: Throwable) { }
        }
    }

    fun set(context: Context, band: Int, level: Int) {
        if (band in levels.indices) levels[band] = level.coerceIn(range)
        applyAll()
        save(context)
    }

    fun turn(context: Context, value: Boolean) {
        on = value
        applyAll()
        save(context)
    }

    fun flat(context: Context) {
        for (i in levels.indices) levels[i] = 0
        applyAll()
        save(context)
    }

    private fun save(context: Context) {
        context.getSharedPreferences("eq", Context.MODE_PRIVATE).edit()
            .putBoolean("on", on).putString("levels", levels.joinToString(",")).apply()
    }

    fun bandNames() = centres.map { if (it >= 1000) "${it / 1000}k" else "$it" }
    fun rangeOf() = range
}

/** The test card on the Dev page: a switch, a slider per band, and what handed its sound over. */
@Composable
fun EqualizerTest() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Eq.start(context) }
    Eq.ready // read so the card draws again once the equalizer is up
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF1F1E23))
            .border(1.dp, Color(0xFF34333A), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer test", color = Look.Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text("Play Spotify, turn it on and move a slider", color = Look.Muted, fontSize = 12.sp)
            }
            Switch(checked = Eq.on, onCheckedChange = { Eq.turn(context, it) })
        }
        Eq.problem?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF8A8D), fontSize = 12.sp)
        }
        val names = Eq.bandNames()
        val range = Eq.rangeOf()
        Eq.levels.forEachIndexed { i, level ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(names.getOrElse(i) { "" }, color = Look.Muted, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                Slider(
                    value = level.toFloat(),
                    onValueChange = { Eq.set(context, i, it.toInt()) },
                    valueRange = range.first.toFloat()..range.last.toFloat(),
                    enabled = Eq.on,
                    colors = SliderDefaults.colors(thumbColor = Look.Cream, activeTrackColor = Color(0xFFC39CDC)),
                    modifier = Modifier.weight(1f),
                )
                Text("${level / 100} dB", color = Look.Muted, fontSize = 11.sp, modifier = Modifier.width(44.dp).padding(start = 6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (Eq.caught.isEmpty()) "Nothing handed over its sound yet"
                else "Working on: " + Eq.caught.joinToString(", "),
                color = Look.Muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text("Flat", color = Look.Cream, fontSize = 13.sp, modifier = Modifier.clickable { Eq.flat(context) }.padding(8.dp))
        }
    }
}
