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
    /** Android's category for the app (ApplicationInfo.category), or -1. */
    val category: Int = -1,
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
                    category = it.activityInfo.applicationInfo.category,
                )
            }
            .distinctBy { it.key }
            .toList()
            .plus(Shortcuts.load(context))
            .plus(HubGames.entries(context))
            .sortedBy { it.label.lowercase() }
    }

    fun loadIcon(context: Context, app: AppEntry, sizePx: Int): Bitmap? =
        when {
            HubGames.isGame(app) -> GameIcons.cached(context, HubGames.idOf(app), sizePx) ?: HubGames.tile(app, sizePx)
            HubLink.isOwn(app) -> try {
                context.packageManager.getApplicationIcon(context.packageName).toBitmap(sizePx)
            } catch (e: Exception) {
                null
            }
            Shortcuts.isShortcut(app) -> Shortcuts.loadIcon(context, app, sizePx)
            else -> appIcon(context, app, sizePx)
        }

    /** The icon of an app or pinned shortcut as Android gives it, not yet drawn. */
    fun iconDrawable(context: Context, app: AppEntry): Drawable? =
        if (Shortcuts.isShortcut(app)) Shortcuts.iconDrawable(context, app)
        else try {
            context.packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))
        } catch (e: Exception) {
            null
        }

    /** The address a web app installed from Chrome opens, when it is one. */
    fun webAppAddress(context: Context, app: AppEntry): String? = try {
        if (Shortcuts.isShortcut(app)) null
        else context.packageManager
            .getApplicationInfo(app.packageName, android.content.pm.PackageManager.GET_META_DATA)
            .metaData?.getString("org.chromium.webapk.shell_apk.startUrl")
            ?.takeIf { it.startsWith("https://") }
    } catch (e: Exception) {
        null
    }

    private fun appIcon(context: Context, app: AppEntry, sizePx: Int): Bitmap? = try {
        val d = context.packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName))
        d.toBitmap(sizePx)
    } catch (e: Exception) {
        null
    }

    fun launch(context: Context, app: AppEntry) {
        if (HubGames.isLive(app)) return HubGames.play(HubGames.idOf(app))
        if (HubGames.isGame(app)) return HubGames.open(HubGames.idOf(app))
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

    /** An app you put on the phone, or an update to one it came with; not this launcher's own entries. */
    fun canUninstall(context: Context, app: AppEntry): Boolean {
        if (HubLink.isOwn(app) || Shortcuts.isShortcut(app)) return false
        return try {
            val info = context.packageManager.getApplicationInfo(app.packageName, 0)
            val system = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            !system || (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (e: Exception) {
            false
        }
    }

    /** Android's own "Do you want to uninstall this app?"; the list updates when you come back. */
    @Suppress("DEPRECATION")
    fun uninstall(context: Context, app: AppEntry) {
        val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            openInfo(context, app)
        }
    }

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
