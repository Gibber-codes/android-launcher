package codes.gibber.phone.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import codes.gibber.phone.sms.Notifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The GibberCodes colors: dark page, neon blue and red. */
object Ink {
    val Page = Color(0xFF101014)
    val Card = Color(0xFF1A1A21)
    val Raised = Color(0xFF24242D)
    val Line = Color(0xFF2C2C36)
    val Text = Color(0xFFF2F2F5)
    val Muted = Color(0xFF9A9AA8)
    val Blue = Color(0xFF1A8CFF)
    val Red = Color(0xFFFF1F5A)
    val Green = Color(0xFF22C55E)
    val Mine = Color(0xFF1A6FD6)
}

@Composable
fun GibberTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.Blue,
            secondary = Ink.Red,
            background = Ink.Page,
            surface = Ink.Card,
            surfaceVariant = Ink.Raised,
            onPrimary = Color.White,
            onBackground = Ink.Text,
            onSurface = Ink.Text,
            onSurfaceVariant = Ink.Muted,
            outline = Ink.Line,
            error = Ink.Red,
        ),
        content = content,
    )
}

fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Full screen, like the rest of God's apps: the status bar and navigation buttons hide and the app
 * draws right up to the top edge, around the camera hole. A swipe in from an edge shows them.
 */
@Composable
fun FullScreen() {
    val activity = LocalContext.current.activity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        onDispose { }
    }
}

private val bitmapCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

/** Loads a picture (a contact photo or one in a text) off the main thread, remembered while the app runs. */
@Composable
fun rememberBitmap(uri: Uri?, size: Int): Bitmap? {
    val context = LocalContext.current
    val key = uri?.let { "$it@$size" }
    var bmp by remember(key) { mutableStateOf(key?.let { bitmapCache.get(it) }) }
    LaunchedEffect(key) {
        if (key == null || bmp != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { Notifier.loadBitmap(context, uri, size) }
        if (loaded != null) {
            bitmapCache.put(key, loaded)
            bmp = loaded
        }
    }
    return bmp
}

private val avatarColors = listOf(
    Color(0xFF1A8CFF), Color(0xFFFF1F5A), Color(0xFF8B5CF6), Color(0xFF10B981),
    Color(0xFFF59E0B), Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFF64748B),
)

/** A round photo, or the first letter on a color picked from the name. */
@Composable
fun Avatar(name: String, photo: String?, size: Dp = 44.dp, group: Boolean = false) {
    val bmp = rememberBitmap(photo?.let { Uri.parse(it) }, 256)
    Box(
        Modifier.size(size).clip(CircleShape).background(avatarColors[(name.hashCode() and 0x7fffffff) % avatarColors.size]),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            val letter = when {
                group -> "+"
                name.firstOrNull()?.isLetter() == true -> name.first().uppercase()
                else -> "#"
            }
            Text(letter, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.42f).sp)
        }
    }
}
