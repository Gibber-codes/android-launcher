package codes.gibber.phone.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.role.RoleManager
import codes.gibber.phone.MainActivity
import codes.gibber.phone.PhoneApp
import codes.gibber.phone.R
import codes.gibber.phone.Screen
import codes.gibber.phone.data.CallRow
import codes.gibber.phone.data.CallRows
import codes.gibber.phone.data.Calls
import codes.gibber.phone.data.Voicemail
import codes.gibber.phone.data.Voicemails
import codes.gibber.phone.data.Contacts
import codes.gibber.phone.data.Numbers
import codes.gibber.phone.data.Person
import codes.gibber.phone.data.RecentItem
import codes.gibber.phone.data.Sorting
import codes.gibber.phone.data.Recent
import codes.gibber.phone.sms.Pending
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

@Composable
fun HomeScreen(main: MainActivity, resumes: Int) {
    val version by PhoneApp.changes.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    val missing = remember(resumes) { main.missingPermissions() }
    val defaultCalls = remember(resumes) { main.isDefaultCalls() }

    val recent by produceState<List<RecentItem>?>(Recent.last, version, resumes) {
        value = withContext(Dispatchers.IO) { Recent.load(main, version) }
    }
    val people by produceState<List<Person>?>(Contacts.cached().ifEmpty { null }, version, resumes) {
        value = withContext(Dispatchers.IO) { Contacts.everyone(main) }
    }
    val calls by produceState<List<CallRow>?>(CallRows.last, version, resumes) {
        value = withContext(Dispatchers.IO) { CallRows.load(main, version) }
    }
    val voicemails by produceState<List<Voicemail>?>(null, version, resumes) {
        value = withContext(Dispatchers.IO) { Voicemails.list(main) }
    }
    val pending by produceState(emptyList<Pending.Item>(), version, resumes) {
        value = withContext(Dispatchers.IO) { Pending.list(main) }
    }
    LaunchedEffect(resumes) {
        if (defaultCalls) withContext(Dispatchers.IO) { Calls.markSeen(main) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Header: logo, name, settings.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.gibbercodes_logo), null, Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Text("People", color = Ink.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconTap(Icons.Filled.Settings, "Settings") { main.go(Screen.Settings) }
            }
            SearchBox(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            // Texts can live in another app, like Google Messages, so the card only asks about calls.
            if (missing.isNotEmpty() || !defaultCalls) {
                SetupCard(main, missing.isNotEmpty(), defaultCalls)
            }
            if (pending.isNotEmpty()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Ink.Card).clickable { Pending.retryAll(main) }.padding(14.dp),
                ) {
                    Text(
                        "${pending.size} picture message${if (pending.size == 1) "" else "s"} didn't download. Tap to try again.",
                        color = Ink.Text, fontSize = 14.sp,
                    )
                }
            }

            if (query.isBlank()) {
                val newVoicemails = voicemails?.count { !it.read } ?: 0
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Tab("Contacts", tab == 0) { tab = 0 }
                    Spacer(Modifier.width(20.dp))
                    Tab("Calls", tab == 1) { tab = 1 }
                    Spacer(Modifier.width(20.dp))
                    Tab(if (newVoicemails > 0) "Voicemail ($newVoicemails)" else "Voicemail", tab == 2) { tab = 2 }
                }
                when (tab) {
                    0 -> EveryoneList(main, people)
                    1 -> CallsList(main, calls)
                    else -> VoicemailList(main, voicemails)
                }
            } else {
                SearchResults(main, query, recent ?: emptyList(), people ?: emptyList())
            }
        }

        // Keypad and new message, bottom right.
        Column(Modifier.align(Alignment.BottomEnd).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // A new text lives in People only while it's your texts app; otherwise texts start in that app.
            if (main.isDefaultTexts()) Fab(Icons.Filled.Edit, "New message", Ink.Raised) { main.go(Screen.NewMessage()) }
            Fab(Icons.Filled.Dialpad, "Keypad", Ink.Blue) { main.go(Screen.Keypad()) }
        }
    }
}

@Composable
private fun SetupCard(main: MainActivity, needsPermissions: Boolean, defaultCalls: Boolean) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Ink.Card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Finish setting up", color = Ink.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (needsPermissions) SetupStep("Let People see your contacts, calls and texts", "Allow") { main.askForPermissions() }
        if (!defaultCalls) SetupStep("Use it for calls", "Make default") { main.askToBeDefault(RoleManager.ROLE_DIALER) }
    }
}

