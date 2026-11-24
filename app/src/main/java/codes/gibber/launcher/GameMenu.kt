package codes.gibber.launcher

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A game to open: the page's address, and which game it is (for landscape). */
data class GamePage(val url: String, val game: String)

private val Faint = Color.White.copy(alpha = 0.6f)

/** Adds https:// when the address was typed without it. */
private fun webAddress(raw: String): String {
    val t = raw.trim()
    return if (t.isEmpty() || t.contains("://")) t else "https://$t"
}

private fun hostOf(url: String): String = Uri.parse(url).host ?: url

/**
 * What tapping a hub game shows: play the live version, an older one you saved, and in dev
 * mode the dev server on your PC.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameMenu(
    id: String,
    name: String,
    prefs: Prefs,
    hubUrl: String,
    devMode: Boolean,
    onPlay: (GamePage) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(enabled = true) { onClose() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var live by remember(id) { mutableStateOf(prefs.liveUrl(id)) }
    var editingLive by remember(id) { mutableStateOf(prefs.liveUrl(id).isBlank()) }
    var liveDraft by remember(id) { mutableStateOf(prefs.liveUrl(id)) }
    var versions by remember(id) { mutableStateOf(prefs.versions(id)) }
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

    // A dimmed backdrop; tapping it closes the menu.
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                // Taps inside the menu stay in the menu.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            // The live version.
            if (live.isNotBlank() && !editingLive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip("Play", strong = true) { onPlay(GamePage(live, id)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Live version", color = Color.White, fontSize = 15.sp)
                        Text(hostOf(live), color = Faint, fontSize = 12.sp, maxLines = 1)
                    }
                    Text(
                        "Change",
                        color = Faint,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { liveDraft = live; editingLive = true }.padding(8.dp),
                    )
                }
            } else {
                Text("Live version", color = Color.White, fontSize = 15.sp)
                Text("Paste the address of your Netlify site.", color = Faint, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = liveDraft,
                    onValueChange = { liveDraft = it },
                    placeholder = { Text("your-site.netlify.app") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Save", strong = true) {
                        val url = webAddress(liveDraft)
                        prefs.setLiveUrl(id, url)
                        live = url
                        editingLive = url.isBlank()
                    }
                    if (live.isNotBlank()) Chip("Cancel") { editingLive = false }
                }
            }

            // Older versions you saved.
            Spacer(Modifier.height(20.dp))
            Text("OLDER VERSIONS", color = Faint, fontSize = 12.sp, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(6.dp))
            if (versions.isEmpty() && !adding) {
                Text(
                    "None saved yet. On Netlify, open Deploys, pick an older deploy and copy its address.",
                    color = Faint,
                    fontSize = 12.sp,
                )
            }
            versions.forEachIndexed { i, (vName, vUrl) ->
                VersionRow(
                    name = vName,
                    url = vUrl,
                    onOpen = { onPlay(GamePage(vUrl, id)) },
                    onRemove = {
                        versions = versions.filterIndexed { j, _ -> j != i }
                        prefs.setVersions(id, versions)
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            if (adding) {
                OutlinedTextField(
                    value = versionName,
                    onValueChange = { versionName = it },
                    placeholder = { Text("Name, like v0.3 or before the boss fight") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = versionUrl,
                    onValueChange = { versionUrl = it },
                    placeholder = { Text("Its address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Save", strong = true) {
                        val url = webAddress(versionUrl)
                        if (url.isNotBlank()) {
                            versions = versions + ((versionName.trim().ifBlank { hostOf(url) }) to url)
                            prefs.setVersions(id, versions)
                        }
                        versionName = ""
                        versionUrl = ""
                        adding = false
                    }
                    Chip("Cancel") { adding = false }
                }
            } else {
                Chip("Add a version") { adding = true }
            }

            // Dev mode: the dev server on your PC, through Gibber Hub.
            if (devMode) {
                Spacer(Modifier.height(20.dp))
                Text("DEV SERVER ON YOUR PC", color = Faint, fontSize = 12.sp, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))
                val w = workspace
                when {
                    api == null -> Text("Add your hub's address in Gibber Settings first.", color = Faint, fontSize = 13.sp)
                    reached == null -> Text("Asking your PC…", color = Faint, fontSize = 13.sp)
                    reached == false -> Text("Couldn't reach your PC. Check that Gibber Hub is running and you're on home Wi-Fi.", color = Faint, fontSize = 13.sp)
                    w == null -> Text("Gibber Hub doesn't list this game.", color = Faint, fontSize = 13.sp)
                    else -> {
                        val hub = api!!
                        val w = w!!
                        val (dot, words) = when {
                            busy -> Color(0xFFE8B04B) to "Working…"
                            w.state == "up" -> Color(0xFF5FD38A) to "Running"
                            w.state == "starting" -> Color(0xFFE8B04B) to "Starting…"
                            w.state == "other" -> Color(0xFFE06C6C) to "Something else has port ${w.port}"
                            else -> Faint to "Off"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                            Spacer(Modifier.width(8.dp))
                            Text(words, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            if (!busy && w.startable) {
                                val what = when (w.state) {
                                    "up", "starting" -> "stop"
                                    "down" -> "start"
                                    else -> null
                                }
                                if (what != null) {
                                    Chip(if (what == "start") "Start dev server" else "Stop", strong = what == "start") {
                                        busy = true
                                        scope.launch {
                                            val problem = hub.press(what, w.id)
                                            if (problem != null) Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
                                            readState()
                                            busy = false
                                        }
                                    }
                                }
                            }
                        }
                        if (w.state == "up") {
                            Spacer(Modifier.height(10.dp))
                            val nav = w.nav.ifEmpty { listOf(HubNav("open", "Open", "/")) }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                nav.forEachIndexed { i, n ->
                                    Chip(n.name, strong = i == 0) { onPlay(GamePage(hub.pageOf(w, n.path), id)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VersionRow(name: String, url: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onOpen)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 15.sp, maxLines = 1)
            Text(hostOf(url), color = Faint, fontSize = 12.sp, maxLines = 1)
        }
        Text(
            "Remove",
            color = Faint,
            fontSize = 13.sp,
            modifier = Modifier.clickable(onClick = onRemove).padding(8.dp),
        )
    }
}

/** A game on top of everything, full screen, turned sideways when it's played that way. */
@Composable
fun GameScreen(page: GamePage, hubUrl: String, onClose: () -> Unit) {
    HideSystemBars()
    GameWindow(sideways = page.game in SIDEWAYS)
    Box(Modifier.fillMaxSize().background(Color(0xFF101014))) {
        HubWebView(url = page.url, proxyBase = hubUrl, isShown = true, onGoHome = onClose)
    }
}
