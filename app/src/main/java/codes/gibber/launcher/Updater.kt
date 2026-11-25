package codes.gibber.launcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Finds the newest launcher build on GitHub, downloads it and opens Android's installer. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/Gibber-codes/android-launcher/releases/latest"

    data class Release(val build: Int, val apkUrl: String)

    /** The newest published build, or null when GitHub can't be reached or has none. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(LATEST).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode != 200) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val build = json.getString("tag_name").removePrefix("build-").toIntOrNull()
                ?: return@withContext null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) {
                    return@withContext Release(build, asset.getString("browser_download_url"))
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun isNewer(release: Release): Boolean = release.build > BuildConfig.VERSION_CODE

    /** Downloads the build into the app's cache. */
    suspend fun download(context: Context, release: Release): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "gibber-launcher-${release.build}.apk")
            var url = URL(release.apkUrl)
            // GitHub sends the file from another host; follow those hops by hand.
            repeat(5) {
                val conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                val code = conn.responseCode
                if (code in 300..399) {
                    url = URL(url, conn.getHeaderField("Location"))
                    return@repeat
                }
                if (code != 200) return@withContext null
                conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                return@withContext file
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Android asks once whether this launcher may install apps. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * The whole update from one tap: checks, asks for the install permission when needed, downloads
     * and opens Android's installer. `busy` shows the step under way (null when done); `say` tells the result.
     */
    suspend fun install(context: Context, busy: (String?) -> Unit, say: (String) -> Unit) {
        busy("Checking…")
        val release = latest()
        if (release == null) {
            busy(null); say("Couldn't reach GitHub to check for an update."); return
        }
        if (!isNewer(release)) {
            busy(null); say("You're on the newest version (build ${BuildConfig.VERSION_CODE})."); return
        }
        if (!canInstall(context)) {
            busy(null)
            say("Turn on Allow for Gibber Launcher, come back, and tap Check again.")
            openInstallPermission(context)
            return
        }
        busy("Downloading…")
        val apk = download(context, release)
        busy(null)
        if (apk == null) say("The download didn't finish. Tap Check to try again.")
        else if (!installQuietly(context, apk)) openInstaller(context, apk)
    }

    /**
     * Installs through Android's own installer session, asking it not to show "Do you want to install
     * this update?" (Android 12 and later). Android skips that only once this launcher installed the
     * version being replaced, so the first update this way may still ask. False if the session failed.
     */
    suspend fun installQuietly(context: Context, apk: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("update.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val done = PendingIntent.getBroadcast(context, id, Intent(context, InstallResult::class.java), flags)
                session.commit(done.intentSender)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
