package codes.gibber.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Collections

/**
 * The owner's Spotify playlists, read with their own Spotify app's Client ID. Sign-in is PKCE with
 * no secret; Spotify sends the browser back to a loopback address, which the launcher listens on
 * for the few minutes a sign-in takes. Tokens stay in the "spotify" prefs and never reach a log.
 */
object Spotify {
    const val REDIRECT = "http://127.0.0.1:43819/callback"
    private const val PORT = 43819
    private const val SCOPE = "playlist-read-private playlist-read-collaborative"
    private const val APP = "com.spotify.music"
    private const val WAIT_MS = 5 * 60_000

    data class Playlist(val name: String, val image: String?, val uri: String, val web: String)

    sealed class Lists {
        class Ok(val playlists: List<Playlist>) : Lists()
        object SignedOut : Lists()
        object Refused : Lists()
        object Failed : Lists()
    }

    /** Bumped on every sign-in and sign-out, so the panel reads the prefs again. */
    var changes by mutableIntStateOf(0)
    /** True while the loopback listener waits for Spotify to send the browser back. */
    var waiting by mutableStateOf(false)
    /** A one-line note about the last sign-in, or null. */
    var note by mutableStateOf<String?>(null)

    // The app's own scope: the browser takes the screen while sign-in waits, so it must outlive the panel.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var server: ServerSocket? = null
    private var attempt = 0

    private fun sp(context: Context) = context.getSharedPreferences("spotify", Context.MODE_PRIVATE)

    fun clientId(context: Context): String = sp(context).getString("client_id", "")!!

    fun saveClientId(context: Context, id: String) {
        sp(context).edit().putString("client_id", id.trim()).apply()
        changes++
    }

    fun signedIn(context: Context): Boolean = sp(context).getString("refresh", null) != null

    /** Clears the tokens and keeps the Client ID. */
    fun signOut(context: Context) {
        sp(context).edit().remove("access").remove("refresh").remove("expires_at").apply()
        note = null
        changes++
    }

    /** Opens Spotify's sign-in in the browser and waits on the loopback address for its answer. */
    fun signIn(context: Context) {
        val app = context.applicationContext
        val id = clientId(app)
        if (id.isBlank()) return
        val mine = ++attempt
        // A second tap starts over: the old listener lets go of the port first.
        try { server?.close() } catch (_: Exception) { }
        note = null
        waiting = true
        scope.launch {
            val result = try { listen(app, id, mine) } catch (_: Exception) { "Spotify sign-in didn't finish. Try again." }
            if (mine == attempt) {
                waiting = false
                note = result
                changes++
            }
        }
    }

