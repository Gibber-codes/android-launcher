package codes.gibber.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings

/** One app that shows in the app drawer. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    /** The app calls itself a game (Android's game category). */
    val isGame: Boolean = false,
) {
    /** Stable id used to save favorites. */
    val key: String get() = "$packageName/$activityName"

    /** The letter it sorts under in the alphabet strip. */
    val letter: Char
        get() {
            val c = label.firstOrNull()?.uppercaseChar() ?: '#'
            return if (c in 'A'..'Z') c else '#'
        }
}

object Apps {
    /** Every app with an icon in the drawer, A to Z, without this launcher itself. */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    activityName = it.activityInfo.name,
                    isGame = isGame(it.activityInfo.applicationInfo),
                )
            }
            .distinctBy { it.key }
            .toList()
            .plus(Shortcuts.load(context))
            .plus(HubLink.entry(context))
            .plus(HubLink.settingsEntry(context))
            .sortedBy { it.label.lowercase() }
    }

    fun loadIcon(context: Context, app: AppEntry, sizePx: Int): Bitmap? =
        when {
            HubLink.isOwn(app) -> try {
                context.packageManager.getApplicationIcon(context.packageName).toBitmap(sizePx)
            } catch (e: Exception) {
                null
            }
            Shortcuts.isShortcut(app) -> Shortcuts.loadIcon(context, app, sizePx)
            else -> appIcon(context, app, sizePx)
        }

    private fun appIcon(context: Context, app: AppEntry, sizePx: Int): Bitmap? = try {
        val d = context.packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))
        d.toBitmap(sizePx)
    } catch (e: Exception) {
        null
    }

    fun launch(context: Context, app: AppEntry) {
        if (HubLink.isHub(app)) return HubLink.open()
        if (HubLink.isSettings(app)) return HubLink.openSettings()
        if (Shortcuts.isShortcut(app)) return Shortcuts.launch(context, app)
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(app.packageName, app.activityName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // The app was removed or can't start; the list refreshes on the next resume.
        }
    }

    // Older games mark themselves with a flag instead of the category.
    @Suppress("DEPRECATION")
    private fun isGame(info: ApplicationInfo): Boolean =
        info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0

    /** The Games list: apps that call themselves games, plus any you added, minus any you took off. */
    fun games(apps: List<AppEntry>, added: Set<String>, removed: Set<String>): List<AppEntry> =
        apps.filter { (it.isGame || it.key in added) && it.key !in removed }

    fun openInfo(context: Context, app: AppEntry) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return Bitmap.createScaledBitmap(bitmap, size, size, true)
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(canvas)
        return bmp
    }
}
