package codes.gibber.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Swiping up opens the full app list: on empty space, or past the end of a page that scrolls. */
@Composable
fun rememberSwipeUp(onOpen: () -> Unit): Modifier {
    val swipePx = with(LocalDensity.current) { 80.dp.toPx() }
    val past = remember(onOpen) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0) pulled -= available.y else if (consumed.y != 0f) pulled = 0f
                if (pulled > swipePx) {
                    pulled = 0f
                    onOpen()
                }
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pulled = 0f
                if (available.y < -1500f) onOpen()
                return Velocity.Zero
            }
        }
    }
    return Modifier
        .nestedScroll(past)
        .pointerInput(onOpen) {
            var pulled = 0f
            detectVerticalDragGestures(
                onDragStart = { pulled = 0f },
                onVerticalDrag = { _, dy ->
                    pulled -= dy
                    if (pulled > swipePx) {
                        pulled = Float.NEGATIVE_INFINITY
                        onOpen()
                    }
                },
            )
        }
}

/**
 * A page's sections: each one's name, then its apps four to a row. Hold an app for its menu; hold
 * a section's name for edit mode, where you tick apps and remove or merge them all at once.
 * Sections named in `lineIcons` show the apps' one-colour line icons, like the design's Dev tools.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionsArea(
    sections: List<String>,
    cells: Map<String, List<Cell>>,
    groups: List<AppGroup>,
    onGroupsChange: (List<AppGroup>) -> Unit,
    moved: Map<String, String>,
    hubProjects: Set<String>,
    onAskMove: (AppEntry) -> Unit,
    onRemove: (AppEntry) -> Unit,
    onHold: () -> Unit = {},
    showEmpty: Boolean = true,
    lineIcons: Set<String> = setOf("Dev tools"),
) {
    var editing by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mergeFor by remember { mutableStateOf<AppEntry?>(null) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<AppGroup?>(null) }
    val context = LocalContext.current

    fun membersOf(c: Cell): List<AppEntry> = when (c) {
        is Cell.One -> listOf(c.app)
        is Cell.Many -> c.apps
    }
    fun merge(chosen: List<Cell>, section: String) {
        val keys = chosen.flatMap { c -> membersOf(c).map { it.key } }.distinct()
        if (keys.size < 2) return
        val oldIds = chosen.filterIsInstance<Cell.Many>().map { it.group.id }.toSet()
        val keep = chosen.filterIsInstance<Cell.Many>().firstOrNull()?.group
        val first = membersOf(chosen.first()).first()
        val group = AppGroup(
            id = keep?.id ?: System.currentTimeMillis().toString(),
            name = keep?.name ?: if (groups.none { it.name == section }) section else first.label,
            keys = keys,
        )
        onGroupsChange(groups.filter { it.id !in oldIds } + group)
    }
    fun takeOut(group: AppGroup, app: AppEntry) {
        val left = group.keys - app.key
        onGroupsChange(if (left.size < 2) groups - group else groups.map { if (it.id == group.id) it.copy(keys = left) else it })
    }
    fun actionsFor(app: AppEntry): List<RowAction> = buildList {
        add(RowAction("Move to a section") { onAskMove(app) })
        add(RowAction("Merge with another app") { mergeFor = app })
        add(RowAction("Remove from this card") { onRemove(app) })
        if (Apps.canUninstall(context, app)) add(RowAction("Uninstall") { Apps.uninstall(context, app) })
    }

    Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
        sections.forEach { name ->
            val inIt = cells[name].orEmpty()
            if (inIt.isEmpty() && !showEmpty) return@forEach
            val isEditing = editing == name
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Head(
                    if (isEditing) "$name · editing" else name,
                    onHold = { if (inIt.isNotEmpty()) { editing = name; selected = emptySet() } },
                )
                if (isEditing) {
                    val chosen = inIt.filter { it.key in selected }
                    EditBar(
                        count = chosen.size,
                        canMerge = chosen.sumOf { membersOf(it).size } >= 2,
                        onRemove = {
                            chosen.flatMap { membersOf(it) }.forEach(onRemove)
                            selected = emptySet(); editing = null
                        },
                        onMerge = { merge(chosen, name); selected = emptySet(); editing = null },
                        onDone = { selected = emptySet(); editing = null },
                    )
                }
                if (inIt.isEmpty()) Text("Empty. Hold any app and move it here.", color = Dark.W62, fontSize = 12.sp)
                inIt.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { cell ->
                            androidx.compose.runtime.key(cell.key) {
                                Box(Modifier.weight(1f)) {
                                    when (cell) {
                                        is Cell.One -> AppCell(cell.app, actionsFor(cell.app), line = name in lineIcons, onHold = onHold)
                                        is Cell.Many -> GroupCell(
                                            cell = cell,
                                            onOpen = { openGroup = cell.group.id },
                                            onHold = onHold,
                                            actions = listOf(
                                                RowAction("Rename group") { renaming = cell.group },
                                                RowAction("Ungroup") { onGroupsChange(groups - cell.group) },
                                                RowAction("Move to a section") { onAskMove(cell.apps.first()) },
                                                RowAction("Remove from this card") { cell.apps.forEach(onRemove) },
                                            ),
                                        )
                                    }
                                    if (isEditing) {
                                        val on = cell.key in selected
                                        Box(
                                            Modifier
                                                .matchParentSize()
                                                .clickable { selected = if (on) selected - cell.key else selected + cell.key }
                                        ) {
                                            Tick(on, Modifier.align(Alignment.TopCenter).offset(x = 26.dp, y = (-6).dp))
                                        }
                                    }
                                }
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }

    mergeFor?.let { app ->
        val all = sections.flatMap { cells[it].orEmpty() }
        val mine = all.firstOrNull { c -> membersOf(c).any { it.key == app.key } }
        MergeDialog(
            app = app,
            choices = all.filter { it != mine },
            onPick = { other ->
                merge(listOfNotNull(mine ?: Cell.One(app), other).let { l -> if (other is Cell.Many) l.reversed() else l },
                    Sections.of(app, moved, hubProjects))
            },
            onDismiss = { mergeFor = null },
        )
    }
    openGroup?.let { id ->
        val cell = cells.values.flatten().filterIsInstance<Cell.Many>().firstOrNull { it.group.id == id }
        if (cell == null) openGroup = null
        else GroupDialog(cell = cell, onTakeOut = { takeOut(cell.group, it) }, onDismiss = { openGroup = null })
    }
    renaming?.let { g ->
        RenameDialog(
            current = g.name,
            onSave = { n -> onGroupsChange(groups.map { if (it.id == g.id) it.copy(name = n) else it }) },
            onDismiss = { renaming = null },
        )
    }
}

/** Edit mode's tick: an empty ring, or white with a check. */
@Composable
private fun Tick(on: Boolean, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (on) Color.White else Color.Black.copy(alpha = 0.35f))
            .border(1.5.dp, Color.White, CircleShape),
    ) {
        if (on) Glyph(Glyphs.Check, 12.dp, Color(0xFF111111))
    }
}

