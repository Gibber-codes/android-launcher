package codes.gibber.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where the home screen's menu goes. */
enum class Place { APPS, GAMES, HUB, DEV }

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    favorites: List<String>,
    onFavoritesChange: (List<String>) -> Unit,
    onGo: (Place) -> Unit,
    onChangeDev: () -> Unit,
    resumes: Int,
) {
    val context = LocalContext.current
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val favApps = favorites.mapNotNull { byKey[it] }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Clock(Modifier.weight(1f))
            UpdateButton(resumes = resumes, modifier = Modifier.padding(top = 14.dp))
        }
        Spacer(Modifier.height(24.dp))

        // The favorites, on a pane of the hub's glass sized to what it holds; the menu stays at the foot.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .hubGlass()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
            ) {
                HubLabel("Favorites")
                if (favApps.isEmpty()) {
                    Text(
                        "Open Apps and hold an app to add it here.",
                        color = Hub.Muted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                LazyColumn {
                    items(favApps, key = { it.key }) { app ->
                        val i = favorites.indexOf(app.key)
                        AppRow(
                            app = app,
                            textSize = 21.sp,
                            iconSize = 34.dp,
                            actions = buildList {
                                if (i > 0) add(RowAction("Move up") { onFavoritesChange(favorites.swap(i, i - 1)) })
                                if (i < favorites.lastIndex) add(RowAction("Move down") { onFavoritesChange(favorites.swap(i, i + 1)) })
                                add(RowAction("Remove from home") { onFavoritesChange(favorites - app.key) })
                                add(RowAction("App info") { Apps.openInfo(context, app) })
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HubLabel("Go to", Modifier.padding(start = 2.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NavTile("Apps", Icons.AutoMirrored.Filled.List, Modifier.weight(1f)) { onGo(Place.APPS) }
            NavTile("Games", Icons.Filled.PlayArrow, Modifier.weight(1f)) { onGo(Place.GAMES) }
            NavTile("Hub", Icons.Filled.Home, Modifier.weight(1f)) { onGo(Place.HUB) }
            NavTile("Dev", Icons.Filled.Build, Modifier.weight(1f), onHold = onChangeDev) { onGo(Place.DEV) }
        }
    }
}

/** One square of the menu: a glyph over its name, on the hub's glass. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NavTile(
    name: String,
    glyph: ImageVector,
    modifier: Modifier = Modifier,
    onHold: (() -> Unit)? = null,
    onTap: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .hubGlass()
            .combinedClickable(onClick = onTap, onLongClick = onHold)
            .padding(vertical = 14.dp),
    ) {
        Icon(glyph, contentDescription = null, tint = Hub.Primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(name, color = Hub.Text, fontSize = 14.sp)
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    val time = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now))
    Column(modifier) {
        Text(time, color = Hub.Text, fontSize = 64.sp, fontWeight = FontWeight.Light)
        Text(date, color = Hub.Text.copy(alpha = 0.8f), fontSize = 17.sp)
    }
}

private fun List<String>.swap(a: Int, b: Int): List<String> =
    toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }
