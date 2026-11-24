package codes.gibber.phone.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/** One group of contacts Claude made, like Family, by the keys of its numbers. */
data class ContactGroup(val name: String, val keys: Set<String>)

/** What Claude wrote on the PC after reading your contacts and texts. */
data class Sorted(
    val groups: List<ContactGroup> = emptyList(),
    /** Spam numbers by key, with Claude's reason. */
    val spam: Map<String, String> = emptyMap(),
    /** Group text names by the group's key (its members' keys, sorted). */
    val names: Map<String, String> = emptyMap(),
)

/**
 * Sends your contacts and recent texts to Gibber Hub on your PC over the home Wi-Fi, where you ask Claude
 * to sort them, and reads back what Claude wrote: groups, spam and group text names. Nothing leaves the
 * phone until you tap Send, and it only ever goes to the hub address you typed.
 */
object Sorting {
    private const val FILE = "hub"
    private const val CONVERSATIONS = 300
    private const val MESSAGES = 12
    private const val TEXT = 280
    // The hub takes 64 KB a write; this leaves room for the part's own keys.
    private const val PART_BYTES = 56_000

    @Volatile private var cached: Sorted? = null

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun address(context: Context): String = prefs(context).getString("address", "")!!

    /** Saves the hub's address as typed, adding http:// and the hub's port when they're left off. */
    fun setAddress(context: Context, typed: String) {
        var a = typed.trim().trimEnd('/')
        if (a.isNotEmpty() && !a.contains("://")) a = "http://$a"
        if (a.isNotEmpty() && Uri.parse(a).port < 0) a = "$a:5175"
        prefs(context).edit().putString("address", a).apply()
    }

    fun sentAt(context: Context): Long = prefs(context).getLong("sent_at", 0)
    fun fetchedAt(context: Context): Long = prefs(context).getLong("fetched_at", 0)

    fun sorted(context: Context): Sorted {
        cached?.let { return it }
        val made = prefs(context).getString("sorted", null)?.let { parse(it) } ?: Sorted()
        cached = made
        return made
    }

    /** Claude's reason when this one-person conversation is spam. Never for a saved contact or a group. */
    fun spamWhy(context: Context, addresses: List<String>, contactId: Long?): String? {
        if (addresses.size != 1 || contactId != null) return null
        return sorted(context).spam[Numbers.key(addresses[0])]
    }

    fun groupName(context: Context, addresses: Collection<String>): String? =
        if (addresses.size < 2) null else sorted(context).names[groupKey(addresses)]

    fun groupsOf(context: Context, person: Person): List<String> {
        val keys = person.numbers.map { Numbers.key(it.number) }.toSet()
        return sorted(context).groups.filter { g -> keys.any { it in g.keys } }.map { it.name }
    }

    private fun groupKey(numbers: Collection<String>) =
        numbers.map { Numbers.key(it) }.filter { it.isNotEmpty() }.sorted().joinToString(",")

