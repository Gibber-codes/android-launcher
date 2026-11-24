package codes.gibber.phone.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import codes.gibber.phone.data.Calls
import codes.gibber.phone.data.Contacts
import codes.gibber.phone.data.Numbers
import codes.gibber.phone.data.Person
import codes.gibber.phone.data.RecentItem
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
    val defaultTexts = remember(resumes) { main.isDefaultTexts() }

    val recent by produceState<List<RecentItem>?>(null, version, resumes) {
        value = withContext(Dispatchers.IO) { Recent.load(main, version) }
    }
    val people by produceState<List<Person>?>(null, version, resumes) {
        value = withContext(Dispatchers.IO) { Contacts.all(main) }
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
                Text("Gibber Phone", color = Ink.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconTap(Icons.Filled.Settings, "Settings") { main.go(Screen.Settings) }
            }
            SearchBox(query, { query = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            if (missing.isNotEmpty() || !defaultCalls || !defaultTexts) {
                SetupCard(main, missing.isNotEmpty(), defaultCalls, defaultTexts)
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
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Tab("Recent", tab == 0) { tab = 0 }
                    Spacer(Modifier.width(20.dp))
                    Tab("Everyone", tab == 1) { tab = 1 }
                }
                if (tab == 0) RecentList(main, recent) else EveryoneList(main, people)
            } else {
                SearchResults(main, query, recent ?: emptyList(), people ?: emptyList())
            }
        }

        // Keypad and new message, bottom right.
        Column(Modifier.align(Alignment.BottomEnd).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Fab(Icons.Filled.Edit, "New message", Ink.Raised) { main.go(Screen.NewMessage()) }
            Fab(Icons.Filled.Dialpad, "Keypad", Ink.Blue) { main.go(Screen.Keypad()) }
        }
    }
}

@Composable
private fun SetupCard(main: MainActivity, needsPermissions: Boolean, defaultCalls: Boolean, defaultTexts: Boolean) {
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Ink.Card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Finish setting up", color = Ink.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (needsPermissions) SetupStep("Let Gibber Phone see your contacts, calls and texts", "Allow") { main.askForPermissions() }
        if (!defaultCalls) SetupStep("Use it for calls", "Make default") { main.askToBeDefault(RoleManager.ROLE_DIALER) }
        if (!defaultTexts) SetupStep("Use it for texts", "Make default") { main.askToBeDefault(RoleManager.ROLE_SMS) }
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
private fun RecentList(main: MainActivity, list: List<RecentItem>?) {
    if (list == null) return
    if (list.isEmpty()) {
        Empty("No calls or texts yet.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
        items(list, key = { it.key }) { item -> RecentRow(main, item) }
    }
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
    val favorites = people.filter { it.starred }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding) {
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
        if (people.isEmpty()) item { Empty("No contacts yet.") }
        items(people, key = { "p${it.contactId}" }) { PersonRow(main, it) }
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
            IconTap(Icons.Filled.Chat, "Text", Ink.Muted) { main.go(Screen.Person(listOf(n.number), null, p.contactId)) }
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
                    Pill("Text") { main.go(Screen.Person(listOf(query))) }
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
