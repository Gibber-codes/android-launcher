package codes.gibber.launcher

import android.content.Context

/** Gibber Hub as an entry in the app list: opening it goes to the hub page. */
object HubLink {
    private const val ACTIVITY = "gibber-hub"

    /** Set by the launcher to slide over to the hub page. */
    var open: () -> Unit = {}

    fun entry(context: Context) = AppEntry(label = "Gibber Hub", packageName = context.packageName, activityName = ACTIVITY)

    fun key(context: Context) = entry(context).key

    fun isHub(app: AppEntry) = app.activityName == ACTIVITY
}
