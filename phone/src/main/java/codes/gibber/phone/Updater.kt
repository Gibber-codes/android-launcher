package codes.gibber.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    private const val RELEASES = "https://api.github.com/repos/Gibber-codes/android-launcher/releases?per_page=40"

    data class Release(val build: Int, val apkUrl: String)

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(RELEASES).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            if (conn.responseCode != 200) return@withContext null
            val list = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            var best: Release? = null
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

    /** The whole Update tap. [say] shows progress; the return value is what to show at the end. */
    suspend fun run(context: Context, say: (String) -> Unit): String {
        say("Checking…")
        val release = latest() ?: return "Couldn't reach GitHub. Try again in a bit."
        if (release.build <= BuildConfig.VERSION_CODE) return "You're on the newest (build ${BuildConfig.VERSION_CODE})"
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return "Turn on Allow, come back and tap Update again"
        }
        say("Downloading build ${release.build}…")
        val apk = download(context, release) ?: return "The download didn't finish. Tap Update to try again."
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return "Build ${release.build} is ready to install"
    }
}
