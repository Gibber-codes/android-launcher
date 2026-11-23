package codes.gibber.launcher

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/** The update pill on the home screen: checks GitHub for a newer build, downloads it and opens the installer. */
@Composable
fun UpdateButton(resumes: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var lastCheck by remember { mutableLongStateOf(0L) }

    fun say(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    // A quiet look every half hour or so, when you come back to the home screen.
    LaunchedEffect(resumes) {
        val now = System.currentTimeMillis()
        if (now - lastCheck > 30 * 60_000L) {
            lastCheck = now
            ready = Updater.latest()?.let { Updater.isNewer(it) } ?: ready
        }
    }

    // Quiet when there is nothing new (tap it to look now), lit when a build is waiting.
    val label = busy ?: if (ready) "Update ready" else "v${BuildConfig.VERSION_NAME}"
    HubPill(
        text = label,
        lit = ready || busy != null,
        modifier = modifier
            .clickable(enabled = busy == null) {
                scope.launch {
                    busy = "Checking…"
                    val release = Updater.latest()
                    if (release == null) {
                        busy = null
                        say("Couldn't reach GitHub to check for an update.")
                        return@launch
                    }
                    if (!Updater.isNewer(release)) {
                        busy = null
                        ready = false
                        say("You're on the newest version (build ${BuildConfig.VERSION_CODE}).")
                        return@launch
                    }
                    ready = true
                    if (!Updater.canInstall(context)) {
                        busy = null
                        say("Turn on Allow for Gibber Launcher, come back, and tap Update again.")
                        Updater.openInstallPermission(context)
                        return@launch
                    }
                    busy = "Downloading…"
                    val apk = Updater.download(context, release)
                    busy = null
                    if (apk == null) say("The download didn't finish. Tap Update to try again.")
                    else Updater.openInstaller(context, apk)
                }
            }
    )
}