package codes.gibber.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** The design's menus and dialogs: the dark pane, square corners, white words. */
private val DarkScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF111111),
    surface = Dark.Pane,
    onSurface = Color.White,
    onSurfaceVariant = Dark.W62,
    surfaceContainer = Dark.Pane,
    surfaceContainerHigh = Dark.Pane,
    surfaceContainerHighest = Dark.Pane,
    background = Dark.Night,
)
private val Square = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun LauncherApp(
    prefs: Prefs,
    apps: List<AppEntry>,
    homePresses: Int,
    resumes: Int,
    onAppsChanged: () -> Unit = {},
) {
    MaterialTheme(colorScheme = DarkScheme, shapes = Square) {
        val pager = rememberPagerState(initialPage = HOME, pageCount = { TABS.size })
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var drawerOpen by remember { mutableStateOf(false) }
        var favorites by remember { mutableStateOf(prefs.favorites) }

        fun saveFavorites(list: List<String>) {
            favorites = list
            prefs.favorites = list
        }

        // The equalizer starts with the launcher, so it hears players that hand over their sound.
        LaunchedEffect(Unit) { Eq.start(context) }
        var hubUrl by remember { mutableStateOf(normalizeHubUrl(prefs.hubUrl)) }
        var settingsOpen by remember { mutableStateOf(false) }
        var hubOpen by remember { mutableStateOf(false) }
        var ideasOpen by remember { mutableStateOf(false) }
        var playerOpen by remember { mutableStateOf(false) }
        var moved by remember { mutableStateOf(prefs.sections) }
        var hubProjects by remember { mutableStateOf(prefs.hubProjects) }
        var gameMenu by remember { mutableStateOf<String?>(null) }
        var gamePage by remember { mutableStateOf<GamePage?>(null) }
        var moving by remember { mutableStateOf<AppEntry?>(null) }
        var widgetIds by remember { mutableStateOf(prefs.widgets) }
        var pickingWidget by remember { mutableStateOf(false) }
        var removingWidget by remember { mutableStateOf(false) }
        var wallMenu by remember { mutableStateOf(false) }
        var groups by remember { mutableStateOf(prefs.groups) }
        var launches by remember { mutableStateOf(prefs.launches) }

        fun saveHubUrl(raw: String) {
            prefs.hubUrl = normalizeHubUrl(raw)
            hubUrl = normalizeHubUrl(prefs.hubUrl)
        }

        SideEffect {
            HubLink.open = { drawerOpen = false; hubOpen = true }
            HubLink.openSettings = { drawerOpen = false; settingsOpen = true }
            HubLink.openIdeas = { drawerOpen = false; ideasOpen = true }
            HubGames.open = { id -> drawerOpen = false; gameMenu = id }
            HubGames.play = { id ->
                val live = prefs.liveUrl(id)
                if (live.isNotBlank()) {
                    drawerOpen = false
                    gamePage = GamePage(live, id)
                }
            }
            Apps.onLaunch = { app ->
                prefs.countLaunch(app.key)
                launches = prefs.launches
            }
        }

        // A game you installed from Chrome (same name as the game) lends it its picture and address.
        LaunchedEffect(apps) {
            var newLive = false
            prefs.hubGames.forEach { (id, name) ->
                val copy = apps.firstOrNull {
                    !HubGames.isGame(it) && !HubLink.isOwn(it) && it.label.trim().equals(name.trim(), ignoreCase = true)
                } ?: return@forEach
                Apps.iconDrawable(context, copy)?.let { GameIcons.fromInstalled(context, id, it) }
                val address = Apps.webAppAddress(context, copy)
                if (address != null && prefs.liveUrl(id).isBlank()) {
                    prefs.setLiveUrl(id, address)
                    newLive = true
                }
            }
            if (newLive) onAppsChanged()
        }

        // Keep the hub's games up to date, whenever you come back to the launcher.
        LaunchedEffect(resumes, hubUrl) {
            val found = if (hubUrl.isBlank()) null else HubApi(hubUrl).workspaces()
            // Fetch each game's own picture: from its live site, else from its dev server on the PC.
            val pc = android.net.Uri.parse(hubUrl).host
            val ports = found.orEmpty().associate { it.id to it.port }
            // When the hub knows a game's live site, use it, unless you set one yourself.
            var newLive = false
            found.orEmpty().forEach { w ->
                if (w.live != null && prefs.liveUrl(w.id).isBlank()) {
                    prefs.setLiveUrl(w.id, w.live)
                    newLive = true
                }
            }
            // A newly known live site makes the game's own app show in the app list.
            if (newLive) onAppsChanged()
            GameIcons.refresh(
                context,
                (prefs.hubGames.map { it.first } + ports.keys).distinct().associateWith { id ->
                    listOfNotNull(
                        prefs.liveUrl(id).ifBlank { null },
                        if (pc != null && ports[id] != null) "http://$pc:${ports[id]}/" else null,
                    )
                },
                // The hub reads each game's icon from its folder on the PC, running or not.
                fromHub = if (found == null) emptyMap() else HubApi(hubUrl).let { hub -> ports.keys.associateWith { hub.iconUrl(it) } },
            )
            if (found == null) return@LaunchedEffect
            val games = found.map { it.id to it.name }
            val projects = found.filter { it.group.isNotBlank() && !it.group.contains("game", ignoreCase = true) }
                .map { it.id }.toSet()
            if (games != prefs.hubGames || projects != prefs.hubProjects) {
                prefs.hubGames = games
                prefs.hubProjects = projects
                hubProjects = projects
                onAppsChanged()
            }
        }

        // Pressing Home on the launcher brings you to the Home page, with nothing open over it. From
        // another app it only brings the launcher back as you left it (MainActivity doesn't count that press).
        LaunchedEffect(homePresses) {
            if (homePresses > 0) {
                drawerOpen = false
                gameMenu = null
                gamePage = null
                playerOpen = false
                ideasOpen = false
                hubOpen = false
                settingsOpen = false
                pager.animateScrollToPage(HOME)
            }
        }
        // A Music group holding Spotify and the JBL app, made once both are installed.
        LaunchedEffect(apps) {
            if (prefs.musicMade) return@LaunchedEffect
            val music = apps.filter { it.packageName == "com.spotify.music" } +
                apps.filter { it.label.contains("JBL", ignoreCase = true) }
            if (music.none { it.packageName == "com.spotify.music" } || music.size < 2) return@LaunchedEffect
            val keys = music.map { it.key }.distinct()
            val rest = groups.map { g -> g.copy(keys = g.keys - keys.toSet()) }.filter { it.keys.size >= 2 }
            groups = rest + AppGroup("music", "Music", keys)
            prefs.groups = groups
            prefs.musicMade = true
        }

        // A home screen has nowhere to go back to, so Back goes to Home and stops there.
        BackHandler(enabled = true) { scope.launch { pager.animateScrollToPage(HOME) } }
        BackHandler(enabled = drawerOpen) { drawerOpen = false }

        val cells = remember(apps, groups, moved, hubProjects) { Groups.cells(apps, groups, moved, hubProjects) }
        // Kept the same across redraws, so a swipe under way isn't restarted.
        val openDrawer = remember { { drawerOpen = true } }
        val kit = PageKit(
            apps = apps,
            cells = cells,
            groups = groups,
            onGroupsChange = { groups = it; prefs.groups = it },
            moved = moved,
            hubProjects = hubProjects,
            onAskMove = { moving = it },
            onRemove = { a ->
                moved = moved + (a.key to Sections.HIDDEN)
                prefs.sections = moved
            },
            onOpenDrawer = openDrawer,
        )

        Box(Modifier.fillMaxSize()) {
            OrbBackground()
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                // No clock and no bar at the bottom: the tabs are the only thing along an edge.
                TabsRow(
                    current = pager.currentPage,
                    onPick = { scope.launch { pager.animateScrollToPage(it) } },
                    trailing = {
                        TabsTrailing(pager.currentPage, onAll = { drawerOpen = true }, onSettings = { settingsOpen = true })
                    },
                    modifier = Modifier.padding(start = 22.dp, end = 16.dp, top = 14.dp),
                )
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
                    when (i) {
                        0 -> SearchPage(kit, launches)
                        HOME -> HomePage(
                            kit,
                            onOpenPlayer = { playerOpen = true },
                            onHoldEmpty = { wallMenu = true },
                            widgets = {
                                WidgetArea(
                                    ids = widgetIds,
                                    onIdsChange = { widgetIds = it; prefs.widgets = it },
                                    picking = pickingWidget,
                                    onPickingChange = { pickingWidget = it },
                                    removing = removingWidget,
                                    onRemovingChange = { removingWidget = it },
                                )
                            },
                        )
                        2 -> DevPage(kit, prefs, hubUrl, onOpenIdeas = { ideasOpen = true })
                        else -> MechanicPage(kit)
                    }
                }
            }

            if (drawerOpen) {
                AppDrawer(
                    // A game with a live app shows once in the list: as the app that plays it.
                    apps = apps.filterNot { a ->
                        HubGames.isMenu(a) && apps.any { HubGames.isLive(it) && HubGames.idOf(it) == HubGames.idOf(a) }
                    },
                    favorites = favorites,
                    onFavoritesChange = ::saveFavorites,
                    onClose = { drawerOpen = false },
                    onAppsChanged = onAppsChanged,
                    seenBuild = prefs.seenBuild,
                    onAskMove = { moving = it },
                )
            }
            if (playerOpen) PlayerScreen(onClose = { playerOpen = false })
            if (ideasOpen) IdeasScreen(hubUrl, onClose = { ideasOpen = false })
            if (hubOpen) {
                key(hubUrl) {
                    HubScreen(hubUrl = hubUrl, onSaveUrl = ::saveHubUrl, isShown = true, onGoHome = { hubOpen = false })
                }
            }
            if (settingsOpen) {
                SettingsScreen(
                    prefs = prefs,
                    hubUrl = hubUrl,
                    onSaveUrl = ::saveHubUrl,
                    resumes = resumes,
                    onClose = { settingsOpen = false },
                )
            }
            val menuFor = gameMenu
            if (menuFor != null) {
                GameMenu(
                    id = menuFor,
                    name = prefs.hubGames.firstOrNull { it.first == menuFor }?.second ?: menuFor,
                    prefs = prefs,
                    hubUrl = hubUrl,
                    // Your games live on the Dev page, so their menus always offer the dev server.
                    devMode = true,
                    onPlay = { gamePage = it; gameMenu = null },
                    onLiveChanged = onAppsChanged,
                    onClose = { gameMenu = null },
                )
            }
            val playing = gamePage
            if (playing != null) {
                key(playing.url) {
                    GameScreen(page = playing, hubUrl = hubUrl, onClose = { gamePage = null })
                }
            }
            if (wallMenu) {
                AlertDialog(
                    onDismissRequest = { wallMenu = false },
                    title = { Text("Home screen") },
                    text = {
                        Column {
                            Text(
                                "Add a widget",
                                color = Color.White,
                                fontSize = 17.sp,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { wallMenu = false; pickingWidget = true }
                                    .padding(vertical = 10.dp),
                            )
                            if (widgetIds.isNotEmpty()) {
                                Text(
                                    "Remove a widget",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { wallMenu = false; removingWidget = true }
                                        .padding(vertical = 10.dp),
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { wallMenu = false }) { Text("Cancel") } },
                )
            }
            val app = moving
            if (app != null) {
                MoveDialog(
                    app = app,
                    moved = moved,
                    hubProjects = hubProjects,
                    onMove = { a, section ->
                        moved = if (section == null) moved - a.key else moved + (a.key to section)
                        prefs.sections = moved
                    },
                    onDismiss = { moving = null },
                )
            }
        }
    }
}

/** Home's place among the tabs: Search is to its left, Dev and Mechanic to its right. */
const val HOME = 1
