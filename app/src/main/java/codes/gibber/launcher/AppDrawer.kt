package codes.gibber.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A line of the list: a letter's heading, or an app. */
private sealed class DrawerLine(val key: String) {
    class Letter(val c: Char) : DrawerLine("letter-$c")
    class App(val app: AppEntry) : DrawerLine(app.key)
}

/** All apps: every app A to Z, with search, the Update button and the alphabet down the right side. Swipe down to close. */
@Composable
fun AppDrawer(
    apps: List<AppEntry>,
    favorites: List<String>,
    onFavoritesChange: (List<String>) -> Unit,
    onClose: () -> Unit,
    onAppsChanged: () -> Unit = {},
    resumes: Int = 0,
    seenBuild: Int = 0,
    onAskMove: (AppEntry) -> Unit = {},
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val lines = remember(shown, query) {
        if (query.isNotBlank()) shown.map { DrawerLine.App(it) }
        else buildList<DrawerLine> {
            var last: Char? = null
            shown.forEach { a ->
                if (a.letter != last) { add(DrawerLine.Letter(a.letter)); last = a.letter }
                add(DrawerLine.App(a))
            }
        }
    }
    val letters = remember(shown) { shown.map { it.letter }.distinct() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun jumpTo(letter: Char) {
        val index = lines.indexOfFirst { it is DrawerLine.Letter && it.c == letter }
        if (index >= 0) scope.launch { listState.scrollToItem(index) }
    }

    // Swiping down closes the list, when it's at the top or the swipe starts above the list.
    // It watches the touch without taking it, so scrolling the list works as before.
    val density = LocalDensity.current
    val swipeDownPx = with(density) { 90.dp.toPx() }
    val stripPx = with(density) { 30.dp.toPx() }
    var listTop by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .pointerInput(onClose) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val onStrip = down.position.x > size.width - stripPx
                    val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    val above = down.position.y < listTop
                    if (onStrip || !(atTop || above)) return@awaitEachGesture
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dy = change.position.y - down.position.y
                        val dx = change.position.x - down.position.x
                        if (dy < -viewConfiguration.touchSlop) break
                        if (dy > swipeDownPx && dy > 2 * abs(dx)) {
                            onClose()
                            break
                        }
                    }
                }
            }
    ) {
        OrbBackground()
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(start = 22.dp, top = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 22.dp)) {
                Box(Modifier.weight(1f)) { BackLink("Your apps", onClose) }
                UpdateButton(resumes = resumes)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(end = 22.dp).fillMaxWidth().height(46.dp).frost().padding(horizontal = 14.dp),
            ) {
                Glyph(Glyphs.Search, 18.dp, Dark.W62)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps", color = Dark.W62, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let { Apps.launch(context, it) } }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (seenBuild > 0) {
                Text("Gibber Launcher build ${BuildConfig.VERSION_CODE}", color = Dark.W40, fontSize = 12.sp)
            }
            Row(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { listTop = it.positionInRoot().y }
            ) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    items(lines.size, key = { lines[it].key }) { i ->
                        when (val line = lines[i]) {
                            is DrawerLine.Letter -> Text(
                                line.c.toString(),
                                color = Dark.W40,
                                fontFamily = Dark.Mono,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            is DrawerLine.App -> DrawerRow(
                                app = line.app,
                                actions = buildList {
                                    val app = line.app
                                    add(RowAction("Move to a section") { onAskMove(app) })
                                    if (Shortcuts.isShortcut(app)) add(RowAction("Remove shortcut") {
                                        Shortcuts.remove(context, app)
                                        onFavoritesChange(favorites - app.key)
                                        onAppsChanged()
                                    })
                                    if (Apps.canUninstall(context, app)) add(RowAction("Uninstall") { Apps.uninstall(context, app) })
                                },
                            )
                        }
                    }
                    item { Box(Modifier.height(28.dp)) }
                }
                if (query.isBlank() && letters.size > 1) AlphabetStrip(letters = letters, onLetter = ::jumpTo)
            }
        }
    }
}

/** One app in the list: its tile and its name. Tap opens it; hold opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(app: AppEntry, actions: List<RowAction>) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .combinedClickable(onClick = { Apps.launch(context, app) }, onLongClick = { menuOpen = true }),
        ) {
            AppTile(app, 36.dp, corner = 10.dp)
            Text(app.label, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HoldMenu(menuOpen, actions) { menuOpen = false }
    }
}

@Composable
private fun AlphabetStrip(letters: List<Char>, onLetter: (Char) -> Unit) {
    fun letterAt(y: Float, height: Int): Char {
        val i = ((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        return letters[i]
    }
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxHeight(0.8f)
            .width(22.dp)
            .pointerInput(letters) { detectTapGestures { offset -> onLetter(letterAt(offset.y, size.height)) } }
            .pointerInput(letters) {
                detectVerticalDragGestures { change, _ -> onLetter(letterAt(change.position.y, size.height)) }
            },
    ) {
        letters.forEach { c -> Text(c.toString(), color = Dark.W62, fontFamily = Dark.Mono, fontSize = 9.sp) }
    }
}