    private fun parse(text: String): Sorted? = try {
        val o = JSONObject(text)
        fun strings(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }
        val groups = o.optJSONArray("groups")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.map { g ->
                ContactGroup(g.getString("name"), strings(g.optJSONArray("numbers")).map { Numbers.key(it) }.toSet())
            }
        } ?: emptyList()
        val spam = o.optJSONArray("spam")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.associate { s ->
                Numbers.key(s.getString("number")) to (s.optString("why").takeIf { it.isNotBlank() && it != "null" } ?: "Claude marked it spam")
            }
        } ?: emptyMap()
        val names = o.optJSONArray("groupNames")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.associate { g -> groupKey(strings(g.optJSONArray("numbers"))) to g.getString("name") }
        } ?: emptyMap()
        Sorted(groups, spam - "", names)
    } catch (_: Exception) {
        null
    }

    private class Answer(val code: Int, val body: String)

    private fun call(base: String, post: JSONObject? = null): Answer {
        val conn = URL("$base/api/people").openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/json")
        if (post != null) {
            conn.requestMethod = "POST"
            conn.doOutput = true
            // The hub takes a write only from its own address, as JSON.
            conn.setRequestProperty("Origin", base)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(post.toString().toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        return Answer(code, stream?.bufferedReader()?.use { it.readText() } ?: "")
    }

    private fun base(context: Context): String? =
        address(context).takeIf { it.isNotBlank() }?.let { Uri.parse(it) }?.let { "${it.scheme ?: "http"}://${it.encodedAuthority}" }

    private fun trouble(a: Answer): String = when (a.code) {
        405, 404 -> "Gibber Hub on your PC doesn't take People's sends yet. Pull the newest gibber-hub there."
        403 -> "Gibber Hub turned it away. Check the address is the one the launcher uses."
        else -> "Gibber Hub said: " + (a.body.trim().take(160).ifBlank { "error ${a.code}" })
    }

    /** Sends your contacts and your newest texts. [say] shows progress; the return value is what to show at the end. */
    suspend fun send(context: Context, say: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val base = base(context) ?: return@withContext "Set Gibber Hub's address first."
        try {
            say("Reading contacts…")
            val contacts = Contacts.everyone(context).map { p ->
                JSONObject().put("name", p.name.take(120)).put("starred", p.starred)
                    .put("numbers", JSONArray(p.numbers.map { it.number.take(40) }.filter { it.isNotBlank() }.take(10)))
            }.filter { it.getJSONArray("numbers").length() > 0 }
            val conversations = Texts.conversations(context).take(CONVERSATIONS)
            val texts = conversations.mapIndexed { i, c ->
                if (i % 20 == 0) say("Reading texts… ${i + 1} of ${conversations.size}")
                val messages = Texts.messages(context, c.id, MESSAGES).map { m ->
                    val body = m.body.ifBlank { if (m.images.isNotEmpty()) "(picture)" else "" }
                    JSONObject().put("from", if (m.outgoing) "me" else Numbers.clean(m.from ?: "unknown").take(40).ifBlank { "unknown" })
                        .put("at", Instant.ofEpochMilli(m.date).toString()).put("text", body.take(TEXT))
                }
                JSONObject().put("thread", c.id.toString()).put("title", c.title?.take(120) ?: JSONObject.NULL)
                    .put("members", JSONArray(c.addresses.map { Numbers.clean(it).take(40) }.filter { it.isNotBlank() }.take(60)))
                    .put("messages", JSONArray(messages))
            }.filter { it.getJSONArray("members").length() > 0 }

            val counts = HashMap<String, Int>()
            for ((kind, items) in listOf("contacts" to contacts, "texts" to texts)) {
                val parts = split(items)
                parts.forEachIndexed { i, part ->
                    say("Sending $kind… ${i + 1} of ${parts.size}")
                    val a = call(base, JSONObject().put("kind", kind).put("part", i + 1).put("items", JSONArray(part)))
                    if (a.code !in 200..299) return@withContext trouble(a)
                }
                counts[kind] = parts.size
            }
            val done = call(base, JSONObject().put("kind", "done").put("contacts", counts["contacts"] ?: 0).put("texts", counts["texts"] ?: 0))
            if (done.code !in 200..299) return@withContext trouble(done)
            prefs(context).edit().putLong("sent_at", System.currentTimeMillis()).apply()
            "Sent ${contacts.size} contacts and ${texts.size} conversations"
        } catch (_: java.io.IOException) {
            "Couldn't reach Gibber Hub. Check your PC is on and on the same Wi-Fi."
        } catch (_: SecurityException) {
            "People needs access to contacts and texts first."
        }
    }

    private fun split(items: List<JSONObject>): List<List<JSONObject>> =
        partsBySize(items.map { it.toString().toByteArray().size + 1 }, PART_BYTES).map { part -> part.map { items[it] } }

    /** Which items go in each part, in order, so no part passes [max] bytes (an item bigger than that goes alone). */
    internal fun partsBySize(sizes: List<Int>, max: Int): List<List<Int>> {
        val parts = ArrayList<List<Int>>()
        var part = ArrayList<Int>()
        var bytes = 0
        sizes.forEachIndexed { i, size ->
            if (part.isNotEmpty() && bytes + size > max) {
                parts.add(part)
                part = ArrayList()
                bytes = 0
            }
            part.add(i)
            bytes += size
        }
        if (part.isNotEmpty()) parts.add(part)
        return parts
    }

    /** Reads what Claude wrote. The return value is what to show. */
    suspend fun fetch(context: Context): String = withContext(Dispatchers.IO) {
        val base = base(context) ?: return@withContext "Set Gibber Hub's address first."
        try {
            val a = call(base)
            if (a.code !in 200..299) return@withContext trouble(a)
            val sorted = JSONObject(a.body).opt("sorted")
            if (sorted !is JSONObject) return@withContext "Claude hasn't sorted them yet."
            val made = parse(sorted.toString()) ?: return@withContext "Claude's sorting didn't read right."
            prefs(context).edit().putString("sorted", sorted.toString()).putLong("fetched_at", System.currentTimeMillis()).apply()
            cached = made
            summary(made)
        } catch (_: java.io.IOException) {
            "Couldn't reach Gibber Hub. Check your PC is on and on the same Wi-Fi."
        } catch (_: org.json.JSONException) {
            "Gibber Hub's answer didn't read right."
        }
    }

    fun summary(s: Sorted): String =
        "${s.groups.size} groups, ${s.spam.size} spam, ${s.names.size} group names"

    /** A quiet check now and then, so Claude's newest sorting shows without a tap. */
    suspend fun refreshIfDue(context: Context) {
        if (address(context).isBlank() || sentAt(context) == 0L) return
        // Away from home the hub can't be reached, so a failed try waits as long as a good one.
        val last = prefs(context).getLong("tried_at", 0)
        if (System.currentTimeMillis() - last < 30 * 60_000) return
        prefs(context).edit().putLong("tried_at", System.currentTimeMillis()).apply()
        fetch(context)
    }

    fun forget(context: Context) {
        prefs(context).edit().remove("sorted").remove("fetched_at").apply()
        cached = Sorted()
    }
}