@Composable
private fun SetupStep(text: String, button: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Ink.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            button, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Ink.Blue).clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun IconTap(icon: ImageVector, label: String, tint: Color = Ink.Text, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Fab(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(60.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun Tab(text: String, on: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(text, color = if (on) Ink.Text else Ink.Muted, fontSize = 17.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.height(3.dp).width(if (on) 28.dp else 0.dp).clip(RoundedCornerShape(2.dp)).background(Ink.Red))
    }
}

@Composable
fun SearchBox(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "Search people and numbers") {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink.Card).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = Ink.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Ink.Muted, fontSize = 16.sp)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = TextStyle(color = Ink.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(Ink.Blue),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(Icons.Filled.Close, "Clear", tint = Ink.Muted, modifier = Modifier.size(20.dp).clickable { onChange("") })
        }
    }
}

private val listPadding = PaddingValues(bottom = 170.dp)

@Composable
private fun CallsList(main: MainActivity, list: List<CallRow>?) {
    if (list == null) return
    if (list.isEmpty()) {
        Empty("No calls yet.")
        return
    }
    // Numbers Claude marked spam on your PC fold away at the bottom, as on Recent before.
    val (spam, kept) = list.partition { it.contactId == null && Sorting.spamWhy(main, listOf(it.number), null) != null }
    var showSpam by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
        var lastDay = ""
        for (row in kept) {
            val day = dayText(row.last.date)
            if (day != lastDay) {
                lastDay = day
                item(key = "d$day${row.last.id}") { Label(day) }
            }
            item(key = "c${row.last.id}") { CallRowView(main, row) }
        }
        if (spam.isNotEmpty()) {
            item(key = "spam") {
                Text(
                    if (showSpam) "Hide spam" else "Spam: ${spam.size} hidden by Claude",
                    color = Ink.Muted, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().clickable { showSpam = !showSpam }.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            if (showSpam) items(spam, key = { "s${it.last.id}" }) { CallRowView(main, it) }
        }
    }
}

@Composable
private fun CallRowView(main: MainActivity, row: CallRow) {
    val call = row.last
    val time = android.text.format.DateUtils.formatDateTime(PhoneApp.instance, call.date, android.text.format.DateUtils.FORMAT_SHOW_TIME)
    val length = if (call.duration > 0) "  ·  " + if (call.duration < 60) "${call.duration} sec" else "${call.duration / 60} min" else ""
    Row(
        Modifier.fillMaxWidth().clickable(enabled = row.number.isNotBlank()) {
            main.go(Screen.Person(listOf(row.number), null, row.contactId))
        }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(row.name, row.photo)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.name + if (row.count > 1) " (${row.count})" else "",
                color = if (call.missed) Ink.Red else Ink.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (call.missed && call.isNew) FontWeight.Bold else FontWeight.Normal,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        call.missed -> Icons.Filled.CallMissed
                        call.outgoing -> Icons.Filled.CallMade
                        else -> Icons.Filled.CallReceived
                    },
                    null, tint = if (call.missed) Ink.Red else Ink.Muted, modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(call.describe() + "  ·  " + time + length, color = Ink.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (Numbers.dialable(row.number)) IconTap(Icons.Filled.Call, "Call", Ink.Muted) { Calls.place(main, row.number) }
    }
}

@Composable
private fun VoicemailList(main: MainActivity, list: List<Voicemail>?) {
    val app = remember { Voicemails.appIntent(main) }
    var playing by remember { mutableStateOf<Long?>(null) }
    val player = remember { arrayOfNulls<android.media.MediaPlayer>(1) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { player[0]?.release(); player[0] = null }
    }
    fun stop() {
        player[0]?.release()
        player[0] = null
        playing = null
    }
    fun play(v: Voicemail) {
        stop()
        if (!v.hasAudio) {
            Voicemails.fetch(main, v)
            android.widget.Toast.makeText(main, "Downloading it. Tap again in a moment.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val mp = android.media.MediaPlayer()
            mp.setDataSource(main, v.uri)
            mp.setOnCompletionListener { stop() }
            mp.prepare()
            mp.start()
            player[0] = mp
            playing = v.id
            Voicemails.markRead(main, v)
        } catch (_: Exception) {
            stop()
            android.widget.Toast.makeText(main, "That recording wouldn't play.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
        item(key = "buttons") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Call voicemail") {
                    if (!Calls.voicemail(main)) android.widget.Toast.makeText(main, "Make People your calls app first.", android.widget.Toast.LENGTH_SHORT).show()
                }
                if (app != null) Pill("Open voicemail app", Ink.Raised) { main.startActivity(app) }
            }
        }
        when {
            list == null -> item(key = "none") {
                Empty("Android isn't sharing your voicemails with People yet. Make People your calls app, then come back here.")
            }
            list.isEmpty() -> item(key = "empty") {
                Empty(
                    "No voicemails shared with People. On your phone they may stay in the carrier's voicemail app" +
                        (if (app != null) ", which the button above opens." else ". Call voicemail to hear them."),
                )
            }
            else -> items(list, key = { "v${it.id}" }) { v -> VoicemailRow(main, v, playing == v.id, { if (playing == v.id) stop() else play(v) }) }
        }
    }
}

@Composable
private fun VoicemailRow(main: MainActivity, v: Voicemail, playing: Boolean, onPlay: () -> Unit) {
    val match = remember(v.number) { Contacts.lookup(main, v.number) }
    val name = match?.name ?: if (v.number.isBlank()) "Private number" else Numbers.pretty(v.number)
    val length = if (v.duration > 0) "${v.duration / 60}:${(v.duration % 60).toString().padStart(2, '0')}  ·  " else ""
    Column(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(name, match?.photo)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Ink.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (v.read) FontWeight.Normal else FontWeight.Bold)
                Text(length + whenText(v.date) + if (v.read) "" else "  ·  new", color = Ink.Muted, fontSize = 14.sp, maxLines = 1)
            }
            IconTap(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, if (playing) "Stop" else "Play", Ink.Blue, onPlay)
            if (Numbers.dialable(v.number)) IconTap(Icons.Filled.Call, "Call back", Ink.Muted) { Calls.place(main, v.number) }
        }
        v.transcript?.let { Text(it, color = Ink.Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 58.dp, top = 2.dp)) }
    }
}

