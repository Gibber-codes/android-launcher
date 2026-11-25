package codes.gibber.launcher

import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PanelColor = Color.White.copy(alpha = 0.07f)
private val Dim = Color.White.copy(alpha = 0.6f)
private val Green = Color(0xFF5FD38A)
private val Amber = Color(0xFFE8B04B)
private val Red = Color(0xFFE06C6C)

/** Games that are played on their side: the phone turns to landscape when one opens. */
internal val SIDEWAYS = setOf("dead-tide")

/** What sits on top of the native hub: a game's page (with its workspace), or the full web hub. */
private data class OpenPage(val url: String, val game: String?)

/** A sideways game turns the phone to landscape while it's on screen, and back when it leaves. */
@Composable
internal fun GameWindow(sideways: Boolean) {
    val activity = LocalContext.current.activity() ?: return
    DisposableEffect(activity, sideways) {
        val before = activity.requestedOrientation
        if (sideways) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (sideways) activity.requestedOrientation = before }
    }
}

/**
 * The hub's home drawn as launcher screens. It asks the hub on the PC for the same things its
 * web page shows, so starting a game here starts it there. A game, and the full web hub, still
 * open in a web page on top, and Back comes back here.
 */
@Composable
fun NativeHub(hubUrl: String, isShown: Boolean, onGoHome: () -> Unit) {
    val api = remember(hubUrl) { HubApi(hubUrl) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var page by remember { mutableStateOf<OpenPage?>(null) }
    var reached by remember { mutableStateOf<Boolean?>(null) }
    var name by remember { mutableStateOf<String?>(null) }
    var workspaces by remember { mutableStateOf<List<HubWorkspace>>(emptyList()) }
    var usage by remember { mutableStateOf<List<HubAccount>?>(null) }
    var inbox by remember { mutableStateOf<List<HubInboxItem>?>(null) }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refreshes by remember { mutableIntStateOf(0) }

    suspend fun readWorkspaces() {
        val w = api.workspaces()
        reached = w != null
        if (w != null) workspaces = w
    }

    // Look again every few seconds while the hub is on screen, like its own page does.
    LaunchedEffect(isShown, page, refreshes) {
        if (!isShown || page != null) return@LaunchedEffect
        var tick = 0
        while (true) {
            readWorkspaces()
            if (reached == true && tick % 8 == 0) {
                if (name == null) name = api.name()
                usage = api.usage()
                inbox = api.inbox()
            }
            tick++
            delay(4_000)
        }
    }

    fun press(what: String, w: HubWorkspace) {
        busy = busy + w.id
        scope.launch {
            val problem = api.press(what, w.id)
            if (problem != null) Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
            readWorkspaces()
            busy = busy - w.id
        }
    }

    val open = page
    if (open != null) {
        if (open.game != null && isShown) GameWindow(sideways = open.game in SIDEWAYS)
        HubWebView(url = open.url, proxyBase = hubUrl, isShown = isShown, onGoHome = { page = null })
        return
    }

    BackHandler(enabled = isShown) { onGoHome() }

    Box(Modifier.fillMaxSize()) {
        OrbBackground()
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { BackLink("Back", onGoHome) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(
                            painter = painterResource(R.drawable.gibbercodes_logo),
                            contentDescription = "GibberCodes",
                            modifier = Modifier.size(28.dp),
                        )
                        ScreenTitle(name ?: "Gibber Hub")
                    }
                    Text(
                        when (reached) {
                            true -> "Your PC is on · home Wi-Fi"
                            false -> "Can't reach your PC"
                            null -> "Looking for your PC…"
                        },
                        color = Dark.W62,
                        fontSize = 13.sp,
                    )
                }
            }

            if (reached == false) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        Text(
                            "Check the hub is running on your PC and your phone is on the same Wi-Fi. The address is in Gibber Settings.",
                            color = Dark.W62,
                            fontSize = 13.sp,
                        )
                        FrostButton("Try again", hi = true) { reached = null; refreshes++ }
                    }
                }
            }

            if (workspaces.isNotEmpty()) {
                item { Head("Games and projects", Modifier.padding(top = 8.dp)) }
                items(workspaces, key = { it.id }) { w ->
                    WorkspaceRow(
                        w = w,
                        busy = w.id in busy,
                        onStart = { press("start", w) },
                        onStop = { press("stop", w) },
                        onOpen = { path -> page = OpenPage(api.pageOf(w, path), w.id) },
                    )
                }
            }

            val accounts = usage
            if (!accounts.isNullOrEmpty()) {
                item { Head("Claude usage", Modifier.padding(top = 18.dp)) }
                items(accounts, key = { "u-" + it.name }) { UsageBlock(it, showName = accounts.size > 1) }
            }

            val notes = inbox
            if (!notes.isNullOrEmpty()) {
                item { Head("Sent to Claude", Modifier.padding(top = 18.dp)) }
                items(notes, key = { "i-" + it.id }) { n ->
                    StatusRow(
                        dot = when (n.state) {
                            "failed" -> Red
                            "done" -> Dim
                            else -> Dark.Green
                        },
                        title = n.note ?: n.kind.replaceFirstChar { it.uppercase() },
                        sub = n.state.replaceFirstChar { it.uppercase() },
                        action = null,
                    )
                }
            }

            item {
                Row(
                    Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .frost()
                        .clickable { page = OpenPage(hubUrl, null) }
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Open the full hub", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Glyph(Glyphs.Forward, 18.dp, Dark.W62)
                }
            }
        }
    }
}

