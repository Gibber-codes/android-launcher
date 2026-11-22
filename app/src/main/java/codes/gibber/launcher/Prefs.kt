package codes.gibber.launcher

import android.content.Context

/** Saved settings: the favorites, in order, and Gibber Hub's address. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.getString(KEY_FAVORITES, "")!!.split("\n").filter { it.isNotBlank() }
        set(value) = sp.edit().putString(KEY_FAVORITES, value.joinToString("\n")).apply()

    var hubUrl: String
        get() = sp.getString(KEY_HUB_URL, "")!!
        set(value) = sp.edit().putString(KEY_HUB_URL, value.trim()).apply()

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_HUB_URL = "hub_url"
    }
}