/** "Today", "Yesterday", or the date, for the day labels on Calls. */
private fun dayText(ms: Long): String = when {
    android.text.format.DateUtils.isToday(ms) -> "Today"
    android.text.format.DateUtils.isToday(ms + android.text.format.DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> android.text.format.DateUtils.formatDateTime(
        PhoneApp.instance, ms,
        android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY or android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_ABBREV_ALL,
    )
}

@Composable
private fun RecentRow(main: MainActivity, item: RecentItem) {
    val lastIsCall = item.lastCall != null && item.lastCall.date >= item.date
    Row(
        Modifier.fillMaxWidth().clickable {
            main.go(Screen.Person(item.addresses, item.threadId, item.contactId))
        }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item.name, item.photo, group = item.isGroup)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name, color = Ink.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (item.unread || item.missedCalls > 0) FontWeight.Bold else FontWeight.Normal,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lastIsCall) {
                    val call = item.lastCall!!
                    Icon(
                        when {
                            call.missed -> Icons.Filled.CallMissed
                            call.outgoing -> Icons.Filled.CallMade
                            else -> Icons.Filled.CallReceived
                        },
                        null, tint = if (call.missed) Ink.Red else Ink.Muted, modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                val lineColor = when {
                    lastIsCall && item.lastCall!!.missed -> Ink.Red
                    item.unread -> Ink.Text
                    else -> Ink.Muted
                }
                Text(item.line.replace('\n', ' '), color = lineColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("  ·  " + whenText(item.date), color = Ink.Muted, fontSize = 13.sp, maxLines = 1)
            }
        }
        if (item.unread) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Ink.Blue))
            Spacer(Modifier.width(6.dp))
        }
        if (!item.isGroup && Numbers.dialable(item.addresses.firstOrNull())) {
            IconTap(Icons.Filled.Call, "Call", Ink.Muted) { Calls.place(main, item.addresses[0]) }
        }
    }
}

