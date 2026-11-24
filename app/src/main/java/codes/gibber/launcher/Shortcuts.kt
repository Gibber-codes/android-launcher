package codes.gibber.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Process
import android.os.UserManager

/**
 * Shortcuts pinned to this launcher, like a website added from Chrome with Add to home screen.
 * They show in the app list beside the apps. Android only gives a launcher the shortcuts pinned
 * to it, so ones made under another home app have to be added again here.
 */
object Shortcuts {
    private const val PREFIX = "shortcut:"

    /** The pinned shortcut behind each list entry, by its key. */
    private val byKey = mutableMapOf<String, ShortcutInfo>()

    fun isShortcut(app: AppEntry) = app.activityName.startsWith(PREFIX)

    fun load(context: Context): List<AppEntry> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        // Only the phone's chosen home app may read shortcuts.
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val users = context.getSystemService(UserManager::class.java)?.userProfiles ?: listOf(Process.myUserHandle())
        val query = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val found = users.flatMap { user ->
            try {
                launcherApps.getShortcuts(query, user).orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
        }.filter { it.isEnabled }
        synchronized(byKey) {
            byKey.clear()
            return found.map { info ->
                val entry = AppEntry(
                    label = (info.shortLabel ?: info.longLabel ?: info.`package`).toString(),
                    packageName = info.`package`,
                    activityName = PREFIX + info.id,
                )
                byKey[entry.key] = info
                entry
            }
        }
    }

    private fun info(app: AppEntry) = synchronized(byKey) { byKey[app.key] }

    fun launch(context: Context, app: AppEntry) {
        val info = info(app) ?: return
        try {
            context.getSystemService(LauncherApps::class.java)
                ?.startShortcut(info.`package`, info.id, null, null, info.userHandle)
        } catch (e: Exception) {
            // The shortcut was removed or turned off; the list refreshes on the next resume.
        }
    }

    fun iconDrawable(context: Context, app: AppEntry): android.graphics.drawable.Drawable? = try {
        info(app)?.let {
            context.getSystemService(LauncherApps::class.java)
                ?.getShortcutIconDrawable(it, context.resources.displayMetrics.densityDpi)
        }
    } catch (e: Exception) {
        null
    }

    fun loadIcon(context: Context, app: AppEntry, sizePx: Int): Bitmap? = try {
        val info = info(app)
        val d = info?.let {
            context.getSystemService(LauncherApps::class.java)
                ?.getShortcutIconDrawable(it, context.resources.displayMetrics.densityDpi)
        }
        d?.let {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            it.setBounds(0, 0, sizePx, sizePx)
            it.draw(Canvas(bmp))
            bmp
        }
    } catch (e: Exception) {
        null
    }

    /** Unpins it, so it leaves the list. */
    fun remove(context: Context, app: AppEntry) {
        val info = info(app) ?: return
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return
        try {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(info.`package`)
                .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val keep = launcherApps.getShortcuts(query, info.userHandle).orEmpty()
                .map { it.id }
                .filter { it != info.id }
            launcherApps.pinShortcuts(info.`package`, keep, info.userHandle)
        } catch (e: Exception) {
            // Nothing to undo; it stays in the list.
        }
    }
}
