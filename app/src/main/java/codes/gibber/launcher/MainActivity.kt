package codes.gibber.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    /** Bumps each time Home is pressed while the launcher is already open. */
    private val homePresses = mutableIntStateOf(0)

    /** Bumps on every resume so the app list picks up installs and removals. */
    private val resumes = mutableIntStateOf(0)

    private val apps = mutableStateOf<List<AppEntry>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = Prefs(this)
        setContent {
            LauncherApp(
                prefs = prefs,
                apps = apps.value,
                homePresses = homePresses.intValue,
                resumes = resumes.intValue,
                onAppsChanged = { apps.value = Apps.load(this) },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        try { Widgets.host(this).startListening() } catch (e: Exception) { }
    }

    override fun onStop() {
        super.onStop()
        try { Widgets.host(this).stopListening() } catch (e: Exception) { }
    }

    override fun onResume() {
        super.onResume()
        apps.value = Apps.load(this)
        resumes.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.intValue++
    }
}
