package codes.gibber.launcher

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Bumps each time Home is pressed while the launcher is already open. */
    private val homePresses = mutableIntStateOf(0)

    /**
     * True from leaving the launcher until it is in front again. Home pressed in another app then only
     * brings the launcher back as you left it. Cleared in onResume, which comes after the Home intent.
     */
    private var away = false

    /** Bumps on every resume so the app list picks up installs and removals. */
    private val resumes = mutableIntStateOf(0)

    private val apps = mutableStateOf<List<AppEntry>>(emptyList())

    private lateinit var prefs: Prefs
    private var lastUpdateCheck = 0L
    private var updating = false

    /** A new build, downloaded and waiting to install the next time you leave the launcher. */
    private var readyApk: java.io.File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        prefs = Prefs(this)
        // Android 13 and later ask once before an app may post a notification.
        if (Build.VERSION.SDK_INT >= 33 && !UpdateNotice.allowed(this) && !prefs.askedNotifications) {
            prefs.askedNotifications = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (intent?.action == UpdateNotice.ACTION) startUpdate()
        // The first start after an automatic update says so.
        if (prefs.seenBuild in 1 until BuildConfig.VERSION_CODE) {
            Toast.makeText(this, "Gibber Launcher updated to build ${BuildConfig.VERSION_CODE}", Toast.LENGTH_LONG).show()
        }
        prefs.seenBuild = BuildConfig.VERSION_CODE
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
        away = true
        try { Widgets.host(this).stopListening() } catch (e: Exception) { }
        // You've gone to another app, so the update can install without closing what you're looking at.
        val apk = readyApk
        if (apk != null && !updating) {
            readyApk = null
            updating = true
            lifecycleScope.launch {
                if (!Updater.installQuietly(this@MainActivity, apk)) {
                    // Couldn't install by itself: fall back to the notification and the Update button.
                    prefs.notifiedBuild = 0
                    lastUpdateCheck = 0L
                }
                updating = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        away = false
        apps.value = Apps.load(this)
        resumes.intValue++
        checkForUpdate()
    }

    /**
     * A quiet look on GitHub at most every half hour. A new build always posts the notification once,
     * then downloads by itself and tries to install the next time you leave the launcher. The
     * notification stays because Android often still wants a tap, which it won't show in the background.
     */
    private fun checkForUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheck < 30 * 60_000L) return
        lastUpdateCheck = now
        lifecycleScope.launch {
            val release = Updater.latest() ?: return@launch
            if (!Updater.isNewer(release)) {
                UpdateNotice.clear(this@MainActivity)
                return@launch
            }
            if (prefs.notifiedBuild != release.build) {
                prefs.notifiedBuild = release.build
                UpdateNotice.show(this@MainActivity, release.build)
            }
            if (Build.VERSION.SDK_INT >= 31 && Updater.canInstall(this@MainActivity) && !updating) {
                if (readyApk?.name?.contains("-${release.build}.apk") == true) return@launch
                Updater.download(this@MainActivity, release)?.let { readyApk = it }
            }
        }
    }

    /** What a tap on the notification does: the same as the Update button. */
    private fun startUpdate() {
        if (updating) return
        updating = true
        readyApk = null
        UpdateNotice.clear(this)
        // If the update doesn't go through (no permission yet, installer closed), the next return
        // to the home screen posts the notification again.
        prefs.notifiedBuild = 0
        lastUpdateCheck = 0L
        lifecycleScope.launch {
            Updater.install(
                this@MainActivity,
                busy = { if (it == "Downloading…") Toast.makeText(this@MainActivity, "Downloading the update…", Toast.LENGTH_SHORT).show() },
                say = { Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show() },
            )
            updating = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // From another app, Home returns to the launcher page you left; on the launcher, it goes Home.
        if (intent.hasCategory(Intent.CATEGORY_HOME) && !away) homePresses.intValue++
        if (intent.action == UpdateNotice.ACTION) startUpdate()
    }
}
