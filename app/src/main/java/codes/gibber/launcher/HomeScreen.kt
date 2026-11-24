package codes.gibber.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    favorites: List<String>,
    onFavoritesChange: (List<String>) -> Unit,
    onOpenDrawer: () -> Unit,
    moved: Map<String, String>,
    hubProjects: Set<String>,
    onMove: (AppEntry, String?) -> Unit,
    devMode: Boolean,
    onDevMode: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val favApps = favorites.mapNotNull { byKey[it] }
    val grouped = remember(apps, moved, hubProjects) {
        apps.groupBy { Sections.of(it, moved, hubProjects) }
    }
    var moving by remember { mutableStateOf<AppEntry?>(null) }

    // Swiping up opens the app list: on empty space, or past the end of the home screen.
    val swipeUpPx = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
    val openOnSwipeUp = remember(onOpenDrawer) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0) pulled -= available.y else if (consumed.y != 0f) pulled = 0f
                if (pulled > swipeUpPx) {
                    pulled = 0f
                    onOpenDrawer()
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pulled = 0f
                if (available.y < -1500f) onOpenDrawer()
                return Velocity.Zero
            }
        }
    }

    fun actionsFor(app: AppEntry): List<RowAction> = buildList {
        val i = favorites.indexOf(app.key)
        if (i >= 0) {
            if (i > 0) add(RowAction("Move up") { onFavoritesChange(favorites.swap(i, i - 1)) })
            if (i < favorites.lastIndex) add(RowAction("Move down") { onFavoritesChange(favorites.swap(i, i + 1)) })
            add(RowAction("Remove from favorites") { onFavoritesChange(favorites - app.key) })
        } else {
            add(RowAction("Add to favorites") { onFavoritesChange(favorites + app.key) })
        }
        add(RowAction("Move to a section") { moving = app })
        if (!Shortcuts.isShortcut(app) && !HubLink.isOwn(app)) add(RowAction("App info") { Apps.openInfo(context, app) })
    }

    Column(
        Modifier
            .fillMaxSize()
            .nestedScroll(openOnSwipeUp)
            .pointerInput(onOpenDrawer) {
                var pulled = 0f
                detectVerticalDragGestures(
                    onDragStart = { pulled = 0f },
                    onVerticalDrag = { _, dy ->
                        pulled -= dy
                        if (pulled > swipeUpPx) {
                            pulled = Float.NEGATIVE_INFINITY
                            onOpenDrawer()
                        }
                    },
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Spacer(Modifier.weight(1f))
            Text("Dev mode", color = Color.White.copy(alpha = if (devMode) 1f else 0.6f), fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Switch(checked = devMode, onCheckedChange = onDevMode, modifier = Modifier.scale(0.8f))
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (favApps.isNotEmpty()) {
                item(key = "favorites") {
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        favApps.forEach { app ->
                            AppRow(app = app, textSize = 22.sp, iconSize = 30.dp, actions = actionsFor(app))
                        }
                    }
                }
            }
            Sections.ALL.forEach { name ->
                val inIt = grouped[name].orEmpty()
                if (inIt.isNotEmpty()) {
                    item(key = "section-$name") {
                        SectionBox(name) {
                            inIt.chunked(4).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { app -> AppCell(app, actionsFor(app), Modifier.weight(1f)) }
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "hint") {
                Text(
                    "Hold an app to move it to another section. Swipe up for all apps.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }

    val app = moving
    if (app != null) {
        val current = Sections.of(app, moved, hubProjects)
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("Move ${app.label}") },
            text = {
                Column {
                    (Sections.ALL + Sections.HIDDEN).forEach { name ->
                        Text(
                            if (name == current) "$name  (here now)" else name,
                            color = Color.White,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMove(app, name); moving = null }
                                .padding(vertical = 10.dp),
                        )
                    }
                    if (app.key in moved) {
                        Text(
                            "Put it back where it started",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMove(app, null); moving = null }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moving = null }) { Text("Cancel") } },
        )
    }
}

/** One section: black, with a thin white line round it, like the wireframe. */
@Composable
private fun SectionBox(name: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Text(
            name.uppercase(),
            color = Color.White,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        content()
    }
}

/** An app as an icon with its name under it. Tap opens it; hold opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(app: AppEntry, actions: List<RowAction>, modifier: Modifier) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    onClick = { Apps.launch(context, app) },
                    onLongClick = { menuOpen = true },
                )
                .padding(vertical = 8.dp, horizontal = 2.dp),
        ) {
            AppIcon(app, 44.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    onClick = {
                        menuOpen = false
                        action.onClick()
                    },
                )
            }
        }
    }
}


private fun List<String>.swap(a: Int, b: Int): List<String> =
    toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }
