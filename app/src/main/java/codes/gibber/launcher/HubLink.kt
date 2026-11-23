package codes.gibber.launcher

import android.content.Context

/** The launcher's own entries in the app list: Gibber Hub and Gibber Settings. */
object HubLink {
    private const val HUB = "gibber-hub"
    private const val SETTINGS = "gibber-settings"

    /** Set by the launcher: slide over to the hub page. */
    var open: () -> Unit = {}

    /** Set by the launcher: open Gibber Settings. */
    var openSettings: () -> Unit = {}

    fun entry(context: Context) = AppEntry(label = "Gibber Hub", packageName = context.packageName, activityName = HUB)

    fun settingsEntry(context: Context) =
        AppEntry(label = "Gibber Settings", packageName = context.packageName, activityName = SETTINGS)

    fun key(context: Context) = entry(context).key

    fun isHub(app: AppEntry) = app.activityName == HUB

    fun isSettings(app: AppEntry) = app.activityName == SETTINGS

    /** One of the launcher's own entries, which have no Android app info page. */
    fun isOwn(app: AppEntry) = isHub(app) || isSettings(app)
}