/** An ISO time from the hub as the phone's clock reads it: "3:40 PM" today, "Tue 9:00 AM" otherwise. */
private fun whenOf(iso: String): String = try {
    val t = Instant.parse(iso).atZone(ZoneId.systemDefault())
    val clock = DateTimeFormatter.ofPattern("h:mm a")
    when (t.toLocalDate()) {
        LocalDate.now() -> "at " + t.format(clock)
        LocalDate.now().plusDays(1) -> "tomorrow at " + t.format(clock)
        else -> t.format(DateTimeFormatter.ofPattern("EEE")) + " at " + t.format(clock)
    }
} catch (e: Exception) {
    iso
}

@Composable
internal fun Chip(label: String, strong: Boolean = false, onClick: () -> Unit) = FrostButton(label, hi = strong, onClick = onClick)

/** A game or project: green while it runs. Open it when it's on, start it when it's off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceRow(
    w: HubWorkspace,
    busy: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val (color, words) = when {
        busy -> Amber to "Working…"
        w.state == "up" -> Dark.Green to "Dev server on"
        w.state == "starting" -> Amber to "Starting…"
        w.state == "other" -> Red to "Something else has port ${w.port}"
        else -> Dim to "Dev server off"
    }
    val first = w.nav.firstOrNull()?.path ?: "/"
    Column {
        StatusRow(
            dot = color,
            title = w.name,
            sub = words,
            action = when {
                w.state == "up" -> "Open"
                !busy && w.startable && w.state == "down" -> "Start"
                else -> null
            },
            onAction = { if (w.state == "up") onOpen(first) else onStart() },
            onClick = if (w.state == "up") ({ onOpen(first) }) else null,
        )
        if (w.state == "up" && (w.nav.size > 1 || w.startable)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
            ) {
                w.nav.drop(1).forEach { n -> FrostButton(n.name) { onOpen(n.path) } }
                if (w.startable && !busy) FrostButton("Stop", onClick = onStop)
            }
        }
    }
}

@Composable
private fun UsageBlock(a: HubAccount, showName: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        if (showName) Text(a.name + (a.plan?.let { " · $it" } ?: ""), color = Color.White, fontSize = 13.sp)
        if (a.error != null && a.session == null && a.week == null) {
            Text(a.error, color = Dark.W62, fontSize = 12.sp)
            return@Column
        }
        a.session?.let { Meter("This session", it) }
        a.week?.let { Meter("This week", it) }
        a.pace?.let { p ->
            val words = when (p.state) {
                "ahead" -> "Going fast" + (p.runsOut?.let { ": at this pace it runs out ${whenOf(it)}" } ?: "")
                "behind" -> "About ${p.unused}% would go unused this week"
                else -> "Right on pace"
            }
            Text(words, color = if (p.state == "ahead") Amber else Dark.W40, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Meter(label: String, limit: HubLimit) {
    val used = limit.used.coerceIn(0, 100)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, color = Dark.W62, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("$used%", color = Color.White, fontSize = 12.sp)
        }
        Bar(used / 100f, Modifier.fillMaxWidth())
        if (limit.resets.isNotBlank()) Text("Resets ${whenOf(limit.resets)}", color = Dark.W40, fontSize = 11.sp)
    }
}
