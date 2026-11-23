package codes.gibber.launcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * While this is on screen, the status bar and the navigation buttons hide, so the launcher's own
 * apps (Gibber Hub, its games, Gibber Settings) fill the whole screen. A swipe in from the edge
 * shows them for a moment. They come back when it leaves the screen.
 */
/** How many screens want the bars hidden right now, so one closing doesn't bring them back under another. */
private var hiders = 0

@Composable
fun HideSystemBars() {
    val activity = LocalContext.current.activity() ?: return
    DisposableEffect(activity) {
        val window = activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hiders++
        bars.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            hiders--
            if (hiders <= 0) {
                hiders = 0
                bars.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
