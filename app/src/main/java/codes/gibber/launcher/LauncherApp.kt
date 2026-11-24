package codes.gibber.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

const val PAGE_HOME = 0
const val PAGE_HUB = 1

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
        val pager = rememberPagerState(initialPage = PAGE_HOME, pageCount = { 2 })
        val scope = rememberCoroutineScope()
        var drawerOpen by remember { mutableStateOf(false) }
        var favorites by remember { mutableStateOf(prefs.favorites) }

        fun saveFavorites(list: List<String>) {
            favorites = list
            prefs.favorites = list
        }

        // Gibber Hub is an app in the list; opening it slides over to the hub page.
        val context = androidx.compose.ui.platform.LocalContext.current
        var hubUrl by remember { mutableStateOf(normalizeHubUrl(prefs.hubUrl)) }
        var settingsOpen by remember { mutableStateOf(false) }
        var devMode by remember { mutableStateOf(prefs.devMode) }
        var moved by remember { mutableStateOf(prefs.sections) }
        var hubProjects by remember { mutableStateOf(prefs.hubProjects) }
        var gameMenu by remember { mutableStateOf<String?>(null) }
        var gamePage by remember { mutableStateOf<GamePage?>(null) }
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
            HubLink.open = {
                drawerOpen = false
                scope.launch { pager.animateScrollToPage(PAGE_HUB) }
            }
        }
        // The first time, put Gibber Hub on the home screen, since the bottom bar's link is gone.
        LaunchedEffect(Unit) {
            if (!prefs.hubPlaced) {
                val key = HubLink.key(context)
                if (key !in favorites) saveFavorites(favorites + key)
                prefs.hubPlaced = true
            }
        }

        // Keep the hub's games on the home screen up to date, whenever you come back to it.
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

        // Pressing Home always brings you back to the favorites.
        LaunchedEffect(homePresses) {
            if (homePresses > 0) {
                drawerOpen = false
                gameMenu = null
                gamePage = null
                pager.animateScrollToPage(PAGE_HOME)
            }
        }
        // Coming back from an app closes the drawer.
        LaunchedEffect(resumes) { drawerOpen = false }

        // A home screen has nowhere to go back to, so Back does nothing here.
        BackHandler(enabled = true) { }
        BackHandler(enabled = drawerOpen) { drawerOpen = false }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
        ) {
            HorizontalPager(
                state = pager,
                // The hub page keeps its own sideways swipes; use its Home button to come back.
                userScrollEnabled = pager.currentPage == PAGE_HOME,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    PAGE_HOME -> HomeScreen(
                        apps = apps,
                        favorites = favorites,
                        onFavoritesChange = ::saveFavorites,
                        onOpenDrawer = { drawerOpen = true },
                        moved = moved,
                        hubProjects = hubProjects,
                        onMove = { app, section ->
                            moved = if (section == null) moved - app.key else moved + (app.key to section)
                            prefs.sections = moved
                        },
                        devMode = devMode,
                        onDevMode = { devMode = it; prefs.devMode = it },
                    )
                    PAGE_HUB -> HubScreen(
                        hubUrl = hubUrl,
                        onSaveUrl = ::saveHubUrl,
                        isShown = pager.currentPage == PAGE_HUB,
                        onGoHome = { scope.launch { pager.animateScrollToPage(PAGE_HOME) } },
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
        }
    }
}
