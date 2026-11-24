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

    /** Gibber Hub has been put on the home screen once, so taking it off sticks. */
    var hubPlaced: Boolean
        get() = sp.getBoolean(KEY_HUB_PLACED, false)
        set(value) = sp.edit().putBoolean(KEY_HUB_PLACED, value).apply()

    /** Dev mode: game menus also offer the dev server on your PC. */
    var devMode: Boolean
        get() = sp.getBoolean(KEY_DEV_MODE, false)
        set(value) = sp.edit().putBoolean(KEY_DEV_MODE, value).apply()

    /** The hub's games, kept so they stay on the home screen when the PC is off: id to name. */
    var hubGames: List<Pair<String, String>>
        get() = lines(KEY_HUB_GAMES).mapNotNull { l -> l.split('\t').takeIf { it.size >= 2 }?.let { it[0] to it[1] } }
        set(value) = putLines(KEY_HUB_GAMES, value.map { "${it.first}\t${it.second}" })

    /** Hub games the hub calls something other than a game, like a project. */
    var hubProjects: Set<String>
        get() = sp.getStringSet(KEY_HUB_PROJECTS, emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet(KEY_HUB_PROJECTS, value).apply()

    /** Sections you moved apps into: app key to section name. */
    var sections: Map<String, String>
        get() = lines(KEY_SECTIONS).mapNotNull { l -> l.split('\t').takeIf { it.size >= 2 }?.let { it[0] to it[1] } }.toMap()
        set(value) = putLines(KEY_SECTIONS, value.map { "${it.key}\t${it.value}" })

    /** A game's live address, like its Netlify site. */
    fun liveUrl(game: String): String = sp.getString("game_live_$game", "")!!
    fun setLiveUrl(game: String, url: String) = sp.edit().putString("game_live_$game", url.trim()).apply()

    /** Older versions you saved for a game: name to address. */
    fun versions(game: String): List<Pair<String, String>> =
        lines("game_versions_$game").mapNotNull { l -> l.split('\t').takeIf { it.size >= 2 }?.let { it[0] to it[1] } }
    fun setVersions(game: String, list: List<Pair<String, String>>) =
        putLines("game_versions_$game", list.map { "${it.first}\t${it.second}" })

    private fun lines(key: String) = sp.getString(key, "")!!.split("\n").filter { it.isNotBlank() }
    private fun putLines(key: String, list: List<String>) = sp.edit().putString(key, list.joinToString("\n")).apply()

    private companion object {
        const val KEY_DEV_MODE = "dev_mode"
        const val KEY_HUB_GAMES = "hub_games"
        const val KEY_HUB_PROJECTS = "hub_projects"
        const val KEY_SECTIONS = "sections"
        const val KEY_HUB_PLACED = "hub_placed"
        const val KEY_FAVORITES = "favorites"
        const val KEY_HUB_URL = "hub_url"
        const val KEY_GAMES_ADDED = "games_added"
        const val KEY_GAMES_REMOVED = "games_removed"
        const val KEY_DEV_URL = "dev_url"
    }
}