    /** Null when signed in, else what to tell the owner. */
    private suspend fun listen(context: Context, id: String, mine: Int): String? {
        val verifier = random(64)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val state = random(16)
        val socket = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT))
            }
        } catch (_: Exception) {
            return "Couldn't wait for Spotify's answer on this phone. Try again."
        }
        server = socket
        val authorize = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", id)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .build()
        val opened = withContext(Dispatchers.Main) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, authorize).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: Exception) { false }
        }
        if (!opened) {
            socket.close()
            return "No browser to sign in to Spotify with."
        }
        val query = socket.use { waitForCallback(it) } ?: return if (mine == attempt) "Spotify sign-in timed out. Try again." else null
        if (query.getQueryParameter("state") != state) return "Spotify's answer didn't match this sign-in. Try again."
        query.getQueryParameter("error")?.let { return if (it == "access_denied") "Spotify sign-in was cancelled." else "Spotify refused the sign-in." }
        val code = query.getQueryParameter("code") ?: return "Spotify sign-in didn't finish. Try again."
        val reply = post(
            mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT,
                "client_id" to id,
                "code_verifier" to verifier,
            )
        ) ?: return "Couldn't reach Spotify. Try again."
        if (reply.code !in 200..299 || !keep(context, reply.body)) {
            return "Spotify didn't finish the sign-in. Check the Client ID, and that the redirect URI is exactly $REDIRECT."
        }
        return null
    }

    /**
     * Answers the browser until it asks for /callback, then gives its query. Other requests (a
     * favicon, a connection opened early and left idle) are let go without ending the wait.
     */
    private fun waitForCallback(socket: ServerSocket): Uri? {
        val until = System.currentTimeMillis() + WAIT_MS
        while (true) {
            val left = until - System.currentTimeMillis()
            if (left <= 0) return null
            socket.soTimeout = left.toInt()
            val client = try { socket.accept() } catch (_: Exception) { return null }
            val query = try {
                client.use { c ->
                    c.soTimeout = 5_000
                    val reader = c.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val line = reader.readLine() ?: return@use null
                    // Read the rest of the request, or closing early can reset the page before it shows.
                    while (true) { val h = reader.readLine(); if (h.isNullOrEmpty()) break }
                    val path = line.split(' ').getOrNull(1).orEmpty()
                    val callback = path.startsWith("/callback")
                    val page = if (callback) "Signed in to Spotify. You can go back to the launcher." else "Not found."
                    val html = "<!doctype html><meta name=viewport content=\"width=device-width\"><body style=\"font:16px sans-serif;background:#0a030a;color:#fff;padding:24px\">$page</body>"
                    val body = html.toByteArray(Charsets.UTF_8)
                    val head = "HTTP/1.1 ${if (callback) "200 OK" else "404 Not Found"}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    c.getOutputStream().apply { write(head.toByteArray(Charsets.ISO_8859_1)); write(body); flush() }
                    if (callback) Uri.parse("http://127.0.0.1$path") else null
                }
            } catch (_: Exception) { null }
            if (query != null) return query
        }
    }

    /** Keeps the tokens from a token answer; a refresh that sends no new refresh token keeps the old one. */
    private fun keep(context: Context, body: String): Boolean = try {
        val j = JSONObject(body)
        val access = j.getString("access_token")
        val edit = sp(context).edit()
            .putString("access", access)
            .putLong("expires_at", System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000)
        j.optString("refresh_token").takeIf { it.isNotBlank() }?.let { edit.putString("refresh", it) }
        edit.apply()
        true
    } catch (_: Exception) { false }

    private sealed class Token {
        class Ok(val access: String) : Token()
        object Dead : Token()
        object Offline : Token()
    }

    private fun token(context: Context, fresh: Boolean): Token {
        val p = sp(context)
        val access = p.getString("access", null)
        if (!fresh && access != null && p.getLong("expires_at", 0) - 60_000 > System.currentTimeMillis()) return Token.Ok(access)
        val refresh = p.getString("refresh", null) ?: return Token.Dead
        val reply = post(mapOf("grant_type" to "refresh_token", "refresh_token" to refresh, "client_id" to clientId(context)))
            ?: return Token.Offline
        if (reply.code in 200..299 && keep(context, reply.body)) {
            return p.getString("access", null)?.let { Token.Ok(it) } ?: Token.Dead
        }
        val error = try { JSONObject(reply.body).optString("error") } catch (_: Exception) { "" }
        // A refresh token Spotify no longer takes (revoked, or past its six months): sign in again.
        if (error == "invalid_grant") {
            p.edit().remove("access").remove("refresh").remove("expires_at").apply()
            return Token.Dead
        }
        return Token.Offline
    }

    /** The owner's playlists, up to 50; image links last about a day, so they are never saved. */
    suspend fun playlists(context: Context): Lists = withContext(Dispatchers.IO) {
        var reply: Reply? = null
        for (fresh in listOf(false, true)) {
            val access = when (val t = token(context, fresh)) {
                is Token.Ok -> t.access
                Token.Dead -> return@withContext Lists.SignedOut
                Token.Offline -> return@withContext Lists.Failed
            }
            reply = get("https://api.spotify.com/v1/me/playlists?limit=50", access) ?: return@withContext Lists.Failed
            if (reply.code != 401) break
        }
        val r = reply ?: return@withContext Lists.Failed
        when {
            r.code == 403 -> Lists.Refused
            r.code !in 200..299 -> Lists.Failed
            else -> try { Lists.Ok(parse(r.body)) } catch (_: Exception) { Lists.Failed }
        }
    }

    private fun parse(body: String): List<Playlist> {
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri").takeIf { it.startsWith("spotify:") } ?: return@mapNotNull null
            val images = o.optJSONArray("images")
            val all = if (images == null) emptyList() else (0 until images.length()).mapNotNull { images.optJSONObject(it) }
            // The smallest picture still sharp at a tile's size; Spotify lists them largest first.
            val pick = all.filter { it.optInt("width", 0) >= 150 }.minByOrNull { it.optInt("width") } ?: all.firstOrNull()
            Playlist(
                name = o.optString("name"),
                image = pick?.optString("url")?.takeIf { it.isNotBlank() },
                uri = uri,
                web = o.optJSONObject("external_urls")?.optString("spotify").orEmpty(),
            )
        }
    }

    /** Opens a Spotify link in the Spotify app, else its web page. */
    fun open(context: Context, uri: String, web: String) {
        val tries = listOfNotNull(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(APP),
            web.takeIf { it.isNotBlank() }?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) },
        )
        for (intent in tries) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) { }
        }
    }

    private val covers = LruCache<String, Bitmap>(64)
    private val noCover: MutableSet<String> = Collections.synchronizedSet(HashSet())

    /** A playlist's cover, loaded off the main thread and kept in memory only. */
    suspend fun cover(context: Context, url: String): Bitmap? {
        covers.get(url)?.let { return it }
        if (url in noCover) return null
        val bitmap = withContext(Dispatchers.IO) { try { NowPlaying.loadCover(context, url, max = 160) } catch (_: Throwable) { null } }
        if (bitmap != null) covers.put(url, bitmap) else {
            if (noCover.size > 100) noCover.clear()
            noCover.add(url)
        }
        return bitmap
    }

    fun cached(url: String?): Bitmap? = url?.let { covers.get(it) }

    private class Reply(val code: Int, val body: String)

    private fun post(form: Map<String, String>): Reply? = call("https://accounts.spotify.com/api/token") {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = form.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }

    private fun get(url: String, access: String): Reply? = call(url) {
        setRequestProperty("Authorization", "Bearer $access")
    }

    // Any failure is just null: an exception here could carry a token into a log.
    private fun call(url: String, setup: HttpURLConnection.() -> Unit): Reply? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/json")
        c.setup()
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        Reply(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty()).also { c.disconnect() }
    } catch (_: Exception) {
        null
    }

    private fun random(bytes: Int) = b64(ByteArray(bytes).also { SecureRandom().nextBytes(it) })
    private fun b64(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
}

