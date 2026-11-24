package codes.gibber.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherApp(
    prefs: Prefs,
    apps: List<AppEntry>,
    homePresses: Int,
    resumes: Int,
    onAppsChanged: () -> Unit = {},
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val pages = Sections.PAGES
        val pager = rememberPagerState(initialPage = 0, pageCount = { pages.size })
        val scope = rememberCoroutineScope()
        var drawerOpen by remember { mutableStateOf(false) }
        var favorites by remember { mutableStateOf(prefs.favorites) }

        fun saveFavorites(list: List<String>) {
            favorites = list
            prefs.favorites = list
        }

        val context = androidx.compose.ui.platform.LocalContext.current
        var hubUrl by remember { mutableStateOf(normalizeHubUrl(prefs.hubUrl)) }
        var settingsOpen by remember { mutableStateOf(false) }
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

        fun saveHubUrl(raw: String) {
            prefs.hubUrl = normalizeHubUrl(raw)
            hubUrl = prefs.hubUrl
        }

        androidx.compose.runtime.SideEffect {
            HubLink.openSettings = {
                drawerOpen = false
                settingsOpen = true
            }
            HubGames.open = { id ->
                drawerOpen = false
                gameMenu = id
            }
            HubGames.play = { id ->
                val live = prefs.liveUrl(id)
                if (live.isNotBlank()) {
                    drawerOpen = false
                    gamePage = GamePage(live, id)
                }
            }
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

        // Pressing Home always brings you back to the Home page.
        LaunchedEffect(homePresses) {
            if (homePresses > 0) {
                drawerOpen = false
                gameMenu = null
                gamePage = null
                pager.animateScrollToPage(0)
            }
        }
        // Coming back from an app closes the drawer.
        LaunchedEffect(resumes) { drawerOpen = false }

        // A Music group on Home holding Spotify and the JBL app, made once both are installed.
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

        // A home screen has nowhere to go back to, so Back goes to the Home page and stops there.
        BackHandler(enabled = true) { scope.launch { pager.animateScrollToPage(0) } }
        BackHandler(enabled = drawerOpen) { drawerOpen = false }

        Box(Modifier.fillMaxSize()) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
                val page = pages[i]
                PageScreen(
                    page = page,
                    apps = apps,
                    onOpenDrawer = { drawerOpen = true },
                    moved = moved,
                    hubProjects = hubProjects,
                    onAskMove = { moving = it },
                    onRemove = { a ->
                        moved = moved + (a.key to Sections.HIDDEN)
                        prefs.sections = moved
                    },
                    onLongPressWallpaper = if (i == 0) ({ wallMenu = true }) else null,
                    groups = groups,
                    // Gibber Settings sits as a small button at the top right of the Dev page.
                    titleAction = if (page.name == "Dev") ({
                        Text(
                            "⚙",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { settingsOpen = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }) else null,
                    onGroupsChange = { groups = it; prefs.groups = it },
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
            }

            // Dots for the pages, at the bottom.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 6.dp),
            ) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == pager.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.95f else 0.45f))
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                    )
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
                    resumes = resumes,
                    onAskMove = { moving = it },
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
                androidx.compose.runtime.key(playing.url) {
                    GameScreen(page = playing, hubUrl = hubUrl, onClose = { gamePage = null })
                }
            }
            if (settingsOpen) {
                SettingsScreen(
                    hubUrl = hubUrl,
                    onSaveUrl = ::saveHubUrl,
                    resumes = resumes,
                    onClose = { settingsOpen = false },
                )
            }
            if (wallMenu) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { wallMenu = false },
                    title = { Text("Home screen") },
                    text = {
                        androidx.compose.foundation.layout.Column {
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
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { wallMenu = false }) { Text("Cancel") }
                    },
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

