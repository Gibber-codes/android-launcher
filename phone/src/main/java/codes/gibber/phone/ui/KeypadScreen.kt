package codes.gibber.phone.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codes.gibber.phone.MainActivity
import codes.gibber.phone.PhoneApp
import codes.gibber.phone.Screen
import codes.gibber.phone.data.Calls
import codes.gibber.phone.data.Contacts
import codes.gibber.phone.data.Numbers
import codes.gibber.phone.data.Person
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val keys = listOf(
    listOf("1" to "", "2" to "ABC", "3" to "DEF"),
    listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
    listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
    listOf("*" to "", "0" to "+", "#" to ""),
)

private val letterDigit: Map<Char, Char> = HashMap<Char, Char>().apply {
    for (row: List<Pair<String, String>> in keys) {
        for (pair: Pair<String, String> in row) {
            for (l: Char in pair.second) put(l.lowercaseChar(), pair.first[0])
        }
    }
}

/** "Jo Smith" as keypad digits: 56 76484, so typing 56 finds Jo. */
private fun nameDigits(name: String): List<String> =
    name.lowercase().split(' ', '-', '.').filter { it.isNotBlank() }.map { w -> w.mapNotNull { letterDigit[it] }.joinToString("") }

/** The keypad: type a number, see who it matches, call or text. Hold 1 for voicemail, 0 for +. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeypadScreen(main: MainActivity, start: String) {
    val version by PhoneApp.changes.collectAsState()
    var number by remember(start) { mutableStateOf(start) }
    val people by produceState<List<Person>>(emptyList(), version) {
        value = withContext(Dispatchers.IO) { Contacts.all(main) }
    }
    val digits = number.filter { it.isDigit() }
    val matches = if (digits.length < 2) emptyList() else people.filter { p ->
        p.numbers.any { it.number.filter(Char::isDigit).contains(digits) } || nameDigits(p.name).any { it.startsWith(digits) }
    }.take(4)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.Filled.ArrowBack, "Back") { main.back() }
            Text("Keypad", color = Ink.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(matches, key = { it.contactId }) { p ->
                val n = p.numbers.firstOrNull { it.number.filter(Char::isDigit).contains(digits) } ?: p.numbers.firstOrNull()
                Row(
                    Modifier.fillMaxWidth().clickable { n?.let { number = Numbers.clean(it.number) } }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(p.name, p.photo, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Ink.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        n?.let { Text(Numbers.pretty(it.number), color = Ink.Muted, fontSize = 13.sp) }
                    }
                    n?.let { IconTap(Icons.Filled.Call, "Call", Ink.Green) { Calls.place(main, it.number) } }
                }
            }
        }

        Text(
            if (number.isEmpty()) "" else Numbers.pretty(number).takeIf { number.length > 3 } ?: number,
            color = Ink.Text, fontSize = 36.sp, textAlign = TextAlign.Center, maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (Numbers.dialable(number) && matches.none { p -> p.numbers.any { Numbers.same(it.number, number) } }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { main.go(Screen.EditContact(null, number)) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PersonAdd, null, tint = Ink.Blue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to contacts", color = Ink.Blue, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in keys) {
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    for ((d, letters) in row) {
                        Box(
                            Modifier.size(76.dp).clip(CircleShape).background(Ink.Card).combinedClickable(
                                onClick = { number += d },
                                onLongClick = {
                                    when (d) {
                                        "0" -> number += "+"
                                        "1" -> if (number.isEmpty()) Calls.voicemail(main) else number += d
                                        else -> number += d
                                    }
                                },
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d, color = Ink.Text, fontSize = 30.sp)
                                if (letters.isNotEmpty() || d == "1") {
                                    Text(if (d == "1") "voicemail" else letters, color = Ink.Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape).clickable(enabled = Numbers.dialable(number)) {
                        main.replace(Screen.Person(listOf(number)))
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Chat, "Text", tint = if (Numbers.dialable(number)) Ink.Text else Ink.Line) }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Ink.Green).clickable(enabled = number.isNotBlank()) {
                        if (Calls.place(main, number)) main.back()
                    },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Call, "Call", tint = Color.White, modifier = Modifier.size(34.dp)) }
                Box(
                    Modifier.size(76.dp).clip(CircleShape).combinedClickable(
                        onClick = { number = number.dropLast(1) },
                        onLongClick = { number = "" },
                    ),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Backspace, "Delete", tint = if (number.isEmpty()) Ink.Line else Ink.Text) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Starting a new text: pick one person for a regular text, or several for a group text. */
@Composable
fun NewMessageScreen(main: MainActivity, screen: Screen.NewMessage) {
    val version by PhoneApp.changes.collectAsState()
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }
    val people by produceState<List<Person>>(emptyList(), version) {
        value = withContext(Dispatchers.IO) { Contacts.all(main) }
    }
    val q = query.trim().lowercase()
    val digits = q.filter { it.isDigit() }
    val matches = people.filter { it.numbers.isNotEmpty() }.filter { p ->
        q.isEmpty() || p.name.lowercase().contains(q) || (digits.length >= 3 && p.numbers.any { it.number.filter(Char::isDigit).contains(digits) })
    }

    fun add(number: String) {
        if (picked.none { Numbers.same(it, number) }) picked.add(Numbers.clean(number))
        query = ""
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.Filled.ArrowBack, "Back") { main.back() }
            Text("New message", color = Ink.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (picked.isNotEmpty()) {
                Pill(if (picked.size == 1) "Next" else "Start group") {
                    main.replace(Screen.Person(picked.toList(), draft = screen.draft, images = screen.images))
                }
                Spacer(Modifier.width(12.dp))
            }
        }
        // Who it's going to.
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            for (n in picked) {
                Row(
                    Modifier.padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp)).background(Ink.Raised).padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Contacts.lookup(main, n, version)?.name ?: Numbers.pretty(n), color = Ink.Text, fontSize = 15.sp)
                    IconTap(Icons.Filled.Close, "Remove", Ink.Muted) { picked.remove(n) }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(22.dp)).background(Ink.Card).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("To", color = Ink.Muted, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Name or number", color = Ink.Muted, fontSize = 16.sp)
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = Ink.Text, fontSize = 16.sp),
                        cursorBrush = SolidColor(Ink.Blue),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (Numbers.dialable(query) && query.all { it.isDigit() || it in "+-() " }) {
                Pill("Add ${Numbers.pretty(query)}") { add(query) }
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(matches, key = { it.contactId }) { p ->
                Column(Modifier.fillMaxWidth()) {
                    for (n in p.numbers) {
                        Row(
                            Modifier.fillMaxWidth().clickable { add(n.number) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(p.name, p.photo, size = 38.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = Ink.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${n.label} · ${Numbers.pretty(n.number)}", color = Ink.Muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