/** One app in a grid: its tile (or line icon) and its name. Tap opens it; hold opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(app: AppEntry, actions: List<RowAction>, line: Boolean = false, onHold: () -> Unit = {}) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (line) 9.dp else 7.dp),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { Apps.launch(context, app) },
                    onLongClick = { onHold(); menuOpen = true },
                ),
        ) {
            if (line && !HubGames.isGame(app)) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { AppIcon(app, 30.dp, mono = true) }
            } else {
                AppTile(app, 52.dp, badge = Badges.counts[app.packageName] ?: 0)
            }
            Text(
                app.label,
                color = Color.White,
                fontSize = 12.sp,
                style = Dark.UnderIcon,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        HoldMenu(menuOpen, actions) { menuOpen = false }
    }
}

/** The menu you get by holding an app, in the design's dark pane. */
@Composable
fun HoldMenu(open: Boolean, actions: List<RowAction>, onDismiss: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        actions.forEach { action ->
            val glyph = when {
                action.label.startsWith("Move") -> Glyphs.Move
                action.label.startsWith("Merge") -> Glyphs.Merge
                action.label.startsWith("Uninstall") -> Glyphs.Trash
                action.label.startsWith("Remove") -> Glyphs.Remove
                else -> null
            }
            DropdownMenuItem(
                text = { Text(action.label, color = Color.White, fontSize = 14.sp) },
                leadingIcon = glyph?.let { g -> { Glyph(g, 18.dp, Color.White) } },
                onClick = { onDismiss(); action.onClick() },
            )
        }
    }
}

/** The bar edit mode shows under a section's name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditBar(count: Int, canMerge: Boolean, onRemove: () -> Unit, onMerge: () -> Unit, onDone: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (count > 0) FrostButton("Remove $count", hi = true, onClick = onRemove)
        if (canMerge) FrostButton("Merge", onClick = onMerge)
        FrostButton("Done", onClick = onDone)
    }
}

/** Where an app can go: every section on every page, or hidden from the pages. */
@Composable
fun MoveDialog(
    app: AppEntry,
    moved: Map<String, String>,
    hubProjects: Set<String>,
    onMove: (AppEntry, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = Sections.of(app, moved, hubProjects)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${app.label}") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Sections.PAGES.forEach { page ->
                    Text(
                        page.name.uppercase(),
                        color = Dark.W62,
                        fontFamily = Dark.Mono,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                    page.sections.forEach { name ->
                        Text(
                            if (name == current) "$name  (here now)" else name,
                            color = Color.White,
                            fontSize = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMove(app, name); onDismiss() }
                                .padding(vertical = 9.dp),
                        )
                    }
                }
                Text(
                    if (current == Sections.HIDDEN) "Hidden  (here now)" else "Hide it (stays in the app list)",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMove(app, Sections.HIDDEN); onDismiss() }
                        .padding(top = 14.dp, bottom = 9.dp),
                )
                if (app.key in moved) {
                    Text(
                        "Put it back where it started",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMove(app, null); onDismiss() }
                            .padding(vertical = 9.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}

/** An app tile's rounded square. */
internal val TileShape = RoundedCornerShape(14.dp)
