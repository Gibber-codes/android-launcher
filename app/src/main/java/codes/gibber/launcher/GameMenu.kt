package codes.gibber.launcher

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

/** A game to open: the page's address, and which game it is (for landscape). */
data class GamePage(val url: String, val game: String)

// The design's colours.
private val Card = Color(0xFF1F1E23)
private val Line = Color(0xFF34333A)
private val PillBg = Color(0xFF2A2930)
private val Cream = Color(0xFFF7F1EA)
private val Sage = Color(0xFFBABF95)
private val Lilac = Color(0xFFC39CDC)
private val Muted = Color(0xFFA39EA6)
private val Ink = Color(0xFF101012)

/** Stand-ins for a game's style colours until the game's real ones are read in. */
private val StyleDots = listOf(Color(0xFF1F6F7A), Color(0xFFE0A85A), Color(0xFFC9473B))

/** Adds https:// when the address was typed without it. */
private fun webAddress(raw: String): String {
    val t = raw.trim()
    return if (t.isEmpty() || t.contains("://")) t else "https://$t"
}

private fun hostOf(url: String): String = Uri.parse(url).host ?: url

/**
 * What tapping a game on the Dev page shows: a small card over the dimmed page. Its top line has
 * the game and a switch for the dev server on your PC; under it, Dev build (while the server runs)
 * and Style; then Older versions, which opens to the versions you saved and the live address.
 */
