package codes.gibber.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The tabs along the top, left to right as you swipe: Search, Home, Dev and Mechanic. */
val TABS = listOf("Search", "Home", "Dev", "Mechanic")

@Composable
fun TabsRow(current: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f).offset(x = (-10).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TABS.forEachIndexed { i, name ->
                val here = i == current
                Text(
                    name.uppercase(),
                    color = if (here) Color.White else Dark.W40,
                    fontFamily = Dark.Mono,
                    fontSize = 12.sp,
                    letterSpacing = 0.72.sp,
                    modifier = Modifier
                        .clickable { onPick(i) }
                        .drawBehind {
                            if (here) {
                                val y = size.height - 1.dp.toPx()
                                drawLine(Color.White, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                            }
                        }
                        .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 6.dp),
                )
            }
        }
        trailing()
    }
}

/** Search's "All ›", Dev's gear: what sits at the right of the tabs on each page. */
@Composable
fun TabsTrailing(page: Int, onAll: () -> Unit, onSettings: () -> Unit) {
    when (page) {
        0 -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.clickable(onClick = onAll).padding(6.dp),
        ) {
            Text("All", color = Dark.W62, fontSize = 12.sp)
            Glyph(Glyphs.Forward, 14.dp, Dark.W62)
        }
        2 -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clickable(onClick = onSettings),
        ) { Glyph(Glyphs.Gear, 20.dp, Dark.W62) }
        else -> Spacer(Modifier.size(36.dp))
    }
}

/** What every page needs to draw its sections and their menus. */
class PageKit(
    val apps: List<AppEntry>,
    val cells: Map<String, List<Cell>>,
    val groups: List<AppGroup>,
    val onGroupsChange: (List<AppGroup>) -> Unit,
    val moved: Map<String, String>,
    val hubProjects: Set<String>,
    val onAskMove: (AppEntry) -> Unit,
    val onRemove: (AppEntry) -> Unit,
    val onOpenDrawer: () -> Unit,
)

@Composable
private fun SectionList(kit: PageKit, names: List<String>, onHold: () -> Unit = {}) {
    SectionsArea(
        sections = names,
        cells = kit.cells,
        groups = kit.groups,
        onGroupsChange = kit.onGroupsChange,
        moved = kit.moved,
        hubProjects = kit.hubProjects,
        onAskMove = kit.onAskMove,
        onRemove = kit.onRemove,
        onHold = onHold,
    )
}

/**
 * Home: the music, a month calendar that fills the page, and its eight apps. Hold empty space for
 * the widget menu; any widgets you added sit above the calendar.
 */
@Composable
fun HomePage(kit: PageKit, onOpenPlayer: () -> Unit, onHoldEmpty: () -> Unit, widgets: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    // Holding an app opens its own menu; this keeps that hold from also opening the widget menu.
    val lastAppHold = remember { longArrayOf(0L) }
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .then(rememberSwipeUp(kit.onOpenDrawer))
            .pointerInput(onHoldEmpty) {
                detectTapGestures(onLongPress = {
                    scope.launch {
                        delay(150)
                        if (System.currentTimeMillis() - lastAppHold[0] > 1_000) onHoldEmpty()
                    }
                })
            }
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 10.dp),
    ) {
        MusicCard(onOpenPlayer)
        widgets()
        CalendarCard(Modifier.weight(1f))
        SectionList(kit, listOf("Apps")) { lastAppHold[0] = System.currentTimeMillis() }
    }
}

/** A page that scrolls, opening the app list when you pull past its end. */
@Composable
private fun ScrollPage(kit: PageKit, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier
            .fillMaxSize()
            .then(rememberSwipeUp(kit.onOpenDrawer))
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 28.dp),
    ) { content() }
}

/** The design's Used most, until you've opened enough apps from the launcher to have your own. */
private val USED_MOST = listOf(
    "com.spotify.music", "com.venmo", "gibber-ideas", "com.samsung.android.dialer", "com.google.android.dialer",
    "com.anthropic.claude", "com.google.android.gm", "Claude Duet", "com.android.chrome",
    "com.samsung.android.calendar", "com.google.android.calendar", "com.samsung.android.app.notes",
    "com.google.android.keep", "com.sec.android.app.myfiles", "com.github.android",
)

/** Search: the apps you use most, then your games. "All" (or swiping up) opens every app with search. */
@Composable
fun SearchPage(kit: PageKit, launches: Map<String, Int>) {
    val apps = kit.apps.filterNot { HubGames.isGame(it) }
    val counted = apps.filter { (launches[it.key] ?: 0) > 0 }.sortedByDescending { launches[it.key] ?: 0 }
    val starting = USED_MOST.mapNotNull { want ->
        apps.firstOrNull { it.packageName == want || it.activityName == want || it.label.equals(want, ignoreCase = true) }
    }
    val most = (counted + starting).distinctBy { it.key }.take(12)
    val games = kit.apps.filter { HubGames.isMenu(it) }
    ScrollPage(kit) {
        Grid("Used most", most)
        if (games.isNotEmpty()) Grid("Games", games)
        Text(
            "Swipe up for all your apps and search",
            color = Dark.W40,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Grid(name: String, apps: List<AppEntry>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Head(name)
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { app ->
                    Box(Modifier.weight(1f)) {
                        AppCell(
                            app,
                            buildList {
                                if (Apps.canUninstall(context, app)) add(RowAction("Uninstall") { Apps.uninstall(context, app) })
                                if (!HubLink.isOwn(app) && !Shortcuts.isShortcut(app)) add(RowAction("App info") { Apps.openInfo(context, app) })
                            },
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Dev: Talk to Claude, the Ideas widget, your games and the dev tools. */
@Composable
fun DevPage(kit: PageKit, prefs: Prefs, hubUrl: String, onOpenIdeas: () -> Unit) {
    ScrollPage(kit) {
        TalkToClaude(prefs)
        IdeasWidget(hubUrl, onOpenIdeas)
        SectionList(kit, listOf("My games", "Dev tools"))
    }
}

/** Mechanic: the same layout, with Jobs, Parts and Mechanic tools. */
@Composable
fun MechanicPage(kit: PageKit) {
    ScrollPage(kit) { SectionList(kit, listOf("Jobs", "Parts", "Mechanic tools")) }
}

/** A row in a list: a dot, a name over a line, and a word at the right. */
@Composable
fun StatusRow(dot: Color, title: String, sub: String, action: String?, onAction: () -> Unit = {}, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
    ) {
        Dot(dot)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = Dark.W62, fontSize = 12.sp, maxLines = 2)
        }
        if (action != null) {
            Text(action, color = Dark.W62, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onAction).padding(8.dp))
        }
    }
}

@Composable
fun Spacing(h: Int) = Spacer(Modifier.height(h.dp))
