package codes.gibber.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
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
fun PageScreen(
    page: LauncherPage,
    apps: List<AppEntry>,
    onOpenDrawer: () -> Unit,
    moved: Map<String, String>,
    hubProjects: Set<String>,
    onAskMove: (AppEntry) -> Unit,
    onRemove: (AppEntry) -> Unit = {},
    onLongPressWallpaper: (() -> Unit)? = null,
    groups: List<AppGroup> = emptyList(),
    onGroupsChange: (List<AppGroup>) -> Unit = {},
    widgets: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    // Holding an app opens its own menu; this keeps that hold from also opening the wallpaper's menu.
    val lastAppHold = remember { longArrayOf(0L) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val cells = remember(apps, groups, moved, hubProjects) { Groups.cells(apps, groups, moved, hubProjects) }

    // Edit mode for one section: tick apps, then remove or merge them all at once.
    var editing by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var mergeFor by remember { mutableStateOf<AppEntry?>(null) }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<AppGroup?>(null) }

    fun membersOf(c: Cell): List<AppEntry> = when (c) {
        is Cell.One -> listOf(c.app)
        is Cell.Many -> c.apps
    }
    fun newGroupName(section: String, first: AppEntry): String =
        if (groups.none { it.name == section }) section else first.label
    fun merge(chosen: List<Cell>, section: String) {
        val keys = chosen.flatMap { c -> membersOf(c).map { it.key } }.distinct()
        if (keys.size < 2) return
        val oldIds = chosen.filterIsInstance<Cell.Many>().map { it.group.id }.toSet()
        val keep = chosen.filterIsInstance<Cell.Many>().firstOrNull()?.group
        val group = AppGroup(
            id = keep?.id ?: System.currentTimeMillis().toString(),
            name = keep?.name ?: newGroupName(section, membersOf(chosen.first()).first()),
            keys = keys,
        )
        onGroupsChange(groups.filter { it.id !in oldIds } + group)
    }
    fun takeOut(group: AppGroup, app: AppEntry) {
        val left = group.keys - app.key
        onGroupsChange(if (left.size < 2) groups - group else groups.map { if (it.id == group.id) it.copy(keys = left) else it })
    }

    // Swiping up opens the app list: on empty space, or past the end of the page.
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
        add(RowAction("Remove from this card") { onRemove(app) })
        add(RowAction("Merge with another app") { mergeFor = app })
        add(RowAction("Move to a section") { onAskMove(app) })
        if (!Shortcuts.isShortcut(app) && !HubLink.isOwn(app)) add(RowAction("App info") { Apps.openInfo(context, app) })
    }

    Column(
        Modifier
            .fillMaxSize()
            .then(if (page.workspace) Modifier.background(Color.Black) else Modifier)
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
            .then(
                if (onLongPressWallpaper == null) Modifier
                else Modifier.pointerInput(onLongPressWallpaper) {
                    detectTapGestures(onLongPress = {
                        scope.launch {
                            kotlinx.coroutines.delay(150)
                            if (System.currentTimeMillis() - lastAppHold[0] > 1_000) onLongPressWallpaper()
                        }
                    })
                }
            )
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (page.workspace) {
            Text(
                page.name,
                color = Color.White,
                fontSize = 30.sp,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 12.dp),
            )
        }

        // On Home the cards keep to a slim column on the left, so the wallpaper shows on the right.
        val slim = !page.workspace
        val perRow = if (slim) 2 else 4
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
        ) {
            if (!page.workspace) item(key = "widgets") { widgets() }
            page.sections.forEach { name ->
                val inIt = cells[name].orEmpty()
                // Home shows only sections with apps; a workspace shows all of its sections, so you can fill them.
                if (inIt.isNotEmpty() || page.workspace) {
                    item(key = "section-$name") {
                        val isEditing = editing == name
                        SectionBox(
                            name,
                            solid = page.workspace,
                            slim = slim,
                            onHoldTitle = { if (inIt.isNotEmpty()) { editing = name; selected = emptySet() } },
                        ) {
                            if (isEditing) {
                                val chosen = inIt.filter { it.key in selected }
                                EditBar(
                                    count = chosen.size,
                                    canMerge = chosen.sumOf { membersOf(it).size } >= 2,
                                    onRemove = {
                                        chosen.flatMap { membersOf(it) }.forEach(onRemove)
                                        selected = emptySet()
                                        editing = null
                                    },
                                    onMerge = {
                                        merge(chosen, name)
                                        selected = emptySet()
                                        editing = null
                                    },
                                    onDone = { selected = emptySet(); editing = null },
                                )
                            }
                            if (inIt.isEmpty()) {
                                Text(
                                    "Empty. Hold any app and move it here.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                                )
                            }
                            inIt.chunked(perRow).forEach { row ->
                                Row(Modifier.fillMaxWidth()) {
                                    row.forEach { cell ->
                                        androidx.compose.runtime.key(cell.key) {
                                            Box(Modifier.weight(1f)) {
                                                val hold = { lastAppHold[0] = System.currentTimeMillis() }
                                                when (cell) {
                                                    is Cell.One -> AppCell(cell.app, actionsFor(cell.app), Modifier.fillMaxWidth(), slim, hold)
                                                    is Cell.Many -> GroupCell(
                                                        cell = cell,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        slim = slim,
                                                        onOpen = { openGroup = cell.group.id },
                                                        onHold = hold,
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
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(if (on) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                                                            .clickable { selected = if (on) selected - cell.key else selected + cell.key }
                                                    ) {
                                                        Text(
                                                            if (on) "✓" else "○",
                                                            color = Color.White,
                                                            fontSize = 16.sp,
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(4.dp)
                                                                .clip(RoundedCornerShape(50))
                                                                .background(if (on) Color(0xFF2E7DFF) else Color.Black.copy(alpha = 0.55f))
                                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    mergeFor?.let { app ->
        val pageCells = page.sections.flatMap { cells[it].orEmpty() }
        val mine = pageCells.firstOrNull { c -> membersOf(c).any { it.key == app.key } }
        MergeDialog(
            app = app,
            choices = pageCells.filter { it != mine },
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
            onSave = { name -> onGroupsChange(groups.map { if (it.id == g.id) it.copy(name = name) else it }) },
            onDismiss = { renaming = null },
        )
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
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        letterSpacing = 1.5.sp,
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A soft dark shadow so white text reads over any wallpaper. */
private val ReadableShadow = androidx.compose.ui.graphics.Shadow(
    color = Color.Black.copy(alpha = 0.9f),
    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
    blurRadius = 6f,
)

/** One section: black (see-through on Home), with a thin white line round it, like the wireframe. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SectionBox(
    name: String,
    solid: Boolean,
    slim: Boolean,
    onHoldTitle: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth(if (slim) 0.42f else 1f)
            .then(
                // On Home there's no box at all: the apps sit right on your wallpaper.
                if (solid) Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                else Modifier
            )
            .padding(horizontal = 8.dp, vertical = if (solid) 10.dp else 2.dp)
    ) {
        Text(
            name.uppercase(),
            color = Color.White,
            fontSize = 12.sp,
            letterSpacing = 1.5.sp,
            style = androidx.compose.ui.text.TextStyle(shadow = ReadableShadow),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = {}, onLongClick = onHoldTitle)
                .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 6.dp),
        )
        content()
    }
}

/** An app as an icon with its name under it. Tap opens it; hold opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(
    app: AppEntry,
    actions: List<RowAction>,
    modifier: Modifier,
    slim: Boolean = false,
    onHold: () -> Unit = {},
) {
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
                    onLongClick = { onHold(); menuOpen = true },
                )
                .padding(vertical = 8.dp, horizontal = 2.dp),
        ) {
            AppIcon(app, if (slim) 40.dp else 44.dp)
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                color = Color.White,
                fontSize = if (slim) 10.sp else 11.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = ReadableShadow),
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


/** The bar edit mode shows under a section's name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditBar(count: Int, canMerge: Boolean, onRemove: () -> Unit, onMerge: () -> Unit, onDone: () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
    ) {
        if (count > 0) Chip("Remove $count", strong = true, onClick = onRemove)
        if (canMerge) Chip("Merge", onClick = onMerge)
        Chip("Done", onClick = onDone)
    }
}
