package codes.gibber.phone.data

import android.content.Context

/**
 * Names you give group chats. Samsung keeps its group names inside its own Messages app, where no other app
 * can read them, so Gibber Phone keeps its own. A group is known by who is in it, so the name sticks.
 */
object GroupNames {
    private const val FILE = "group_names"

    private fun keyOf(addresses: Collection<String>): String =
        addresses.map { Numbers.key(it) }.filter { it.isNotEmpty() }.sorted().joinToString(",")

    fun get(context: Context, addresses: Collection<String>): String? {
        if (addresses.size < 2) return null
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(keyOf(addresses), null)?.takeIf { it.isNotBlank() }
    }

    fun set(context: Context, addresses: Collection<String>, name: String) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (name.isBlank()) prefs.remove(keyOf(addresses)) else prefs.putString(keyOf(addresses), name.trim())
        prefs.apply()
    }

    /** The group's name (yours first, then the phone's own), or its people's first names. */
    fun title(context: Context, addresses: List<String>, phoneName: String? = null): String =
        get(context, addresses) ?: phoneName ?: addresses.joinToString(", ") { a ->
            Contacts.lookup(context, a)?.name?.substringBefore(' ') ?: Numbers.pretty(a)
        }
}
