package codes.gibber.launcher

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    favorites: List<String>,
    onFavoritesChange: (List<String>) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenHub: () -> Unit,
) {
    val context = LocalContext.current
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val favApps = favorites.mapNotNull { byKey[it] }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Clock()
        Spacer(Modifier.height(32.dp))

        if (favApps.isEmpty()) {
            Text(
                "Open All apps and hold an app to add it here.",
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
                        add(RowAction("App info") { Apps.openInfo(context, app) })
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

@Composable
private fun Clock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    val time = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now))
    Column {
        Text(time, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
        Text(date, color = Color.White.copy(alpha = 0.8f), fontSize = 18.sp)
    }
}

private fun List<String>.swap(a: Int, b: Int): List<String> =
    toMutableList().also { val t = it[a]; it[a] = it[b]; it[b] = t }
