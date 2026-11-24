package codes.gibber.phone.ui

import android.content.ContentValues
import android.net.Uri
import android.provider.BlockedNumberContract
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import codes.gibber.phone.MainActivity
import codes.gibber.phone.PhoneApp
import codes.gibber.phone.Screen
import codes.gibber.phone.data.Calls
import codes.gibber.phone.data.Contacts
import codes.gibber.phone.data.History
import codes.gibber.phone.data.GroupNames
import codes.gibber.phone.data.Sorting
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import codes.gibber.phone.data.Moment
import codes.gibber.phone.data.Msg
import codes.gibber.phone.data.MsgState
import codes.gibber.phone.data.Numbers
import codes.gibber.phone.data.Person
import codes.gibber.phone.data.Texts
import codes.gibber.phone.sms.Notifier
import codes.gibber.phone.sms.Pending
import codes.gibber.phone.sms.Sender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One person (or group): every text and call with them in one timeline, and a box to text them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PersonScreen(main: MainActivity, screen: Screen.Person, resumes: Int) {
    val version by PhoneApp.changes.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var addresses by remember(screen) { mutableStateOf(screen.addresses) }
    var threadId by remember(screen) { mutableStateOf(screen.threadId) }
    val group = addresses.size > 1

    val person by produceState<Person?>(null, screen.contactId, addresses, version) {
        value = withContext(Dispatchers.IO) {
            val id = screen.contactId ?: if (!group) Contacts.lookup(main, addresses.firstOrNull(), version)?.contactId else null
            id?.let { Contacts.person(main, it) }
        }
    }
    // A contact picked from Everyone may have no number on screen yet; use their first one.
    LaunchedEffect(person) {
        val p = person
        if (addresses.isEmpty() && p != null && p.numbers.isNotEmpty()) addresses = listOf(p.numbers[0].number)
    }
    LaunchedEffect(addresses, version) {
        if (threadId == null && addresses.isNotEmpty()) {
            threadId = withContext(Dispatchers.IO) { History.findThread(main, addresses) }
        }
    }
    val callNumbers = if (group) emptyList() else (person?.numbers?.map { it.number } ?: addresses)
    val history by produceState<List<Moment>?>(null, threadId, callNumbers, version, resumes) {
        value = withContext(Dispatchers.IO) { History.load(main, threadId, callNumbers) }
    }
    var renamed by remember { mutableStateOf(0) }
    val phoneName by produceState<String?>(null, threadId, version) {
        val t = threadId
        value = if (group && t != null) withContext(Dispatchers.IO) { Texts.titleOf(main, t) } else null
    }
    var naming by remember { mutableStateOf(false) }
    val title = when {
        group -> phoneName.let { n -> remember(addresses, version, renamed, n) { GroupNames.title(main, addresses, n) } }
        person != null -> person!!.name
        addresses.isNotEmpty() -> Numbers.pretty(addresses[0])
        else -> ""
    }

    // Opening a conversation reads it, and its notification goes away.
    LaunchedEffect(threadId, version) {
        val t = threadId ?: return@LaunchedEffect
        withContext(Dispatchers.IO) { Texts.markRead(main, t) }
        Notifier.cancel(main, t)
    }
    DisposableEffect(threadId) {
        Notifier.openThread = threadId ?: -1
        onDispose { Notifier.openThread = -1 }
    }

    var draft by remember(screen) { mutableStateOf(screen.draft) }
    val images = remember(screen) { mutableStateListOf<Uri>().apply { addAll(screen.images) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { picked ->
        images.addAll(picked)
    }
    var menu by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Uri?>(null) }
    var blocked by remember(addresses) { mutableStateOf(false) }
    LaunchedEffect(addresses) {
        blocked = !group && addresses.isNotEmpty() && withContext(Dispatchers.IO) {
            try { BlockedNumberContract.isBlocked(main, addresses[0]) } catch (_: Exception) { false }
        }
    }

    fun send() {
        val text = draft.trim()
        if ((text.isEmpty() && images.isEmpty()) || addresses.isEmpty()) return
        val pics = images.toList()
        draft = ""
        images.clear()
        scope.launch {
            val t = withContext(Dispatchers.IO) {
                try {
                    Sender.send(main, addresses, text, pics)
                } catch (e: Exception) {
                    null
                }
            }
            if (t == null) Toast.makeText(main, "Couldn't send. Is People your texting app?", Toast.LENGTH_LONG).show()
            else threadId = t
            PhoneApp.instance.bump()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.Filled.ArrowBack, "Back") { main.back() }
            Avatar(title.ifBlank { "#" }, person?.photo, size = 40.dp, group = group)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!group && person != null && addresses.isNotEmpty()) {
                    val label = person!!.numbers.firstOrNull { Numbers.same(it.number, addresses[0]) }?.label
                    Text(listOfNotNull(label, Numbers.pretty(addresses[0])).joinToString(" · "), color = Ink.Muted, fontSize = 13.sp, maxLines = 1)
                } else if (group) {
                    Text("${addresses.size} people", color = Ink.Muted, fontSize = 13.sp)
                }
            }
            if (!group && Numbers.dialable(addresses.firstOrNull())) {
                IconTap(Icons.Filled.Call, "Call", Ink.Green) { Calls.place(main, addresses[0]) }
            }
            Box {
                IconTap(Icons.Filled.MoreVert, "More") { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    val p = person
                    if (!group) {
                        // Switch which of their numbers you're texting.
                        p?.numbers?.filter { addresses.isEmpty() || !Numbers.same(it.number, addresses[0]) }?.forEach { n ->
                            DropdownMenuItem({ Text("Text ${n.label} ${Numbers.pretty(n.number)}") }, {
                                menu = false
                                addresses = listOf(n.number)
                                threadId = null
                            })
                        }
                        if (p != null) {
                            DropdownMenuItem({ Text("Edit contact") }, { menu = false; main.go(Screen.EditContact(p.contactId)) })
                            DropdownMenuItem({ Text(if (p.starred) "Remove from favorites" else "Add to favorites") }, {
                                menu = false
                                scope.launch(Dispatchers.IO) { Contacts.setStarred(main, p.contactId, !p.starred) }
                            })
                        } else if (addresses.isNotEmpty()) {
                            DropdownMenuItem({ Text("Add to contacts") }, { menu = false; main.go(Screen.EditContact(null, addresses[0])) })
                        }
                        if (addresses.isNotEmpty()) {
                            DropdownMenuItem({ Text("Copy number") }, {
                                menu = false
                                clipboard.setText(AnnotatedString(Numbers.clean(addresses[0])))
                            })
                            DropdownMenuItem({ Text(if (blocked) "Unblock" else "Block") }, {
                                menu = false
                                val number = addresses[0]
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        if (blocked) BlockedNumberContract.unblock(main, number)
                                        else main.contentResolver.insert(
                                            BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                                            ContentValues().apply { put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) },
                                        )
                                        blocked = !blocked
                                    } catch (_: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(main, "Make People your calls or texting app to block numbers.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            })
                        }
                    }
                    if (group) {
                        DropdownMenuItem({ Text("Name this group") }, { menu = false; naming = true })
                    }
                    threadId?.let { t ->
                        DropdownMenuItem({ Text("Delete conversation", color = Ink.Red) }, {
                            menu = false
                            scope.launch {
                                withContext(Dispatchers.IO) { Texts.deleteConversation(main, t) }
                                main.back()
                            }
                        })
                    }
                }
            }
        }

        if (naming) {
            var name by remember { mutableStateOf(GroupNames.get(main, addresses) ?: Sorting.groupName(main, addresses) ?: "") }
            AlertDialog(
                onDismissRequest = { naming = false },
                title = { Text("Name this group") },
                text = {
                    OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Group name") })
                },
                confirmButton = {
                    TextButton({
                        GroupNames.set(main, addresses, name)
                        naming = false
                        renamed++
                        PhoneApp.instance.bump()
                    }) { Text("Save") }
                },
                dismissButton = { TextButton({ naming = false }) { Text("Cancel") } },
            )
        }

        // Timeline
        val list = history ?: emptyList()
        val state = rememberLazyListState()
        LaunchedEffect(list.size) { if (list.isNotEmpty()) state.scrollToItem(list.size - 1) }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = state,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (group && renamed >= 0 && phoneName == null && !GroupNames.asked(main, addresses)) {
                item(key = "name-group") {
                    // Claude's name for it, from the sorting on your PC, is filled in for you to keep or change.
                    val suggested = remember { Sorting.groupName(main, addresses) }
                    var name by remember { mutableStateOf(suggested ?: "") }
                    Column(
                        Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.Card).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(if (suggested != null) "Claude suggests this name. Keep it?" else "New group. Give it a name?", color = Ink.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Group name") }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Pill("Save") {
                                if (name.isNotBlank()) {
                                    GroupNames.set(main, addresses, name)
                                    renamed++
                                    PhoneApp.instance.bump()
                                }
                            }
                            Pill("Not now", Ink.Raised) {
                                GroupNames.skip(main, addresses)
                                renamed++
                            }
                        }
                    }
                }
            }
            if (!main.canReadTexts()) {
                item {
                    Column(
                        Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.Card).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Texts are hidden because People isn't allowed to read them yet.", color = Ink.Text, fontSize = 14.sp)
                        Pill("Allow texts") { main.askForPermissions() }
                    }
                }
            }
            if (history != null && list.isEmpty()) {
                item { Text(if (group) "Say hi to the group." else "No texts or calls yet.", color = Ink.Muted, fontSize = 15.sp, modifier = Modifier.padding(12.dp)) }
            }
            items(list.size, key = { i -> list[i].let { m -> if (m is Moment.Text) "t${m.msg.mms}${m.msg.id}" else "c${(m as Moment.Phone).call.id}" } }) { i ->
                val m = list[i]
                val prev = list.getOrNull(i - 1)
                if (prev == null || !sameDay(prev.date, m.date)) DayLabel(m.date)
                when (m) {
                    is Moment.Phone -> CallLine(m)
                    is Moment.Text -> {
                        val showName = group && m.msg.from != null &&
                            !(prev is Moment.Text && prev.msg.from != null && Numbers.same(prev.msg.from, m.msg.from))
                        Bubble(
                            main, m.msg, showName,
                            onImage = { viewing = it },
                            onRetry = {
                                scope.launch(Dispatchers.IO) { Sender.retry(main, m.msg.id, m.msg.mms, addresses, m.msg.body) }
                            },
                            onCopy = { clipboard.setText(AnnotatedString(m.msg.body)) },
                            onDelete = { scope.launch(Dispatchers.IO) { Texts.deleteMessage(main, m.msg) } },
                        )
                    }
                }
            }
        }

        // Composer
        if (images.isNotEmpty()) {
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(images.toList()) { uri ->
                    Box {
                        val bmp = rememberBitmap(uri, 300)
                        Box(Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).background(Ink.Raised)) {
                            bmp?.let { Image(it.asImageBitmap(), null, Modifier.size(84.dp), contentScale = ContentScale.Crop) }
                        }
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color(0xAA000000))
                                .clickable { images.remove(uri) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            IconTap(Icons.Filled.AddPhotoAlternate, "Add a picture", Ink.Muted) {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Ink.Card)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (draft.isEmpty()) Text(if (group || images.isNotEmpty()) "Picture or group text" else "Text message", color = Ink.Muted, fontSize = 16.sp)
                BasicTextField(
                    draft, { draft = it },
                    textStyle = TextStyle(color = Ink.Text, fontSize = 16.sp),
                    cursorBrush = SolidColor(Ink.Blue),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 22.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            val canSend = (draft.isNotBlank() || images.isNotEmpty()) && addresses.isNotEmpty()
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (canSend) Ink.Blue else Ink.Raised).clickable(enabled = canSend) { send() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
        if (draft.length > 150 && images.isEmpty() && !group) {
            Text("${draft.length} characters, sent as ${(draft.length + 152) / 153} texts", color = Ink.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 64.dp, bottom = 6.dp))
        }
    }

    viewing?.let { uri ->
        Dialog({ viewing = null }, DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { viewing = null }, contentAlignment = Alignment.Center) {
                val bmp = rememberBitmap(uri, 2000)
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
            }
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) && ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

@Composable
private fun DayLabel(ms: Long) {
    val text = when {
        DateUtils.isToday(ms) -> "Today"
        DateUtils.isToday(ms + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> DateUtils.formatDateTime(PhoneApp.instance, ms, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Ink.Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CallLine(m: Moment.Phone) {
    val c = m.call
    val time = DateUtils.formatDateTime(PhoneApp.instance, c.date, DateUtils.FORMAT_SHOW_TIME)
    val length = if (c.duration > 0) " · " + if (c.duration < 60) "${c.duration} sec" else "${c.duration / 60} min" else ""
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when {
                c.missed -> Icons.Filled.CallMissed
                c.outgoing -> Icons.Filled.CallMade
                else -> Icons.Filled.CallReceived
            },
            null, tint = if (c.missed) Ink.Red else Ink.Muted, modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("${c.describe()}$length · $time", color = if (c.missed) Ink.Red else Ink.Muted, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    main: MainActivity,
    msg: Msg,
    showName: Boolean,
    onImage: (Uri) -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val mine = msg.outgoing
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (showName && msg.from != null) {
            Text(
                Contacts.lookup(main, msg.from)?.name ?: Numbers.pretty(msg.from),
                color = Ink.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
            )
        }
        Box {
            Column(
                Modifier.widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        when {
                            msg.state == MsgState.FAILED -> Ink.Red.copy(alpha = 0.35f)
                            mine -> Ink.Mine
                            else -> Ink.Raised
                        },
                    )
                    .combinedClickable(
                        onClick = { if (msg.state == MsgState.FAILED) onRetry() else if (msg.state == MsgState.WAITING) Pending.retryAll(main) },
                        onLongClick = { menu = true },
                    ),
            ) {
                for (img in msg.images) {
                    val bmp = rememberBitmap(img, 900)
                    Box(
                        Modifier.width(240.dp).height(if (bmp != null) (240f * bmp.height / bmp.width.coerceAtLeast(1)).coerceIn(80f, 400f).dp else 180.dp)
                            .background(Ink.Card).clickable { onImage(img) },
                    ) {
                        bmp?.let { Image(it.asImageBitmap(), "Picture", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    }
                }
                for (f in msg.otherFiles) {
                    Text("Attachment: $f", color = Ink.Text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
                if (msg.body.isNotBlank()) {
                    Text(msg.body, color = Ink.Text, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
            DropdownMenu(menu, { menu = false }) {
                if (msg.body.isNotBlank()) DropdownMenuItem({ Text("Copy text") }, { menu = false; onCopy() })
                DropdownMenuItem({ Text("Delete", color = Ink.Red) }, { menu = false; onDelete() })
            }
        }
        val time = DateUtils.formatDateTime(PhoneApp.instance, msg.date, DateUtils.FORMAT_SHOW_TIME)
        val status = when (msg.state) {
            MsgState.SENDING -> "Sending…"
            MsgState.FAILED -> "Not sent. Tap to try again."
            MsgState.WAITING -> "Tap to download"
            else -> time
        }
        Text(
            status,
            color = if (msg.state == MsgState.FAILED) Ink.Red else Ink.Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}
