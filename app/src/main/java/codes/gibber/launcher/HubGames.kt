package codes.gibber.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/** The hub's games as apps of their own: on the home screen and in the app list. */
object HubGames {
    private const val PREFIX = "hub-game:"
    private const val LIVE = "hub-live:"

    /** Set by the launcher: open a game's menu. */
    var open: (String) -> Unit = {}

    /** Set by the launcher: play a game's newest Netlify version full screen. */
    var play: (String) -> Unit = {}

    /**
     * Each game twice: on the Dev page as the icon that opens its menu, and, once its Netlify site
     * is known, in the app list as an app of its own that opens straight to the newest version.
     */
    fun entries(context: Context): List<AppEntry> {
        val prefs = Prefs(context)
        return prefs.hubGames.flatMap { (id, name) ->
            listOfNotNull(
                AppEntry(label = name, packageName = context.packageName, activityName = PREFIX + id, isGame = true),
                if (prefs.liveUrl(id).isBlank()) null
                else AppEntry(label = name, packageName = context.packageName, activityName = LIVE + id, isGame = true),
            )
        }
    }

    /** Any of the hub's games, the menu icon or the live app. */
    fun isGame(app: AppEntry) = isMenu(app) || isLive(app)

    /** The Dev page icon that opens the game's menu. */
    fun isMenu(app: AppEntry) = app.activityName.startsWith(PREFIX)

    /** The app that opens the newest Netlify version. */
    fun isLive(app: AppEntry) = app.activityName.startsWith(LIVE)

    fun idOf(app: AppEntry) = app.activityName.removePrefix(PREFIX).removePrefix(LIVE)

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
