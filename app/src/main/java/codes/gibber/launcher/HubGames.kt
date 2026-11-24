package codes.gibber.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = size * 0.22f
        val inset = size * 0.04f
        val box = RectF(inset, inset, size - inset, size - inset)
        c.drawRoundRect(box, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16161C.toInt() })
        c.drawRoundRect(box, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF2F2F5.toInt()
            style = Paint.Style.STROKE
            strokeWidth = size * 0.045f
        })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF2F2F5.toInt()
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val letter = app.label.firstOrNull()?.uppercase() ?: "?"
        c.drawText(letter, size / 2f, size / 2f - (text.descent() + text.ascent()) / 2, text)
        return bmp
    }
}
