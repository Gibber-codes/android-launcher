package codes.gibber.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
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

/** A group, like Music: the design's folder, a count of its apps, and its name. */
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
    val press = Modifier
        .clip(RoundedCornerShape(12.dp))
        .combinedClickable(onClick = onOpen, onLongClick = { onHold(); menuOpen = true })
    val shadow = androidx.compose.ui.text.TextStyle(
        shadow = androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.6f),
            offset = androidx.compose.ui.geometry.Offset(0f, 1f),
            blurRadius = 4f,
        ),
    )
    Box(modifier) {
        if (slim) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).then(press),
            ) {
                FolderWithCount(cell.apps.size, 26.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    cell.group.name,
                    color = Look.Cream,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    style = shadow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().then(press).padding(vertical = 6.dp),
            ) {
                Box(Modifier.heightIn(min = 32.dp), contentAlignment = Alignment.Center) { FolderWithCount(cell.apps.size, 30.dp) }
                Spacer(Modifier.height(8.dp))
                Text(
                    cell.group.name,
                    color = Look.Cream,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            actions.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, onClick = { menuOpen = false; action.onClick() })
            }
        }
    }
}

@Composable
private fun FolderWithCount(count: Int, size: androidx.compose.ui.unit.Dp) {
    Box {
        FolderGlyph(size)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 7.dp, y = (-6).dp)
                .size(15.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Look.Lilac),
        ) {
            Text("$count", color = Look.Page, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** An open group: a small card over the page. Tap an app to open it, hold one to take it out. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupDialog(cell: Cell.Many, onTakeOut: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .width(230.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Look.Folder)
                .border(1.dp, Look.Line, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Text(
                cell.group.name.uppercase(),
                color = Look.Sage,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
            )
            cell.apps.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app ->
                        var menuOpen by remember(app.key) { mutableStateOf(false) }
                        Box(Modifier.weight(1f)) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .combinedClickable(
                                        onClick = { onDismiss(); Apps.launch(context, app) },
                                        onLongClick = { menuOpen = true },
                                    ),
                            ) {
                                androidx.compose.runtime.key(app.key) { AppIcon(app, 28.dp, mono = true) }
                                Spacer(Modifier.height(6.dp))
                                Text(app.label, color = Look.Cream, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Take out of group") },
                                    onClick = { menuOpen = false; onTakeOut(app) },
                                )
                            }
                        }
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text("Hold an app to take it out, tap outside to close", color = Look.Muted, fontSize = 11.sp)
        }
    }
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
