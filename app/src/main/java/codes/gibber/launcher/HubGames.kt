package codes.gibber.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/** The hub's games as apps of their own: on the home screen and in the app list. */
object HubGames {
    private const val PREFIX = "hub-game:"

    /** Set by the launcher: open a game's menu. */
    var open: (String) -> Unit = {}

    fun entries(context: Context): List<AppEntry> =
        Prefs(context).hubGames.map { (id, name) ->
            AppEntry(label = name, packageName = context.packageName, activityName = PREFIX + id, isGame = true)
        }

    fun isGame(app: AppEntry) = app.activityName.startsWith(PREFIX)

    fun idOf(app: AppEntry) = app.activityName.removePrefix(PREFIX)

    /** A plain icon: the game's first letter in white on a dark tile with a white line round it. */
    fun tile(app: AppEntry, size: Int): Bitmap {
        // Until its picture is found, a game gets a plain coloured icon with its first letter,
        // shaped like the other apps' icons rather than a boxed tile.
        val colours = intArrayOf(0xFF3B5BDB.toInt(), 0xFFC2255C.toInt(), 0xFF2B8A3E.toInt(), 0xFFE8590C.toInt(), 0xFF7048E8.toInt(), 0xFF0B7285.toInt())
        val face = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(face)
        c.drawColor(colours[Math.floorMod(app.label.hashCode(), colours.size)])
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size * 0.46f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val letter = app.label.firstOrNull()?.uppercase() ?: "?"
        c.drawText(letter, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2, text)
        return GameIcons.appShaped(face, size)
    }
}
