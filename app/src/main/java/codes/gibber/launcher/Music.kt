package codes.gibber.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.delay

/**
 * Lets the launcher see what's playing, and how many notifications each app has, once you turn it
 * on in Android's notification access. It shows nothing of the notifications themselves.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() = recount()
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val now = try { activeNotifications?.toList().orEmpty() } catch (e: Exception) { emptyList() }
        val counts = now
            .filter { it.isClearable && !it.isOngoing && (it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) == 0 }
            .groupingBy { it.packageName }
            .eachCount()
        (Badges.counts.keys.toSet() - counts.keys).forEach { Badges.counts.remove(it) }
        Badges.counts.putAll(counts)
    }
}

/** How many notifications each app has, by package, for the badges on Home. */
object Badges {
    val counts = mutableStateMapOf<String, Int>()
}

/** What's playing now, read from the phone's media sessions. */
object NowPlaying {
    data class Track(
        val title: String,
        val artist: String,
        val player: String,
        val art: Bitmap?,
        val playing: Boolean,
        val position: Long,
        val duration: Long,
    )

    fun allowed(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openAccess(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    private fun controller(context: Context): MediaController? = try {
        val all = context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(ComponentName(context, MediaListener::class.java))
        all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: all.firstOrNull()
    } catch (e: Exception) {
        null
    }

    fun read(context: Context): Track? {
        if (!allowed(context)) return null
        val c = controller(context) ?: return null
        val m = c.metadata
        val state = c.playbackState
        val pm = context.packageManager
        val player = try { pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() } catch (e: Exception) { c.packageName }
        val position = if (state == null) 0L else {
            val since = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            state.position + if (state.state == PlaybackState.STATE_PLAYING) (since * state.playbackSpeed).toLong() else 0L
        }
        return Track(
            title = m?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            player = player,
            art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = state?.state == PlaybackState.STATE_PLAYING,
            position = position,
            duration = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
        )
    }

    /** Play or pause, skip ahead or back: on the playing app, or the last one when none is open. */
    fun press(context: Context, key: Int) {
        val c = if (allowed(context)) controller(context) else null
        if (c != null) {
            val t = c.transportControls
            when (key) {
                KeyEvent.KEYCODE_MEDIA_NEXT -> t.skipToNext()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> t.skipToPrevious()
                else -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            }
            return
        }
        val audio = context.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    /** The Bluetooth speaker the sound goes to, like your JBL, or null when it plays on the phone. */
    fun speaker(context: Context): String? = try {
        context.getSystemService(AudioManager::class.java)
            .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?.productName?.toString()?.ifBlank { null }
    } catch (e: Exception) {
        null
    }
}

/** Reads what's playing every second while it's on screen. */
@Composable
fun rememberTrack(): NowPlaying.Track? {
    val context = LocalContext.current
    val track by produceState<NowPlaying.Track?>(null) {
        while (true) {
            value = NowPlaying.read(context)
            delay(1_000)
        }
    }
    return track
}

/** The album art, or the design's blue-to-red square while there is none. */
@Composable
fun Cover(art: Bitmap?, size: Dp) {
    if (art != null) {
        Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
    } else {
        Box(
            Modifier
                .size(size)
                .background(Brush.linearGradient(listOf(Dark.Blue, Color(0xFF8A2BE2), Dark.Red)))
        )
    }
}

/** Home's music: the song, where it plays, and back, play and next. Tap it to open the music screen. */
@Composable
fun MusicCard(onOpen: () -> Unit) {
    val context = LocalContext.current
    val track = rememberTrack()
    val speaker = remember(track?.title) { NowPlaying.speaker(context) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .frost()
            .clickable(onClick = onOpen)
            .padding(start = 7.dp, top = 7.dp, bottom = 7.dp, end = 12.dp),
    ) {
        Cover(track?.art, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                track?.title?.ifBlank { null } ?: "Music",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = when {
                track == null && !NowPlaying.allowed(context) -> "Tap to see what's playing"
                track == null -> "Nothing playing"
                else -> listOfNotNull(track.artist.ifBlank { null }, speaker?.let { "on your $it" } ?: track.player).joinToString(" · ")
            }
            Text(sub, color = Dark.W62, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TapGlyph(Glyphs.Prev, 18.dp) { NowPlaying.press(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            TapGlyph(if (track?.playing == true) Glyphs.Pause else Glyphs.Play, 22.dp) {
                NowPlaying.press(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
            TapGlyph(Glyphs.Next, 18.dp) { NowPlaying.press(context, KeyEvent.KEYCODE_MEDIA_NEXT) }
        }
    }
}

@Composable
fun TapGlyph(g: Glyphs, size: Dp, color: Color = Color.White, onTap: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size + 14.dp)
            .pointerInput(g) { detectTapGestures { onTap() } },
    ) { Glyph(g, size, color) }
}

/**
 * The music screen: the song, its controls, the speaker it plays on with its volume, and the
 * equalizer. Swipe down or press Back to close it.
 */
@Composable
fun PlayerScreen(onClose: () -> Unit) {
    BackHandler(enabled = true) { onClose() }
    val context = LocalContext.current
    val track = rememberTrack()
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()) }
    val speaker = remember(track?.title) { NowPlaying.speaker(context) }
    var pulled by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { pulled = 0f },
                    onVerticalDrag = { _, dy ->
                        pulled += dy
                        if (pulled > 120.dp.toPx()) { pulled = Float.NEGATIVE_INFINITY; onClose() }
                    },
                )
            }
    ) {
        OrbBackground()
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable(onClick = onClose).padding(vertical = 6.dp),
            ) {
                Glyph(Glyphs.Down, 16.dp, Dark.W62)
                Text("Close", color = Dark.W62, fontSize = 13.sp)
            }

            Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Cover(track?.art, 190.dp) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(track?.title?.ifBlank { null } ?: "Nothing playing", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text(
                        listOfNotNull(track?.artist?.ifBlank { null }, track?.player).joinToString(" · ").ifBlank { "Start a song in Spotify" },
                        color = Dark.W62,
                        fontSize = 14.sp,
                    )
                }
                val t = track
                Bar(if (t != null && t.duration > 0) t.position / t.duration.toFloat() else 0f, Modifier.fillMaxWidth())
                Row(
                    horizontalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TapGlyph(Glyphs.Prev, 24.dp) { NowPlaying.press(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .frost(hi = true)
                            .clickable { NowPlaying.press(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) },
                    ) { Glyph(if (t?.playing == true) Glyphs.Pause else Glyphs.Play, 26.dp, Color.White) }
                    TapGlyph(Glyphs.Next, 24.dp) { NowPlaying.press(context, KeyEvent.KEYCODE_MEDIA_NEXT) }
                }
            }

            if (!NowPlaying.allowed(context)) {
                FrostButton("Let the launcher see what's playing", Modifier.fillMaxWidth(), height = 44.dp) { NowPlaying.openAccess(context) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Head("Playing on")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFFF5A00)),
                    ) { Glyph(Glyphs.Speaker, 20.dp, Color.White) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(speaker ?: "This phone", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text("Volume", color = Dark.W62, fontSize = 12.sp)
                    }
                    val jbl = remember { Apps.load(context).firstOrNull { it.label.contains("JBL", ignoreCase = true) } }
                    if (jbl != null) FrostButton("Open ${jbl.label}", hi = true, height = 38.dp) { Apps.launch(context, jbl) }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .pointerInput(Unit) {
                            fun set(x: Float) {
                                volume = (x / size.width).coerceIn(0f, 1f)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).toInt(), 0)
                            }
                            detectTapGestures { set(it.x) }
                        }
                        .pointerInput(Unit) {
                            androidx.compose.foundation.gestures.detectHorizontalDragGestures { change, _ ->
                                volume = (change.position.x / size.width).coerceIn(0f, 1f)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volume * maxVolume).toInt(), 0)
                            }
                        },
                    contentAlignment = Alignment.CenterStart,
                ) { Bar(volume, Modifier.fillMaxWidth()) }
            }

            EqualizerPanel()
            Gap(24.dp)
        }
    }

    // The volume can change from the phone's buttons too.
    LaunchedEffect(Unit) {
        while (true) {
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVolume.toFloat()
            delay(1_500)
        }
    }
}