@Composable
fun GameMenu(
    id: String,
    name: String,
    prefs: Prefs,
    hubUrl: String,
    devMode: Boolean,
    onPlay: (GamePage) -> Unit,
    onLiveChanged: () -> Unit = {},
    onClose: () -> Unit,
) {
    BackHandler(enabled = true) { onClose() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var live by remember(id) { mutableStateOf(prefs.liveUrl(id)) }
    var editingLive by remember(id) { mutableStateOf(false) }
    var liveDraft by remember(id) { mutableStateOf(prefs.liveUrl(id)) }
    var versions by remember(id) { mutableStateOf(prefs.versions(id)) }
    var olderOpen by remember(id) { mutableStateOf(false) }
    var adding by remember(id) { mutableStateOf(false) }
    var versionName by remember(id) { mutableStateOf("") }
    var versionUrl by remember(id) { mutableStateOf("") }

    val api = remember(hubUrl) { if (hubUrl.isBlank()) null else HubApi(hubUrl) }
    var workspace by remember(id) { mutableStateOf<HubWorkspace?>(null) }
    var reached by remember(id) { mutableStateOf<Boolean?>(null) }
    var busy by remember(id) { mutableStateOf(false) }

    suspend fun readState() {
        val all = api?.workspaces()
        reached = all != null
        workspace = all?.firstOrNull { it.id == id }
    }
    LaunchedEffect(devMode, api, id) {
        if (!devMode || api == null) return@LaunchedEffect
        while (true) {
            readState()
            delay(3_000)
        }
    }

    val w = workspace
    val running = w?.state == "up"

    // While the dev server runs, fetch the game's own picture from it, so its icon shows from then on.
    LaunchedEffect(running, id) {
        val hub = api
        val ws = workspace
        if (running && hub != null && ws != null) {
            GameIcons.refresh(context, mapOf(id to listOfNotNull(live.ifBlank { null }, hub.pageOf(ws))))
        }
    }
    val on = running || w?.state == "starting"
    val (status, statusColor) = when {
        !devMode -> "" to Muted
        api == null -> "Add your hub's address in Gibber Settings" to Muted
        reached == null -> "Asking your PC…" to Muted
        reached == false -> "Can't reach your PC" to Muted
        w == null -> "Gibber Hub doesn't list this game" to Muted
        busy -> "Working…" to Muted
        running -> "Running on your PC" to Sage
        w?.state == "starting" -> "Starting…" to Sage
        w?.state == "other" -> "Something else has port ${w?.port}" to Color(0xFFE58FB3)
        else -> "Off" to Muted
    }
    val canSwitch = devMode && w != null && w.startable && !busy && w.state != "other"

    fun flip(turnOn: Boolean) {
        val hub = api ?: return
        val ws = w ?: return
        busy = true
        scope.launch {
            val problem = hub.press(if (turnOn) "start" else "stop", ws.id)
            if (problem != null) Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
            readState()
            busy = false
        }
    }

    // The game's own picture, or its letter icon until that's found.
    val artPx = with(LocalDensity.current) { 44.dp.roundToPx() }
    val art = remember(id, GameIcons.version) {
        GameIcons.cached(context, id, artPx)
            ?: HubGames.tile(AppEntry(label = name, packageName = context.packageName, activityName = "hub-game:$id"), artPx)
    }

    // The dimmed page behind; tapping it closes the card.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .imePadding()
                .fillMaxWidth(0.8f)
                .widthIn(max = 340.dp)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Card)
                .border(1.dp, Line, RoundedCornerShape(16.dp))
                // Taps inside the card stay in the card.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            // Top line: the game, and the dev server's switch.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(art.asImageBitmap(), contentDescription = null, modifier = Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Cream, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (devMode) {
                        Text("Dev server", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text(status, color = statusColor, fontSize = 11.sp, maxLines = 2)
                    }
                }
                if (devMode) {
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = on,
                        onCheckedChange = { flip(it) },
                        enabled = canSwitch,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Lilac,
                            checkedThumbColor = Ink,
                            checkedBorderColor = Lilac,
                            uncheckedTrackColor = PillBg,
                            uncheckedThumbColor = Muted,
                            uncheckedBorderColor = Line,
                        ),
                    )
                }
            }

            // Two pills: Dev build (only while the dev server runs) and Style.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (running && api != null && w != null) {
                    val first = w.nav.firstOrNull { !it.name.contains("style", ignoreCase = true) }
                    Pill("Dev build", Modifier.weight(1f), lead = { PlayMark() }) {
                        onPlay(GamePage(api.pageOf(w, first?.path ?: "/"), id))
                    }
                }
                Pill("Style", Modifier.weight(1f), lead = { Dots() }) {
                    val style = w?.nav?.firstOrNull { it.name.contains("style", ignoreCase = true) }
                    if (running && api != null && w != null && style != null) onPlay(GamePage(api.pageOf(w, style.path), id))
                    else Toast.makeText(
                        context,
                        if (style == null) "$name's style guide isn't set up yet." else "Turn on the dev server to open the style guide.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }

            // Older versions: one dropdown with the versions you saved and the live address.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Line, RoundedCornerShape(10.dp))
                    .clickable { olderOpen = !olderOpen }
                    .padding(horizontal = 12.dp),
            ) {
                Text("Older versions", color = Cream, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("⌄", color = Muted, fontSize = 14.sp, modifier = Modifier.rotate(if (olderOpen) 180f else 0f).offset(y = (-3).dp))
            }
            if (olderOpen) {
                versions.forEachIndexed { i, (vName, vUrl) ->
                    LineRow(
                        title = vName,
                        sub = hostOf(vUrl),
                        action = "Remove",
                        onOpen = { onPlay(GamePage(vUrl, id)) },
                        onAction = {
                            versions = versions.filterIndexed { j, _ -> j != i }
                            prefs.setVersions(id, versions)
                        },
                    )
                }
                if (versions.isEmpty() && !adding) {
                    Text(
                        "None saved yet. On Netlify, open Deploys, pick an older deploy and copy its address.",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
                if (adding) {
                    Field(versionName, { versionName = it }, "Name, like v0.3")
                    Field(versionUrl, { versionUrl = it }, "Its address", uri = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("Save", Modifier.weight(1f), strong = true) {
                            val url = webAddress(versionUrl)
                            if (url.isNotBlank()) {
                                versions = versions + ((versionName.trim().ifBlank { hostOf(url) }) to url)
                                prefs.setVersions(id, versions)
                            }
                            versionName = ""
                            versionUrl = ""
                            adding = false
                        }
                        Pill("Cancel", Modifier.weight(1f)) { adding = false }
                    }
                } else {
                    Pill("Add a version", Modifier.fillMaxWidth()) { adding = true }
                }

                // The newest version's address, which the game's own app plays.
                if (live.isNotBlank() && !editingLive) {
                    LineRow(
                        title = "Newest version",
                        sub = hostOf(live),
                        action = "Change",
                        onOpen = { onPlay(GamePage(live, id)) },
                        onAction = { liveDraft = live; editingLive = true },
                    )
                } else {
                    Text("Newest version: paste your Netlify site's address.", color = Muted, fontSize = 11.sp)
                    Field(liveDraft, { liveDraft = it }, "your-site.netlify.app", uri = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("Save", Modifier.weight(1f), strong = true) {
                            val url = webAddress(liveDraft)
                            prefs.setLiveUrl(id, url)
                            live = url
                            editingLive = false
                            onLiveChanged()
                        }
                        if (live.isNotBlank()) Pill("Cancel", Modifier.weight(1f)) { editingLive = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(
    label: String,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    lead: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (strong) Lilac else PillBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
    ) {
        if (lead != null) {
            lead()
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = if (strong) Ink else Cream, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun PlayMark() = Text("▶", color = Cream, fontSize = 9.sp)

@Composable
private fun Dots() {
    Row {
        StyleDots.forEachIndexed { i, c ->
            Box(
                Modifier
                    .offset(x = (-3 * i).dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(c)
            )
        }
    }
}

@Composable
private fun LineRow(title: String, sub: String, action: String, onOpen: () -> Unit, onAction: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Cream, fontSize = 13.sp, maxLines = 1)
            Text(sub, color = Muted, fontSize = 11.sp, maxLines = 1)
        }
        Text(action, color = Muted, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onAction).padding(8.dp))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, uri: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = if (uri) KeyboardOptions(keyboardType = KeyboardType.Uri) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A game on top of everything, full screen, turned sideways when it's played that way. */
@Composable
fun GameScreen(page: GamePage, hubUrl: String, onClose: () -> Unit) {
    HideSystemBars()
    GameWindow(sideways = page.game in SIDEWAYS)
    // A game with no picture of its own gets one from the screen a few seconds into play.
    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(page.game) {
        if (activity == null) return@LaunchedEffect
        delay(8_000)
        val shot = grabScreen(activity) ?: return@LaunchedEffect
        GameIcons.snapshot(activity, page.game, shot)
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
        HubWebView(url = page.url, proxyBase = hubUrl, isShown = true, onGoHome = onClose)
    }
}

/** What's on screen now, games' GPU drawing included (Android 8 and later). */
private suspend fun grabScreen(activity: android.app.Activity): android.graphics.Bitmap? =
    kotlinx.coroutines.suspendCancellableCoroutine { done ->
        try {
            val view = activity.window.decorView
            if (view.width == 0 || view.height == 0) { done.resume(null); return@suspendCancellableCoroutine }
            val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(activity.window, bmp, { result ->
                done.resume(if (result == android.view.PixelCopy.SUCCESS) bmp else null)
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        } catch (e: Exception) {
            done.resume(null)
        }
    }
