package codes.gibber.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    onOpenHub: () -> Unit,
    resumes: Int,
) {
    val context = LocalContext.current
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val favApps = favorites.mapNotNull { byKey[it] }

    // Swiping up opens the app list: on empty space, or past the end of the favorites list.
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
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        if (favApps.isEmpty()) {
            Text(
                "Swipe up for all apps, and hold one to add it here.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
            )
        }

        LazyColumn(Modifier.weight(1f)) {
            items(favApps, key = { it.key }) { app ->
                val i = favorites.indexOf(app.key)
                AppRow(
                    app = app,
                    textSize = 24.sp,
                    iconSize = 32.dp,
                    actions = buildList {
                        if (i > 0) add(RowAction("Move up") { onFavoritesChange(favorites.swap(i, i - 1)) })
                        if (i < favorites.lastIndex) add(RowAction("Move down") { onFavoritesChange(favorites.swap(i, i + 1)) })
                        add(RowAction("Remove from home") { onFavoritesChange(favorites - app.key) })
                        if (!Shortcuts.isShortcut(app)) add(RowAction("App info") { Apps.openInfo(context, app) })
                    },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "All apps",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(onClick = onOpenDrawer)
                    .padding(vertical = 12.dp),
            )
            UpdateButton(resumes = resumes)
            Text(
                "Gibber Hub  ›",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(onClick = onOpenHub)
                    .padding(vertical = 12.dp),
            )
        }
    }
}


private fun List<String>.swap(a: Int, b: Int): List<String> =
    toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }
