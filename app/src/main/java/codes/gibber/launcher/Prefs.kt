package codes.gibber.launcher

import android.content.Context

/** Saved settings: the favorites in order, Gibber Hub's address, the Games list and the Dev site. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.getString(KEY_FAVORITES, "")!!.split("\n").filter { it.isNotBlank() }
        set(value) = sp.edit().putString(KEY_FAVORITES, value.joinToString("\n")).apply()

    var hubUrl: String
        get() = sp.getString(KEY_HUB_URL, "")!!
        set(value) = sp.edit().putString(KEY_HUB_URL, value.trim()).apply()

    /** Apps you put in Games, and games you took out of it. */
    var gamesAdded: Set<String>
        get() = sp.getStringSet(KEY_GAMES_ADDED, emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet(KEY_GAMES_ADDED, value).apply()

    var gamesRemoved: Set<String>
        get() = sp.getStringSet(KEY_GAMES_REMOVED, emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet(KEY_GAMES_REMOVED, value).apply()

    /** The site the Dev button opens. */
    var devUrl: String
        get() = sp.getString(KEY_DEV_URL, "")!!
        set(value) = sp.edit().putString(KEY_DEV_URL, value.trim()).apply()

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HUB_URL = "hub_url"
        const val KEY_GAMES_ADDED = "games_added"
        const val KEY_GAMES_REMOVED = "games_removed"
        const val KEY_DEV_URL = "dev_url"
    }
}
