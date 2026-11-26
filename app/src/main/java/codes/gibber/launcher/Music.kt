package codes.gibber.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.LruCache
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Collections

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
        /** Where the cover is when the player sends a link to it instead of the picture. */
        val artUri: String?,
        val playing: Boolean,
        val position: Long,
        val duration: Long,
    )

    private const val SPOTIFY = "com.spotify.music"

    private fun listener(context: Context) = ComponentName(context, MediaListener::class.java)

    fun allowed(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Set once the access screen has been opened, so the music screen can say what to do if it's still off. */
    var asked by mutableStateOf(false)

    /** Opens the launcher's own notification access switch (Android 11+), else the list of apps. */
    fun openAccess(context: Context) {
        asked = true
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener(context).flattenToString())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (e: Exception) { }
        }
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    /** The launcher's App info, where Android 13+ keeps "Allow restricted settings" in the ⋮ menu. */
    /** Opens Spotify; when it isn't on the phone, its Play Store page. False if neither opened. */
    fun openSpotify(context: Context): Boolean {
        val app = context.packageManager.getLaunchIntentForPackage(SPOTIFY)
        val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SPOTIFY"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SPOTIFY"))
        listOfNotNull(app, store, web).forEach { intent ->
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) { }
        }
        return false
    }

    fun openAppInfo(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) { }
    }

    /** Asks Android to connect the listener now that access is on, so sessions read without a restart. */
    fun rebind(context: Context) {
        try { NotificationListenerService.requestRebind(listener(context)) } catch (e: Exception) { }
    }

    private fun live(c: MediaController) = c.playbackState?.state.let {
        it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING
    }

    private fun controller(context: Context): MediaController? = try {
        val all = context.getSystemService(MediaSessionManager::class.java).getActiveSessions(listener(context))
        all.firstOrNull { live(it) } ?: all.firstOrNull { it.packageName == SPOTIFY } ?: all.firstOrNull()
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
        fun text(vararg keys: String) = keys.firstNotNullOfOrNull { k -> m?.getString(k)?.ifBlank { null } }.orEmpty()
        fun bitmap(vararg keys: String) = keys.firstNotNullOfOrNull { k -> try { m?.getBitmap(k) } catch (e: Exception) { null } }
        return Track(
            title = text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = text(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            player = player,
            art = bitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            artUri = text(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).ifBlank { null },
            playing = live(c),
            position = position,
            duration = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
        )
    }

    private val covers = LruCache<String, Bitmap>(6)
    private val noCover: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** The cover at a link, loaded off the main thread once and kept; null when it can't be read. */
    suspend fun cover(context: Context, uri: String): Bitmap? {
        covers.get(uri)?.let { return it }
        if (uri in noCover) return null
        val bitmap = withContext(Dispatchers.IO) { try { loadCover(context, uri) } catch (e: Throwable) { null } }
        if (bitmap != null) covers.put(uri, bitmap) else {
            if (noCover.size > 50) noCover.clear()
            noCover.add(uri)
        }
        return bitmap
    }

    /** Reads a picture from a web link or a content link, shrunk by halves while it stays at least [max] pixels. */
    internal fun loadCover(context: Context, uri: String, max: Int = 512): Bitmap? {
        val u = Uri.parse(uri)
        val bytes = when (u.scheme?.lowercase()) {
            "http", "https" -> java.net.URL(uri).openConnection().run {
                connectTimeout = 5_000
                readTimeout = 5_000
                getInputStream().use { it.readBytes() }
            }
            else -> context.contentResolver.openInputStream(u)?.use { it.readBytes() }
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= max && bounds.outHeight / (sample * 2) >= max) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
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

/** Reads what's playing every second while it's on screen, with its cover loaded from a link if it came as one. */
@Composable
fun rememberTrack(): NowPlaying.Track? {
    val context = LocalContext.current
    val track by produceState<NowPlaying.Track?>(null) {
        while (true) {
            val t = NowPlaying.read(context)
            value = if (t != null && t.art == null && t.artUri != null) t.copy(art = NowPlaying.cover(context, t.artUri)) else t
            delay(1_000)
        }
    }
    return track
}

/**
 * Whether notification access is on, read every second: it changes in Android's settings while
 * the launcher waits behind them, and nothing else tells the screen.
 */
@Composable
fun rememberAccess(): Boolean {
    val context = LocalContext.current
    val allowed by produceState(NowPlaying.allowed(context)) {
        while (true) {
            value = NowPlaying.allowed(context)
            delay(1_000)
        }
    }
    return allowed
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
    val allowed = rememberAccess()
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
                track == null && !allowed -> "Tap to see what's playing"
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
 * The music screen: the song and its controls, your Spotify playlists, your JBL speaker and
 * headphones with which one is connected, and the bass boost. Swipe down or press Back to close it.
 */
@Composable
fun PlayerScreen(onClose: () -> Unit) {
    BackHandler(enabled = true) { onClose() }
    val context = LocalContext.current
    val track = rememberTrack()
    val allowed = rememberAccess()
    var pulled by remember { mutableStateOf(0f) }
    // When access is switched on while this screen waits, connect the listener straight away.
    var wasOff by remember { mutableStateOf(!allowed) }
    LaunchedEffect(allowed) {
        if (allowed && wasOff) NowPlaying.rebind(context)
        wasOff = !allowed
    }

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
                FrostButton("Open Spotify", Modifier.fillMaxWidth(), height = 40.dp) { NowPlaying.openSpotify(context) }
            }

            if (!allowed) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FrostButton("Let the launcher see what's playing", Modifier.fillMaxWidth(), height = 44.dp) { NowPlaying.openAccess(context) }
                    // Android 13+ greys the switch out for apps not installed from a store ("Restricted setting").
                    if (NowPlaying.asked && Build.VERSION.SDK_INT >= 33) {
                        Text(
                            "Still off? If the switch was greyed out, open App info, tap ⋮ at the top, then Allow restricted settings, and try again.",
                            color = Dark.W62,
                            fontSize = 12.sp,
                        )
                        FrostButton("Open App info", Modifier.fillMaxWidth(), hi = true, height = 38.dp) { NowPlaying.openAppInfo(context) }
                    }
                }
            }

            PlaylistsPanel()

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Head("Playing on")
                SpeakersPanel()
            }

            BassPanel()
            Gap(24.dp)
        }
    }
}
