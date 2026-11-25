package codes.gibber.launcher

import android.content.Context

/** The launcher's own apps in the app list: Gibber Hub, Gibber Settings and Ideas. */
object HubLink {
    private const val HUB = "gibber-hub"
    private const val SETTINGS = "gibber-settings"
    private const val IDEAS = "gibber-ideas"

    /** Set by the launcher: open Gibber Hub full screen. */
    var open: () -> Unit = {}

    /** Set by the launcher: open Gibber Settings. */
    var openSettings: () -> Unit = {}

    /** Set by the launcher: open Ideas. */
    var openIdeas: () -> Unit = {}

    fun entry(context: Context) = AppEntry(label = "Gibber Hub", packageName = context.packageName, activityName = HUB)

    fun settingsEntry(context: Context) =
        AppEntry(label = "Gibber Settings", packageName = context.packageName, activityName = SETTINGS)

    fun ideasEntry(context: Context) = AppEntry(label = "Ideas", packageName = context.packageName, activityName = IDEAS)

    /** All three, for the app list. */
    fun entries(context: Context) = listOf(entry(context), settingsEntry(context), ideasEntry(context))

    fun key(context: Context) = entry(context).key

    fun isHub(app: AppEntry) = app.activityName == HUB

    fun isSettings(app: AppEntry) = app.activityName == SETTINGS

    fun isIdeas(app: AppEntry) = app.activityName == IDEAS

    /** One of the launcher's own entries, which have no Android app info page. */
    fun isOwn(app: AppEntry) = isHub(app) || isSettings(app) || isIdeas(app) || HubGames.isGame(app)
}
