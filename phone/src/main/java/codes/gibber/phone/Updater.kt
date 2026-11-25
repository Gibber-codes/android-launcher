package codes.gibber.phone

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds the newest Gibber Phone build on GitHub and opens Android's installer for it.
 * Phone builds are posted as "phone-N" pre-releases, so they never get in the way of the
 * launcher's own Update button, which reads the repo's latest (non-pre) release.
 */
object Updater {
    private const val RELEASES = "https://api.github.com/repos/Gibber-codes/android-launcher/releases?per_page=100&page="

    data class Release(val build: Int, val apkUrl: String)

    /** The newest phone build. Launcher builds share the list, so it looks through a few pages. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        try {
            var best: Release? = null
            for (page in 1..3) {
                val conn = URL(RELEASES + page).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                if (conn.responseCode != 200) break
                val list = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                for (i in 0 until list.length()) {
                    val r = list.getJSONObject(i)
                    val build = r.getString("tag_name").takeIf { it.startsWith("phone-") }?.removePrefix("phone-")?.toIntOrNull() ?: continue
                    val assets = r.getJSONArray("assets")
                    for (j in 0 until assets.length()) {
                        val a = assets.getJSONObject(j)
                        if (a.getString("name") == "gibber-phone.apk" && (best == null || build > best.build)) {
                            best = Release(build, a.getString("browser_download_url"))
                        }
                    }
                }
                if (list.length() < 100) break
            }
            best
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun download(context: Context, release: Release): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "gibber-phone-${release.build}.apk")
            var url = URL(release.apkUrl)
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
        } catch (_: Exception) {
            null
        }
    }

    /** The newest phone build, when it's newer than this one. */
    suspend fun newer(): Release? = latest()?.takeIf { it.build > BuildConfig.VERSION_CODE }

    /** The whole Update tap. [say] shows progress; the return value is what to show at the end. */
    suspend fun run(context: Context, say: (String) -> Unit): String {
        say("Checking…")
        val release = latest() ?: return "Couldn't reach GitHub. Try again in a bit."
        if (release.build <= BuildConfig.VERSION_CODE) return "You're on the newest (build ${BuildConfig.VERSION_CODE})"
        if (codes.gibber.phone.call.CallManager.calls.value.isNotEmpty()) return "Update after your call ends"
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return "Turn on Allow, come back and tap Update again"
        }
        say("Downloading build ${release.build}…")
        val apk = download(context, release) ?: return "The download didn't finish. Tap Update to try again."
        if (installQuietly(context, apk)) return "Installing build ${release.build}…"
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return "Build ${release.build} is ready to install"
    }

    /**
     * Installs through Android's own installer session without the "Do you want to install this update?"
     * question (Android 12 and later), the same way the launcher does. Android skips the question only once
     * Gibber Phone installed the version it replaces, so the first update this way may still ask once.
     */
    private suspend fun installQuietly(context: Context, apk: File): Boolean = withContext(Dispatchers.IO) {
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
        } catch (_: Exception) {
            false
        }
    }
}
