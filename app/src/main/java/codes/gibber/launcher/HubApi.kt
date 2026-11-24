package codes.gibber.launcher

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One item a workspace shows inside the hub, like its game or its style guide. */
data class HubNav(val id: String, val name: String, val path: String)

/** A game or project the hub can start, stop and open. */
data class HubWorkspace(
    val id: String,
    val name: String,
    val port: Int,
    val startable: Boolean,
    /** up, starting, down, or other (something else has its port). */
    val state: String,
    val nav: List<HubNav>,
    /** The hub's own grouping, like games, or blank. */
    val group: String = "",
)

data class HubLimit(val used: Int, val resets: String)

data class HubPace(val state: String, val unused: Int, val runsOut: String?)

data class HubAccount(
    val name: String,
    val plan: String?,
    val error: String?,
    val session: HubLimit?,
    val week: HubLimit?,
    val pace: HubPace?,
)

data class HubInboxItem(val id: String, val kind: String, val state: String, val note: String?)

/** What the hub on the PC answers, read the same way its own page reads it. */
class HubApi(hubUrl: String) {
    private val base: String = Uri.parse(hubUrl).let { "${it.scheme ?: "http"}://${it.encodedAuthority}" }

    class Answer(val code: Int, val body: String)

    private suspend fun call(path: String, post: JSONObject? = null): Answer? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(base + path).openConnection() as HttpURLConnection
            conn.connectTimeout = 4_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            if (post != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                // The hub takes a write only from its own page: its own address as Origin, and JSON.
                conn.setRequestProperty("Origin", base)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(post.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            Answer(code, body)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun json(path: String): Any? {
        val a = call(path) ?: return null
        if (a.code !in 200..299) return null
        return try {
            val t = a.body.trim()
            when {
                t.startsWith("[") -> JSONArray(t)
                t.startsWith("{") -> JSONObject(t)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** The hub's name, or null when the PC can't be reached. */
    suspend fun name(): String? = (json("/api/name") as? JSONObject)?.optString("name")?.ifBlank { null }

    suspend fun workspaces(): List<HubWorkspace>? {
        val arr = json("/api/workspaces") as? JSONArray ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            val w = arr.optJSONObject(i) ?: return@mapNotNull null
            val navArr = w.optJSONArray("nav")
            val nav = if (navArr == null) emptyList() else (0 until navArr.length()).mapNotNull { j ->
                navArr.optJSONObject(j)?.let { HubNav(it.optString("id"), it.optString("name"), it.optString("path", "/")) }
            }
            HubWorkspace(
                id = w.optString("id"),
                name = w.optString("name"),
                port = w.optInt("port"),
                startable = w.optBoolean("startable"),
                state = w.optString("state", "down"),
                nav = nav,
                group = w.optString("group"),
            )
        }
    }

    private fun limitOf(o: JSONObject?): HubLimit? =
        o?.let { HubLimit(it.optDouble("used", 0.0).toInt(), it.optString("resets")) }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    /** Both Claude accounts' limits, or null when the hub has none (or isn't Gibber Hub). */
    suspend fun usage(): List<HubAccount>? {
        val o = json("/api/usage") as? JSONObject ?: return null
        val arr = o.optJSONArray("accounts") ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i) ?: return@mapNotNull null
            val pace = a.optJSONObject("pace")?.let {
                HubPace(it.optString("state"), it.optInt("unused"), it.str("runsOut"))
            }
            HubAccount(
                name = a.optString("name"),
                plan = a.str("plan"),
                error = a.str("error"),
                session = limitOf(a.optJSONObject("session")),
                week = limitOf(a.optJSONObject("week")),
                pace = pace,
            )
        }
    }

    suspend fun inbox(): List<HubInboxItem>? {
        val arr = json("/api/inbox") as? JSONArray ?: return null
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { HubInboxItem(it.optString("id"), it.optString("kind"), it.optString("state"), it.str("note")) }
        }
    }

    /** Starts or stops a workspace. Null when it worked, or what the hub said went wrong. */
    suspend fun press(what: String, id: String): String? {
        val a = call("/api/workspaces/$what", JSONObject().put("id", id)) ?: return "Couldn't reach your PC."
        if (a.code in 200..299) return null
        return try {
            JSONObject(a.body).optString("error").ifBlank { null }
        } catch (e: Exception) {
            null
        } ?: "The hub said no (error ${a.code})."
    }

    /** A workspace's page as the phone reaches it: the PC's address, its own port. */
    fun pageOf(w: HubWorkspace, path: String = "/"): String =
        "${Uri.parse(base).scheme}://${Uri.parse(base).host}:${w.port}${if (path.startsWith("/")) path else "/$path"}"
}
