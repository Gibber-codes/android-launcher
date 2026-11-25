package codes.gibber.launcher

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Gibber Settings: where the hub runs, which chat Talk to Claude opens for each project, and the launcher's build. */
@Composable
fun SettingsScreen(
    prefs: Prefs,
    hubUrl: String,
    onSaveUrl: (String) -> Unit,
    resumes: Int,
    onClose: () -> Unit,
) {
    BackHandler(enabled = true) { onClose() }
    HideSystemBars()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(hubUrl.removePrefix("http://")) }
    var chatFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var chats by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        OrbBackground()
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 28.dp),
        ) {
            BackLink("Dev", onClose)
            ScreenTitle("Gibber Settings")

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Head("Hub")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Where your hub runs", color = Dark.W62, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.weight(1f).height(44.dp).frost().padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            BasicTextField(
                                value = address,
                                onValueChange = { address = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                cursorBrush = SolidColor(Color.White),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { onSaveUrl(address) }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        FrostButton("Save", hi = true, height = 44.dp) {
                            onSaveUrl(address)
                            Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                        }
                    }
                    Text("Your PC on Tailscale is 100.112.100.42:5175. Turn Tailscale on on this phone to reach it.", color = Dark.W40, fontSize = 11.sp)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Head("Chats Talk to Claude opens")
                chats
                val projects = listOf("dev" to "Dev page") + prefs.hubGames
                projects.forEach { (id, name) ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable { chatFor = id to name },
                    ) {
                        Text(name, color = Dark.W62, fontSize = 12.sp)
                        Box(
                            Modifier.fillMaxWidth().height(44.dp).frost().padding(horizontal = 14.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            val link = prefs.chatLink(id)
                            Text(
                                if (link.isBlank()) "Opens Claude · tap to set a chat" else chatName(link),
                                color = if (link.isBlank()) Dark.W62 else Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Head("Launcher")
                StatusRow(
                    dot = Dark.Green,
                    title = "Build ${BuildConfig.VERSION_CODE}",
                    sub = busy ?: if (UpdateNotice.allowed(context)) "Tells you when a new build is out"
                        else "Notifications are off, so no update notices",
                    action = "Check",
                    onAction = {
                        if (busy == null) scope.launch {
                            Updater.install(context, busy = { busy = it }, say = {
                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            })
                        }
                    },
                )
            }
        }
    }

    chatFor?.let { (id, name) ->
        ChatLinkDialog(name, prefs.chatLink(id), onClose = { chatFor = null }) {
            prefs.setChatLink(id, it)
            chats++
        }
    }
}

/** A chat link as a short name: a channel's or chat's last part, like app-launcher. */
private fun chatName(link: String): String =
    android.net.Uri.parse(link).lastPathSegment?.ifBlank { null } ?: link
