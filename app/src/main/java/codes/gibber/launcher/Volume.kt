package codes.gibber.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/** The phone's media volume, the one music plays at, wherever it plays. */
object Volume {
    private fun audio(context: Context): AudioManager? = context.getSystemService(AudioManager::class.java)

    fun max(context: Context): Int = try { audio(context)?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0 } catch (_: Throwable) { 0 }

    fun now(context: Context): Int = try { audio(context)?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0 } catch (_: Throwable) { 0 }

    /** Sets it without the system's volume pop-up; a problem to show when Android refuses. */
    fun set(context: Context, level: Int): String? = try {
        audio(context)?.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
        null
    } catch (_: Throwable) {
        "Android didn't let the launcher change the volume (Do Not Disturb can stop it)."
    }
}

/** The volume as the music screen shows it: a speaker, the slider, the percentage. */
@Composable
fun VolumeRow(onNote: (String) -> Unit) {
    val context = LocalContext.current
    val max = remember { Volume.max(context) }
    var level by remember { mutableIntStateOf(Volume.now(context)) }

    // Volume keys and a speaker's own buttons change it from outside; Android announces each change.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { level = Volume.now(context) }
        }
        val registered = try {
            ContextCompat.registerReceiver(context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_EXPORTED)
            true
        } catch (_: Throwable) {
            false
        }
        onDispose { if (registered) try { context.unregisterReceiver(receiver) } catch (_: Throwable) { } }
    }

    if (max <= 0) return
    fun to(fraction: Float) {
        val v = (fraction.coerceIn(0f, 1f) * max).roundToInt()
        if (v == level) return
        val problem = Volume.set(context, v)
        if (problem == null) level = v else onNote(problem)
    }
    val percent = level * 100 / max

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics { contentDescription = "Volume, $percent%" },
    ) {
        Glyph(Glyphs.Volume, 18.dp, Dark.W62)
        Box(
            Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(Unit) { detectTapGestures { to(it.x / size.width) } }
                .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> to(change.position.x / size.width) } },
            contentAlignment = Alignment.CenterStart,
        ) { Bar(level / max.toFloat(), Modifier.fillMaxWidth()) }
        Text(
            "$percent%",
            color = androidx.compose.ui.graphics.Color.White,
            fontFamily = Dark.Mono,
            fontSize = 11.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp),
        )
    }
}
