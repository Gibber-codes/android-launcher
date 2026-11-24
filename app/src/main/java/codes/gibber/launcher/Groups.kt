package codes.gibber.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Apps merged into one icon, like Phone, Messages and Contacts. */
data class AppGroup(val id: String, val name: String, val keys: List<String>)

/** A spot on a page: one app, or a group of merged apps. */
sealed class Cell {
    abstract val key: String
    data class One(val app: AppEntry) : Cell() { override val key get() = app.key }
    data class Many(val group: AppGroup, val apps: List<AppEntry>) : Cell() { override val key get() = "group:" + group.id }
}

object Groups {
    /** Each section's spots: grouped apps fold into their group, which sits where its first app would. */
    fun cells(
        apps: List<AppEntry>,
        groups: List<AppGroup>,
        moved: Map<String, String>,
        hubProjects: Set<String>,
    ): Map<String, List<Cell>> {
        val byKey = apps.associateBy { it.key }
        val inGroup = mutableSetOf<String>()
        val out = mutableMapOf<String, MutableList<Cell>>()
        groups.forEach { g ->
            val members = g.keys.mapNotNull { byKey[it] }
            if (members.isEmpty()) return@forEach
            inGroup += members.map { it.key }
            val section = Sections.of(members.first(), moved, hubProjects)
            out.getOrPut(section) { mutableListOf() } += Cell.Many(g, members)
        }
        apps.forEach { a ->
            if (a.key in inGroup) return@forEach
            out.getOrPut(Sections.of(a, moved, hubProjects)) { mutableListOf() } += Cell.One(a)
        }
        // Groups and apps together, A to Z by name.
        return out.mapValues { (_, list) ->
            list.sortedBy { c -> (if (c is Cell.Many) c.group.name else (c as Cell.One).app.label).lowercase() }
        }
    }
}

/** A group's icon: up to four of its apps' icons in a small tile, with its name under it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupCell(
    cell: Cell.Many,
    modifier: Modifier,
    slim: Boolean,
    onOpen: () -> Unit,
    onHold: () -> Unit,
    actions: List<RowAction>,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val tile = if (slim) 40.dp else 44.dp
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onOpen, onLongClick = { onHold(); menuOpen = true })
                .padding(vertical = 8.dp, horizontal = 2.dp),
        ) {
            Box(
                Modifier
                    .size(tile)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.22f))
                    .padding(3.dp)
            ) {
                Column {
                    cell.apps.take(4).chunked(2).forEach { row ->
                        Row {
                            row.forEach { app ->
                                Box(Modifier.padding(1.dp)) { androidx.compose.runtime.key(app.key) { AppIcon(app, (tile - 10.dp) / 2) } }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                cell.group.name,
                color = Color.White,
                fontSize = if (slim) 10.sp else 11.sp,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.9f),
                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                        blurRadius = 6f,
                    ),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, onClick = { menuOpen = false; action.onClick() })
            }
        }
    }
}

/** An open group: tap an app to open it, hold one to take it out. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupDialog(cell: Cell.Many, onTakeOut: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(cell.group.name) },
        text = {
            Column {
                cell.apps.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app ->
                            var menuOpen by remember(app.key) { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .combinedClickable(
                                            onClick = { onDismiss(); Apps.launch(context, app) },
                                            onLongClick = { menuOpen = true },
                                        )
                                        .padding(vertical = 8.dp),
                                ) {
                                    androidx.compose.runtime.key(app.key) { AppIcon(app, 48.dp) }
                                    Spacer(Modifier.height(4.dp))
                                    Text(app.label, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Take out of group") },
                                        onClick = { menuOpen = false; onTakeOut(app) },
                                    )
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Text(
                    "Hold an app to take it out.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Pick what to merge an app with: another app on this page, or a group already there. */
@Composable
fun MergeDialog(
    app: AppEntry,
    choices: List<Cell>,
    onPick: (Cell) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge ${app.label} with") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                if (choices.isEmpty()) Text("There's nothing else on this page to merge it with.")
                choices.forEach { c ->
                    Text(
                        when (c) {
                            is Cell.Many -> "${c.group.name}  (group)"
                            is Cell.One -> c.app.label
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(c); onDismiss() }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Give a group a new name. */
@Composable
fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename group") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim()); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
