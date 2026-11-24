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

        var hubUrl by remember { mutableStateOf(normalizeHubUrl(prefs.hubUrl)) }
        var settingsOpen by remember { mutableStateOf(false) }
        var devMode by remember { mutableStateOf(prefs.devMode) }
        var moved by remember { mutableStateOf(prefs.sections) }
        var hubProjects by remember { mutableStateOf(prefs.hubProjects) }
        var gameMenu by remember { mutableStateOf<String?>(null) }
        var gamePage by remember { mutableStateOf<GamePage?>(null) }
        var moving by remember { mutableStateOf<AppEntry?>(null) }
        var widgetIds by remember { mutableStateOf(prefs.widgets) }
        var pickingWidget by remember { mutableStateOf(false) }
        var removingWidget by remember { mutableStateOf(false) }

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
        }

        // Keep the hub's games up to date, whenever you come back to the launcher.
        LaunchedEffect(resumes, hubUrl) {
            if (hubUrl.isBlank()) return@LaunchedEffect
            val found = HubApi(hubUrl).workspaces() ?: return@LaunchedEffect
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
                    topBar = {
                        if (i == 0) {
                            HomeTopBar(
                                hasWidgets = widgetIds.isNotEmpty(),
                                onAddWidget = { pickingWidget = true },
                                onRemoveWidget = { removingWidget = true },
                                devMode = devMode,
                                onDevMode = { devMode = it; prefs.devMode = it },
                            )
                        }
                    },
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
                    apps = apps,
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
                    devMode = devMode,
                    onPlay = { gamePage = it; gameMenu = null },
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

/** The thin row at the top of Home: add or remove a widget, and the dev mode switch. */
@Composable
private fun HomeTopBar(
    hasWidgets: Boolean,
    onAddWidget: () -> Unit,
    onRemoveWidget: () -> Unit,
    devMode: Boolean,
    onDevMode: (Boolean) -> Unit,
) {
    val dim = Color.White.copy(alpha = 0.75f)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text("+ Widget", color = dim, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onAddWidget).padding(8.dp))
        if (hasWidgets) {
            Text("Remove widget", color = dim, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onRemoveWidget).padding(8.dp))
        }
        Spacer(Modifier.weight(1f))
        Text("Dev mode", color = Color.White.copy(alpha = if (devMode) 1f else 0.6f), fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Switch(checked = devMode, onCheckedChange = onDevMode, modifier = Modifier.scale(0.8f))
    }
}