/**
 * Your playlists on the music screen, two to a row like Spotify's own home: Liked Songs first,
 * then the rest. Until Spotify is connected it asks for the Client ID and offers Connect Spotify.
 */
@Composable
fun PlaylistsPanel() {
    val context = LocalContext.current
    val changes = Spotify.changes
    val clientId = remember(changes) { Spotify.clientId(context) }
    val signedIn = remember(changes) { Spotify.signedIn(context) }
    var lists by remember { mutableStateOf<Spotify.Lists?>(null) }
    LaunchedEffect(changes) {
        lists = null
        if (signedIn) lists = Spotify.playlists(context).also { if (it is Spotify.Lists.SignedOut) Spotify.changes++ }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Head("Your playlists")
        if (!signedIn) {
            var typed by remember { mutableStateOf("") }
            if (clientId.isBlank()) {
                Text("Connect your Spotify account to show your playlists here.", color = Dark.W62, fontSize = 13.sp)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text("Paste your Spotify app's Client ID", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FrostButton(
                if (Spotify.waiting) "Waiting for Spotify… tap to start over" else "Connect Spotify",
                Modifier.fillMaxWidth(),
                hi = true,
                height = 40.dp,
            ) {
                if (clientId.isBlank()) {
                    if (typed.isBlank()) return@FrostButton
                    Spotify.saveClientId(context, typed)
                }
                Spotify.signIn(context)
            }
            Spotify.note?.let { Text(it, color = Dark.W62, fontSize = 12.sp) }
            // A mistyped Client ID would otherwise leave no way to paste the right one.
            if (clientId.isNotBlank()) SmallAction("Change Client ID") { Spotify.saveClientId(context, "") }
        } else {
            Grid(lists)
            SmallAction("Sign out of Spotify") { Spotify.signOut(context) }
        }
    }
}

@Composable
private fun Grid(l: Spotify.Lists?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val playlists = (l as? Spotify.Lists.Ok)?.playlists.orEmpty()
        val tiles = listOf(Spotify.Playlist("Liked Songs", null, "spotify:collection:tracks", "https://open.spotify.com/collection/tracks")) + playlists
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { p -> PlaylistTile(p, liked = p.uri == "spotify:collection:tracks", Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        when (l) {
            null -> Text("Reading your playlists…", color = Dark.W40, fontSize = 12.sp)
            Spotify.Lists.Refused -> Text("Spotify refused: add your account under Users Management in your Spotify app's settings", color = Dark.W62, fontSize = 12.sp)
            Spotify.Lists.Failed -> Text("Couldn't read your playlists from Spotify.", color = Dark.W62, fontSize = 12.sp)
            else -> {}
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Text(label, color = Dark.W40, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp))
}

@Composable
private fun PlaylistTile(p: Spotify.Playlist, liked: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(4.dp))
            .frost()
            .clickable { Spotify.open(context, p.uri, p.web) },
    ) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (liked) {
                Box(Modifier.size(56.dp).background(Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFFC4EFD9)))), contentAlignment = Alignment.Center) {
                    Glyph(Glyphs.Heart, 22.dp, Color.White)
                }
            } else {
                val url = p.image
                val art by produceState(Spotify.cached(url), url) { if (value == null && url != null) value = Spotify.cover(context, url) }
                val a = art
                if (a != null) Image(a.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
                else Box(Modifier.size(56.dp).background(Color.White.copy(alpha = 0.05f)))
            }
        }
        Text(
            p.name,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
    }
}
