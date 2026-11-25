package codes.gibber.launcher

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

/**
 * An app as the design draws it: its whole icon filling a rounded square, like a phone's own home
 * screen. Android's adaptive icons are drawn layer by layer so their colour reaches the corners.
 */
@Composable
fun AppTile(app: AppEntry, size: Dp, corner: Dp = 14.dp, badge: Int = 0) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val cacheKey = "${app.key}@$px~tile#${if (HubGames.isGame(app)) GameIcons.version else 0}"
    val bitmap by produceState<Bitmap?>(initialValue = iconCache.get(cacheKey), cacheKey) {
        value = iconCache.get(cacheKey) ?: withContext(Dispatchers.IO) {
            tileBitmap(context, app, px)?.also { iconCache.put(cacheKey, it) }
        }
    }
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(corner))
                .background(if (HubLink.isIdeas(app)) Color(0xFFF2C230) else Color(0xFF16121F)),
        ) {
            val bmp = bitmap
            when {
                HubLink.isIdeas(app) -> Glyph(Glyphs.Bulb, size * 0.5f, Color(0xFF222222))
                bmp != null -> Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(size))
            }
        }
        if (badge > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 5.dp, y = (-5).dp)
                    .defaultMinSize(minWidth = 18.dp)
                    .height(18.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                    .background(Color.White)
                    .padding(horizontal = 4.dp),
            ) {
                Text(if (badge > 99) "99+" else "$badge", color = Color(0xFF111111), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun tileBitmap(context: android.content.Context, app: AppEntry, px: Int): Bitmap? {
    if (HubGames.isGame(app) || HubLink.isOwn(app) || Shortcuts.isShortcut(app)) return Apps.loadIcon(context, app, px)
    val d = Apps.iconDrawable(context, app) ?: return null
    return try {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        if (d is android.graphics.drawable.AdaptiveIconDrawable) {
            // A layer is 108 units with the middle 72 shown, so it's drawn half as big again, centred.
            val inset = px / 4
            d.background?.let { it.setBounds(-inset, -inset, px + inset, px + inset); it.draw(canvas) }
            d.foreground?.let { it.setBounds(-inset, -inset, px + inset, px + inset); it.draw(canvas) }
        } else {
            canvas.drawColor(android.graphics.Color.rgb(42, 37, 48))
            val pad = px / 8
            d.setBounds(pad, pad, px - pad, px - pad)
            d.draw(canvas)
        }
        bmp
    } catch (e: Exception) {
        Apps.loadIcon(context, app, px)
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