@Composable
private fun EveryoneList(main: MainActivity, people: List<Person>?) {
    if (people == null) return
    // Claude's groups from your PC, as chips; picking one shows only its people.
    val groups = Sorting.sorted(main).groups.map { it.name }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val group = picked?.takeIf { it in groups }
    val shown = if (group == null) people else people.filter { group in Sorting.groupsOf(main, it) }
    val favorites = if (group == null) people.filter { it.starred } else emptyList()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
        if (groups.isNotEmpty()) {
            item(key = "groups") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill("All", if (group == null) Ink.Blue else Ink.Raised) { picked = null }
                    for (g in groups) Pill(g, if (group == g) Ink.Blue else Ink.Raised) { picked = g }
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { main.go(Screen.EditContact()) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Ink.Raised), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PersonAdd, null, tint = Ink.Text)
                }
                Spacer(Modifier.width(14.dp))
                Text("New contact", color = Ink.Text, fontSize = 17.sp)
            }
        }
        if (favorites.isNotEmpty()) {
            item { Label("Favorites") }
            items(favorites, key = { "f${it.contactId}" }) { PersonRow(main, it) }
            item { Label("Everyone") }
        }
        if (shown.isEmpty()) item { Empty(if (group == null) "No contacts yet." else "No one in $group.") }
        items(shown, key = { "p${it.contactId}" }) { PersonRow(main, it) }
    }
}

@Composable
fun Label(text: String) {
    Text(text, color = Ink.Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun Empty(text: String) {
    Text(text, color = Ink.Muted, fontSize = 15.sp, modifier = Modifier.padding(24.dp))
}

@Composable
private fun PersonRow(main: MainActivity, p: Person) {
    Row(
        Modifier.fillMaxWidth().clickable {
            main.go(Screen.Person(p.numbers.take(1).map { it.number }, null, p.contactId))
        }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.name, p.photo)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, color = Ink.Text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (p.starred) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Star, null, tint = Ink.Blue, modifier = Modifier.size(14.dp))
                }
            }
            p.numbers.firstOrNull()?.let { Text("${it.label} · ${Numbers.pretty(it.number)}", color = Ink.Muted, fontSize = 13.sp, maxLines = 1) }
        }
        p.numbers.firstOrNull()?.let { n ->
            IconTap(Icons.Filled.Chat, "Text", Ink.Muted) { main.textTo(listOf(n.number), p.contactId) }
            IconTap(Icons.Filled.Call, "Call", Ink.Muted) { Calls.place(main, n.number) }
        }
    }
}

@Composable
private fun SearchResults(main: MainActivity, query: String, recent: List<RecentItem>, people: List<Person>) {
    val q = query.trim().lowercase()
    val digits = q.filter { it.isDigit() }
    val matchPeople = people.filter { p ->
        p.name.lowercase().contains(q) || (digits.length >= 3 && p.numbers.any { it.number.filter(Char::isDigit).contains(digits) })
    }.take(40)
    val known = matchPeople.map { it.contactId }.toSet()
    val matchRecent = recent.filter { r ->
        (r.contactId == null || r.contactId !in known) &&
            (r.name.lowercase().contains(q) || (digits.length >= 3 && r.addresses.any { it.filter(Char::isDigit).contains(digits) }) ||
                r.line.lowercase().contains(q))
    }.take(40)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
        if (Numbers.dialable(query) && query.all { it.isDigit() || it in "+-() " }) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Call ${Numbers.pretty(query)}") { Calls.place(main, query) }
                    Pill("Text") { main.textTo(listOf(query)) }
                }
            }
        }
        if (matchPeople.isNotEmpty()) {
            item { Label("People") }
            items(matchPeople, key = { "p${it.contactId}" }) { PersonRow(main, it) }
        }
        if (matchRecent.isNotEmpty()) {
            item { Label("Recent") }
            items(matchRecent, key = { "r${it.key}" }) { RecentRow(main, it) }
        }
        if (matchPeople.isEmpty() && matchRecent.isEmpty()) item { Empty("Nothing matches \"$query\".") }
    }
}

@Composable
fun Pill(text: String, color: Color = Ink.Blue, onClick: () -> Unit) {
    Text(
        text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** "2:14 PM" today, "Tue" this week, "Nov 3" this year, else "Nov 3, 2025". */
fun whenText(ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val ctx = PhoneApp.instance
    return when {
        DateUtils.isToday(ms) -> DateUtils.formatDateTime(ctx, ms, DateUtils.FORMAT_SHOW_TIME)
        System.currentTimeMillis() - ms < 6 * DateUtils.DAY_IN_MILLIS -> DateUtils.formatDateTime(ctx, ms, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY)
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> DateUtils.formatDateTime(ctx, ms, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH)
        else -> DateUtils.formatDateTime(ctx, ms, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
}
