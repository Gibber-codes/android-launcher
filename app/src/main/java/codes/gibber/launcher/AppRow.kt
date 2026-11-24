package codes.gibber.launcher

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val iconCache = LruCache<String, Bitmap>(300)

@Composable
fun AppIcon(app: AppEntry, size: Dp, mono: Boolean = false) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    // A game's picture can arrive later, so its version is part of the key.
    val useMono = mono && !HubGames.isGame(app)
    val cacheKey = if (HubGames.isGame(app)) "${app.key}@$px#${GameIcons.version}" else "${app.key}@$px${if (useMono) "~mono" else ""}"
    // Keyed on the app: when a spot is reused for another app (after one is removed),
    // it must show that app's icon, never the old one's.
    val bitmap by produceState<Bitmap?>(initialValue = iconCache.get(cacheKey), cacheKey) {
        value = iconCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
            ((if (useMono) Look.monoIcon(context, app, px) else null) ?: Apps.loadIcon(context, app, px))
                ?.also { iconCache.put(cacheKey, it) }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size))
    }
}

/** A menu item shown when an app is held down. */
class RowAction(val label: String, val onClick: () -> Unit)

/** One app: icon and name. Tap opens it; hold opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    app: AppEntry,
    textSize: TextUnit,
    iconSize: Dp,
    actions: List<RowAction>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { Apps.launch(context, app) },
                    onLongClick = { menuOpen = true },
                )
                .padding(vertical = 10.dp, horizontal = 4.dp),
        ) {
            AppIcon(app, iconSize)
            Spacer(Modifier.width(16.dp))
            Text(app.label, color = Color.White, fontSize = textSize, maxLines = 1)
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
