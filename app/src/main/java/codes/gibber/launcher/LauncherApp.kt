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
import androidx.compose.ui.platform.LocalContext
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
) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val pager = rememberPagerState(initialPage = PAGE_HOME, pageCount = { 2 })
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        // The list open over the home screen, Apps or Games, or none.
        var panel by remember { mutableStateOf<Place?>(null) }
        val drawerOpen = panel != null
        var favorites by remember { mutableStateOf(prefs.favorites) }
        var gamesAdded by remember { mutableStateOf(prefs.gamesAdded) }
        var gamesRemoved by remember { mutableStateOf(prefs.gamesRemoved) }
        var askDev by remember { mutableStateOf(false) }

        fun setGame(app: AppEntry, on: Boolean) {
            gamesAdded = if (on) gamesAdded + app.key else gamesAdded - app.key
            gamesRemoved = if (on) gamesRemoved - app.key else gamesRemoved + app.key
            prefs.gamesAdded = gamesAdded
            prefs.gamesRemoved = gamesRemoved
        }

        fun go(place: Place) {
            when (place) {
                Place.APPS, Place.GAMES -> panel = place
                Place.HUB -> scope.launch { pager.animateScrollToPage(PAGE_HUB) }
                Place.DEV -> if (prefs.devUrl.isBlank()) askDev = true else openSite(context, prefs.devUrl)
            }
        }

        fun saveFavorites(list: List<String>) {
            favorites = list
            prefs.favorites = list
        }

        // Pressing Home always brings you back to the favorites.
        LaunchedEffect(homePresses) {
            if (homePresses > 0) {
                panel = null
                pager.animateScrollToPage(PAGE_HOME)
            }
        }
        // Coming back from an app closes the drawer.
        LaunchedEffect(resumes) { panel = null }

        // A home screen has nowhere to go back to, so Back does nothing here.
        BackHandler(enabled = true) { }
        BackHandler(enabled = drawerOpen) { panel = null }

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
                        onGo = ::go,
                        onChangeDev = { askDev = true },
                        resumes = resumes,
                    )
                    PAGE_HUB -> HubScreen(
                        prefs = prefs,
                        isShown = pager.currentPage == PAGE_HUB,
                        onGoHome = { scope.launch { pager.animateScrollToPage(PAGE_HOME) } },
                    )
                }
            }

            val games = remember(apps, gamesAdded, gamesRemoved) { Apps.games(apps, gamesAdded, gamesRemoved) }
            val gameKeys = remember(games) { games.map { it.key }.toSet() }
            when (panel) {
                Place.APPS -> AppDrawer(
                    title = "Apps",
                    apps = apps,
                    favorites = favorites,
                    onFavoritesChange = ::saveFavorites,
                    onClose = { panel = null },
                    moreActions = { app ->
                        listOf(
                            if (app.key in gameKeys) RowAction("Take out of Games") { setGame(app, false) }
                            else RowAction("Put in Games") { setGame(app, true) }
                        )
                    },
                )
                Place.GAMES -> AppDrawer(
                    title = "Games",
                    apps = games,
                    favorites = favorites,
                    onFavoritesChange = ::saveFavorites,
                    onClose = { panel = null },
                    emptyText = "No games found. In Apps, hold an app and choose Put in Games.",
                    moreActions = { app -> listOf(RowAction("Take out of Games") { setGame(app, false) }) },
                )
                else -> {}
            }

            if (askDev) {
                DevAddressDialog(
                    current = prefs.devUrl,
                    onSave = { address ->
                        prefs.devUrl = address
                        askDev = false
                        if (address.isNotBlank()) openSite(context, address)
                    },
                    onCancel = { askDev = false },
                )
            }
        }
    }
}
