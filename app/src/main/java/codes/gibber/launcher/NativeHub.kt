package codes.gibber.launcher

import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
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
private val SIDEWAYS = setOf("dead-tide")

/** What sits on top of the native hub: a game's page (with its workspace), or the full web hub. */
private data class OpenPage(val url: String, val game: String?)

/** A sideways game turns the phone to landscape while it's on screen, and back when it leaves. */
@Composable
private fun GameWindow(sideways: Boolean) {
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

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101014)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name ?: "Gibber Hub", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    val (dot, words) = when (reached) {
                        true -> Green to "Connected to your PC"
                        false -> Red to "Can't reach your PC"
                        null -> Dim to "Looking for your PC…"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(6.dp))
                        Text(words, color = Dim, fontSize = 13.sp)
                    }
                }
            }
        }

        if (reached == false) {
            item {
                Panel {
                    Text("The hub didn't answer.", color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Check it's running on your PC and your phone is on the same Wi-Fi. The address is in Gibber Settings.",
                        color = Dim,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Chip("Try again") { reached = null; refreshes++ }
                }
            }
        }

        if (workspaces.isNotEmpty()) {
            item { Heading("Games and projects") }
            items(workspaces, key = { it.id }) { w ->
                WorkspaceCard(
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
            item { Heading("Claude usage") }
            items(accounts, key = { "u-" + it.name }) { UsageCard(it) }
        }

        val notes = inbox
        if (!notes.isNullOrEmpty()) {
            item { Heading("Sent to Claude") }
            items(notes, key = { "i-" + it.id }) { n ->
                Panel {
                    Text(n.kind.replaceFirstChar { it.uppercase() }, color = Color.White, fontSize = 14.sp)
                    Text(n.state.replaceFirstChar { it.uppercase() }, color = if (n.state == "failed") Red else Dim, fontSize = 12.sp)
                    n.note?.let { Text(it, color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { page = OpenPage(hubUrl, null) }
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Open the full hub", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("›", color = Dim, fontSize = 20.sp)
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
private fun Heading(text: String) {
    Text(
        text.uppercase(),
        color = Dim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PanelColor)
            .padding(14.dp)
    ) { content() }
}

@Composable
private fun Chip(label: String, strong: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (strong) Color(0xFF101014) else Color.White,
        fontSize = 14.sp,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (strong) Color.White else Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceCard(
    w: HubWorkspace,
    busy: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val (color, words) = when {
        busy -> Amber to "Working…"
        w.state == "up" -> Green to "Running"
        w.state == "starting" -> Amber to "Starting…"
        w.state == "other" -> Red to "Something else has port ${w.port}"
        else -> Dim to "Off"
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(w.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(words, color = Dim, fontSize = 12.sp)
                }
            }
            if (!busy && w.startable) {
                when (w.state) {
                    "up", "starting" -> Chip("Stop", onClick = onStop)
                    "down" -> Chip("Start", strong = true, onClick = onStart)
                }
            }
        }
        if (w.state == "up") {
            val nav = w.nav.ifEmpty { listOf(HubNav("open", "Open", "/")) }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                nav.forEachIndexed { i, n -> Chip(n.name, strong = i == 0) { onOpen(n.path) } }
            }
        }
    }
}

@Composable
private fun UsageCard(a: HubAccount) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            a.plan?.let { Text(it, color = Dim, fontSize = 12.sp) }
        }
        if (a.error != null && a.session == null && a.week == null) {
            Spacer(Modifier.height(6.dp))
            Text(a.error, color = Dim, fontSize = 13.sp)
            return@Panel
        }
        a.session?.let { Spacer(Modifier.height(10.dp)); Meter("Five hours", it) }
        a.week?.let { Spacer(Modifier.height(10.dp)); Meter("This week", it) }
        a.pace?.let { p ->
            val words = when (p.state) {
                "ahead" -> "Going fast" + (p.runsOut?.let { ": at this pace it runs out ${whenOf(it)}" } ?: "")
                "behind" -> "About ${p.unused}% would go unused this week"
                else -> "Right on pace"
            }
            Spacer(Modifier.height(8.dp))
            Text(words, color = if (p.state == "ahead") Amber else Dim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Meter(label: String, limit: HubLimit) {
    val used = limit.used.coerceIn(0, 100)
    Row {
        Text(label, color = Dim, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("$used%", color = Color.White, fontSize = 12.sp)
    }
    Spacer(Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { used / 100f },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = when {
            used >= 90 -> Red
            used >= 70 -> Amber
            else -> Green
        },
        trackColor = Color.White.copy(alpha = 0.1f),
        drawStopIndicator = {},
    )
    if (limit.resets.isNotBlank()) {
        Text("Resets ${whenOf(limit.resets)}", color = Dim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}
