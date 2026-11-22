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

        // Pressing Home always brings you back to the favorites.
        LaunchedEffect(homePresses) {
            if (homePresses > 0) {
                drawerOpen = false
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
                        onOpenHub = { scope.launch { pager.animateScrollToPage(PAGE_HUB) } },
                        resumes = resumes,
                    )
                    PAGE_HUB -> HubScreen(
                        prefs = prefs,
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
                )
            }
        }
    }
}
