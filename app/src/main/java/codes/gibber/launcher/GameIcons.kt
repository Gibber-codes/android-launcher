package codes.gibber.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Your games' own pictures, fetched from the games themselves: the icon their page names
 * (apple-touch-icon, favicon or web manifest), else the usual icon files, else the page's
 * preview image. Kept on the phone so they show even when the PC is off.
 */
object GameIcons {
    /** Bumps when a new picture arrives, so icons on screen redraw. */
    var version by mutableIntStateOf(0)
        private set

    private val lastTry = mutableMapOf<String, Long>()

    private fun file(context: Context, id: String) = File(File(context.filesDir, "game-icons"), "$id.png")

    fun cached(context: Context, id: String, size: Int): Bitmap? {
        val f = file(context, id)
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.path) ?: return null
        return Bitmap.createScaledBitmap(b, size, size, true)
    }

    /** Looks for each game's picture at its addresses, in order, and keeps the first that works. */
    suspend fun refresh(context: Context, places: Map<String, List<String>>) = withContext(Dispatchers.IO) {
        var found = false
        places.forEach { (id, bases) ->
            val f = file(context, id)
            val now = System.currentTimeMillis()
            if (f.exists() && now - f.lastModified() < 6 * 3_600_000L) return@forEach
            if (now - (lastTry[id] ?: 0L) < 10 * 60_000L) return@forEach
            lastTry[id] = now
            for (base in bases.filter { it.isNotBlank() }) {
                val bmp = try { findIcon(base) } catch (e: Exception) { null } ?: continue
                f.parentFile?.mkdirs()
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                found = true
                break
            }
        }
        if (found) withContext(Dispatchers.Main) { version++ }
    }

    private fun findIcon(base: String): Bitmap? {
        val page = URL(base)
        val html = text(page) ?: ""
        val links = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }.toList()
        fun attr(tag: String, name: String) =
            Regex("\\b$name\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
        fun sizeOf(s: String?) = s?.substringBefore('x')?.toIntOrNull() ?: 0

        val tries = mutableListOf<URL>()
        // The icons the page itself names, biggest first, apple-touch-icon ahead of the rest.
        links.mapNotNull { tag ->
            val rel = attr(tag, "rel")?.lowercase() ?: return@mapNotNull null
            if (!rel.contains("icon")) return@mapNotNull null
            val href = attr(tag, "href") ?: return@mapNotNull null
            Triple(href, rel.contains("apple"), sizeOf(attr(tag, "sizes")))
        }.sortedWith(compareByDescending<Triple<String, Boolean, Int>> { it.second }.thenByDescending { it.third })
            .forEach { tries += URL(page, it.first) }
        // The web manifest's icons.
        links.firstOrNull { attr(it, "rel")?.lowercase() == "manifest" }?.let { attr(it, "href") }?.let { href ->
            val murl = URL(page, href)
            val icons = text(murl)?.let { JSONObject(it).optJSONArray("icons") }
            if (icons != null) {
                (0 until icons.length()).mapNotNull { icons.optJSONObject(it) }
                    .sortedByDescending { sizeOf(it.optString("sizes").split(' ').first()) }
                    .forEach { tries += URL(murl, it.optString("src")) }
            }
        }
        listOf("/apple-touch-icon.png", "/icon.png", "/favicon.png", "/favicon.ico").forEach { tries += URL(page, it) }

        for (u in tries) {
            if (u.path.endsWith(".svg", ignoreCase = true)) continue // Android can't draw these without help.
            val b = image(u) ?: continue
            if (b.width >= 16) return square(b)
        }
        // Last, the page's preview picture, cut to a square from the middle.
        val og = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }
            .firstOrNull { (attr(it, "property") ?: attr(it, "name"))?.lowercase() in setOf("og:image", "twitter:image") }
            ?.let { attr(it, "content") }
        return og?.let { image(URL(page, it)) }?.let { square(it) }
    }

    private fun square(b: Bitmap): Bitmap {
        val side = minOf(b.width, b.height)
        val cut = Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
        return if (side > 256) Bitmap.createScaledBitmap(cut, 256, 256, true) else cut
    }

    private fun open(u: URL): HttpURLConnection? {
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = 4_000
        c.readTimeout = 8_000
        c.instanceFollowRedirects = true
        return if (c.responseCode in 200..299) c else null
    }

    private fun text(u: URL): String? = try {
        open(u)?.inputStream?.use { read(it, 400_000).toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    private fun image(u: URL): Bitmap? = try {
        open(u)?.inputStream?.use { s -> read(s, 3_000_000).let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    } catch (e: Exception) {
        null
    }

    /** Reads up to [max] bytes. */
    private fun read(s: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16_384)
        while (out.size() < max) {
            val n = s.read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
