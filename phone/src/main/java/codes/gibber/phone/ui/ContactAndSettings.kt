package codes.gibber.phone.ui

import android.app.role.RoleManager
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import codes.gibber.phone.BuildConfig
import codes.gibber.phone.MainActivity
import codes.gibber.phone.PhoneApp
import codes.gibber.phone.Screen
import codes.gibber.phone.Updater
import codes.gibber.phone.data.ContactForm
import codes.gibber.phone.data.Contacts
import codes.gibber.phone.data.FormRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val phoneTypes = listOf(Phone.TYPE_MOBILE to "Mobile", Phone.TYPE_HOME to "Home", Phone.TYPE_WORK to "Work", Phone.TYPE_OTHER to "Other")
private val emailTypes = listOf(Email.TYPE_HOME to "Personal", Email.TYPE_WORK to "Work", Email.TYPE_OTHER to "Other")

/** Add or edit a contact: name, phone numbers and emails. */
@Composable
fun ContactEditScreen(main: MainActivity, screen: Screen.EditContact) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf<ContactForm?>(null) }
    var name by remember { mutableStateOf("") }
    val phones = remember { mutableStateListOf<FormRow>() }
    val emails = remember { mutableStateListOf<FormRow>() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(screen) {
        val f = withContext(Dispatchers.IO) { Contacts.form(main, screen.contactId, screen.number) }
        form = f
        name = f.name
        phones.clear(); phones.addAll(f.phones.ifEmpty { listOf(FormRow(null, "", Phone.TYPE_MOBILE)) })
        emails.clear(); emails.addAll(f.emails)
    }
    val before = form ?: return

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.Filled.ArrowBack, "Back") { main.back() }
            Text(if (screen.contactId == null) "New contact" else "Edit contact", color = Ink.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Pill("Save") {
                if (name.isBlank() && phones.all { it.value.isBlank() }) {
                    Toast.makeText(main, "Add a name or a number first.", Toast.LENGTH_SHORT).show()
                    return@Pill
                }
                scope.launch {
                    val id = withContext(Dispatchers.IO) { Contacts.save(main, before, name.trim(), phones.toList(), emails.toList()) }
                    if (id == null) {
                        Toast.makeText(main, "Couldn't save the contact.", Toast.LENGTH_LONG).show()
                    } else {
                        PhoneApp.instance.bump()
                        main.back()
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Name", name, { name = it })
            Label("Phone")
            phones.forEachIndexed { i, row ->
                RowEditor(row, phoneTypes, KeyboardType.Phone, "Phone number",
                    onChange = { phones[i] = it },
                    onRemove = { phones.removeAt(i) })
            }
            Text("+ Add a number", color = Ink.Blue, fontSize = 15.sp, modifier = Modifier.clickable { phones.add(FormRow(null, "", Phone.TYPE_MOBILE)) }.padding(8.dp))
            Label("Email")
            emails.forEachIndexed { i, row ->
                RowEditor(row, emailTypes, KeyboardType.Email, "Email",
                    onChange = { emails[i] = it },
                    onRemove = { emails.removeAt(i) })
            }
            Text("+ Add an email", color = Ink.Blue, fontSize = 15.sp, modifier = Modifier.clickable { emails.add(FormRow(null, "", Email.TYPE_HOME)) }.padding(8.dp))

            val id = screen.contactId
            if (id != null) {
                Spacer(Modifier.height(24.dp))
                if (!confirmDelete) {
                    Text("Delete contact", color = Ink.Red, fontSize = 15.sp, modifier = Modifier.clickable { confirmDelete = true }.padding(8.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Delete for good?", color = Ink.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Pill("Keep", Ink.Raised) { confirmDelete = false }
                        Spacer(Modifier.width(8.dp))
                        Pill("Delete", Ink.Red) {
                            scope.launch {
                                withContext(Dispatchers.IO) { Contacts.delete(main, id) }
                                main.home()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(hint: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ink.Card).padding(horizontal = 14.dp, vertical = 14.dp)) {
        if (value.isEmpty()) Text(hint, color = Ink.Muted, fontSize = 16.sp)
        BasicTextField(
            value, onChange, singleLine = true,
            textStyle = TextStyle(color = Ink.Text, fontSize = 16.sp),
            cursorBrush = SolidColor(Ink.Blue),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RowEditor(
    row: FormRow,
    types: List<Pair<Int, String>>,
    keyboard: KeyboardType,
    hint: String,
    onChange: (FormRow) -> Unit,
    onRemove: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(hint, row.value, { onChange(row.copy(value = it)) }, keyboard, Modifier.weight(1f))
            IconTap(Icons.Filled.Close, "Remove", Ink.Muted) { onRemove() }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((t, label) in types) {
                val on = row.type == t
                Text(
                    label, color = if (on) Ink.Page else Ink.Muted, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Ink.Text else Ink.Card)
                        .clickable { onChange(row.copy(type = t)) }.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** Defaults, permissions, notifications and updates. */
@Composable
fun SettingsScreen(main: MainActivity, resumes: Int) {
    val scope = rememberCoroutineScope()
    val calls = remember(resumes) { main.isDefaultCalls() }
    val texts = remember(resumes) { main.isDefaultTexts() }
    val missing = remember(resumes) { main.missingPermissions() }
    var update by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTap(Icons.Filled.ArrowBack, "Back") { main.back() }
            Text("Settings", color = Ink.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingRow("Calls", if (calls) "Gibber Phone handles your calls" else "Samsung's Phone app handles your calls",
                if (calls) "Change" else "Make default") {
                if (calls) main.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) else main.askToBeDefault(RoleManager.ROLE_DIALER)
            }
            SettingRow("Texts", if (texts) "Gibber Phone handles your texts" else "Another app handles your texts",
                if (texts) "Change" else "Make default") {
                if (texts) main.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) else main.askToBeDefault(RoleManager.ROLE_SMS)
            }
            if (missing.isNotEmpty()) {
                SettingRow("Access", "${missing.size} permission${if (missing.size == 1) "" else "s"} still off", "Allow") { main.askForPermissions() }
            }
            SettingRow("Notifications", "Sounds and how texts and calls show up", "Open") {
                main.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, main.packageName))
            }
            SettingRow("Blocked numbers", "Calls and texts from these never reach you", "Open") {
                try {
                    main.startActivity(main.getSystemService(android.telecom.TelecomManager::class.java).createManageBlockedNumbersIntent())
                } catch (_: Exception) {
                    Toast.makeText(main, "Make Gibber Phone your calls app first.", Toast.LENGTH_SHORT).show()
                }
            }
            SettingRow("Version", update ?: "Build ${BuildConfig.VERSION_CODE}", "Update") {
                scope.launch { update = Updater.run(main) { update = it } }
            }
        }
    }
}

@Composable
private fun SettingRow(title: String, detail: String, button: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.Card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = Ink.Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Pill(button) { onClick() }
    }
}
