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

    /** When each game was last looked for, and at which addresses (new addresses mean look again). */
    private val lastTry = mutableMapOf<String, Pair<Long, List<String>>>()

    private fun file(context: Context, id: String) = File(File(context.filesDir, "game-icons"), "$id.png")

    /** Where a game's picture came from: "chosen" by you, "found" on its page, or a "snapshot" of play. */
    private fun kindFile(context: Context, id: String) = File(File(context.filesDir, "game-icons"), "$id.kind")

    private fun kindOf(context: Context, id: String): String? =
        kindFile(context, id).takeIf { it.exists() }?.readText()?.trim()?.ifBlank { null }
            ?: if (file(context, id).exists()) "found" else null

    private suspend fun save(context: Context, id: String, bmp: Bitmap, kind: String) = withContext(Dispatchers.IO) {
        val f = file(context, id)
        f.parentFile?.mkdirs()
        f.outputStream().use { square(bmp).compress(Bitmap.CompressFormat.PNG, 100, it) }
        kindFile(context, id).writeText(kind)
        withContext(Dispatchers.Main) { version++ }
    }

    /** You picked a picture for a game; it stays until you choose another or go back to its own. */
    suspend fun choose(context: Context, id: String, uri: android.net.Uri): Boolean {
        val bmp = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 256) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            } catch (e: Exception) {
                null
            }
        } ?: return false
        save(context, id, bmp, "chosen")
        return true
    }

    fun isChosen(context: Context, id: String) = kindOf(context, id) == "chosen"

    /** Drops the picture you chose, so the game's own is looked for again. */
    suspend fun forget(context: Context, id: String) = withContext(Dispatchers.IO) {
        file(context, id).delete()
        kindFile(context, id).delete()
        lastTry.remove(id)
        withContext(Dispatchers.Main) { version++ }
    }

    /**
     * The picture of a copy of the game installed on the phone, like Dead Tide added from Chrome.
     * It beats a picture found on the web or a snapshot, and gives way only to one you chose.
     */
    suspend fun fromInstalled(context: Context, id: String, icon: android.graphics.drawable.Drawable) {
        if (kindOf(context, id) == "chosen") return
        val bmp = withContext(Dispatchers.Default) { fullBleed(icon, 256) }
        save(context, id, bmp, "app")
    }

    /** Draws an icon edge to edge, without the phone's own mask, so it can be cut like the rest. */
    private fun fullBleed(d: android.graphics.drawable.Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        if (d is android.graphics.drawable.AdaptiveIconDrawable) {
            // Adaptive icon layers are 108 units with the middle 72 shown, so draw them half again as big.
            val extra = size / 4
            listOfNotNull(d.background, d.foreground).forEach {
                it.setBounds(-extra, -extra, size + extra, size + extra)
                it.draw(c)
            }
        } else {
            d.setBounds(0, 0, size, size)
            d.draw(c)
        }
        return bmp
    }

    /** A picture from the game itself while you play, kept only when the game has no picture yet. */
    suspend fun snapshot(context: Context, id: String, shot: Bitmap) {
        if (kindOf(context, id) != null) return
        if (looksBlank(shot)) return
        save(context, id, shot, "snapshot")
    }

    /** A loading screen or a black frame: nearly every sampled pixel the same. */
    private fun looksBlank(b: Bitmap): Boolean {
        val seen = mutableSetOf<Int>()
        for (x in 1..8) for (y in 1..8) {
            val p = b.getPixel(b.width * x / 9, b.height * y / 9)
            seen += (p shr 20 and 0xF shl 8) or (p shr 12 and 0xF shl 4) or (p shr 4 and 0xF)
        }
        return seen.size < 4
    }

    fun cached(context: Context, id: String, size: Int): Bitmap? {
        val f = file(context, id)
        if (!f.exists()) return null
        val b = BitmapFactory.decodeFile(f.path) ?: return null
        return appShaped(b, size)
    }

    /**
     * A picture cut to the rounded square the phone's own app icons have, a little in from the edge
     * like theirs, so a game sits among the other apps as one of them.
     */
    fun appShaped(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        val inset = size * 0.06f
        val box = android.graphics.RectF(inset, inset, size - inset, size - inset)
        val r = box.width() * 0.3f
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        c.drawRoundRect(box, r, r, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, null, box, paint)
        return out
    }

    /**
     * Looks for each game's picture, and keeps the first that works: the icon the hub reads from the
     * game's folder on the PC ([fromHub]), then the game's pages at its addresses, in order.
     */
    suspend fun refresh(context: Context, places: Map<String, List<String>>, fromHub: Map<String, String> = emptyMap()) = withContext(Dispatchers.IO) {
        var found = false
        places.forEach { (id, bases) ->
            val f = file(context, id)
            val now = System.currentTimeMillis()
            val kind = kindOf(context, id)
            if (kind == "chosen" || kind == "app") return@forEach
            if (kind == "found" && now - f.lastModified() < 6 * 3_600_000L) return@forEach
            val last = lastTry[id]
            val tries = listOfNotNull(fromHub[id]) + bases
            if (last != null && last.second == tries && now - last.first < 10 * 60_000L) return@forEach
            lastTry[id] = now to tries
            val hubIcon = fromHub[id]?.let { u ->
                try { image(URL(u))?.let { square(it) } ?: svg(URL(u)) } catch (e: Exception) { null }
            }
            for (base in listOf("") + bases.filter { it.isNotBlank() }) {
                val bmp = if (base.isEmpty()) hubIcon ?: continue
                    else try { findIcon(base) } catch (e: Exception) { null } ?: continue
                f.parentFile?.mkdirs()
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                kindFile(context, id).writeText("found")
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

        // Pictures first; SVG icons after, drawn with the SVG library.
        for (u in tries.filterNot { isSvg(it) }) {
            val b = image(u) ?: continue
            if (b.width >= 16) return square(b)
        }
        for (u in (tries.filter { isSvg(it) } + URL(page, "/favicon.svg") + URL(page, "/icon.svg")).distinctBy { it.toString() }) {
            svg(u)?.let { return it }
        }
        // Last, the page's preview picture, cut to a square from the middle.
        val og = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }
            .firstOrNull { (attr(it, "property") ?: attr(it, "name"))?.lowercase() in setOf("og:image", "twitter:image") }
            ?.let { attr(it, "content") }
        return og?.let { image(URL(page, it)) }?.let { square(it) }
    }

    private fun isSvg(u: URL) = u.path.endsWith(".svg", ignoreCase = true) || u.toString().startsWith("data:image/svg")

    /** Draws an SVG icon at 256 pixels, or null if it isn't one or can't be drawn. */
    private fun svg(u: URL): Bitmap? = try {
        val bytes = open(u)?.inputStream?.use { read(it, 1_000_000) }
        val doc = bytes?.let { com.caverock.androidsvg.SVG.getFromString(it.toString(Charsets.UTF_8)) }
        if (doc == null) null else {
            if (doc.documentViewBox == null && doc.documentWidth > 0 && doc.documentHeight > 0) {
                doc.setDocumentViewBox(0f, 0f, doc.documentWidth, doc.documentHeight)
            }
            doc.setDocumentWidth("100%")
            doc.setDocumentHeight("100%")
            val bmp = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
            doc.renderToCanvas(android.graphics.Canvas(bmp))
            bmp
        }
    } catch (e: Throwable) {
        null
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
