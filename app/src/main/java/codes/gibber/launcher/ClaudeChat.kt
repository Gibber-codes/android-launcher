package codes.gibber.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Talking to Claude from the launcher: each project can keep the link of its own Claude chat, which
 * you paste in. With no link, it opens Claude Duet, else the Claude app.
 */
object ClaudeChat {
    /** Claude Duet if it's on the phone, else the Claude app, else null. */
    fun app(context: Context): Intent? {
        val pm = context.packageManager
        val all = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val pick = listOf("Claude Duet", "Claude").firstNotNullOfOrNull { name ->
            all.firstOrNull { it.loadLabel(pm).toString().trim().equals(name, ignoreCase = true) }
        } ?: return null
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(pick.activityInfo.packageName, pick.activityInfo.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** A pasted link as an https address, or "" when it isn't one. */
    fun cleaned(raw: String): String {
        val t = raw.trim()
        if (t.isEmpty()) return ""
        val url = if (t.contains("://")) t else "https://$t"
        return if (url.startsWith("https://") && Uri.parse(url).host != null) url else ""
    }

    /** Opens the project's chat link, or the Claude app when it has none or the link won't open. */
    fun open(context: Context, link: String) {
        if (link.isNotBlank()) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // Nothing on the phone opens it; fall back to the app.
            }
        }
        val app = app(context)
        if (app != null) context.startActivity(app)
        else Toast.makeText(context, "Claude isn't on this phone.", Toast.LENGTH_LONG).show()
    }
}

/**
 * One wide tap at the top of the Dev page that opens your chat with Claude. Hold it to paste the
 * link of the chat it should open.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TalkToClaude(prefs: Prefs) {
    val context = LocalContext.current
    var link by remember { mutableStateOf(prefs.chatLink("dev")) }
    var editing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF1F1E23))
            .border(1.dp, Color(0xFF34333A), shape)
            .combinedClickable(onClick = { ClaudeChat.open(context, link) }, onLongClick = { editing = true })
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("Talk to Claude", color = Look.Cream, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                if (link.isBlank()) "Hold to set the chat it opens" else "Opens your app-launcher chat · hold to change",
                color = Look.Muted,
                fontSize = 12.sp,
            )
        }
        MicMark(36.dp)
    }
    if (editing) {
        ChatLinkDialog("Dev", link, onClose = { editing = false }) {
            prefs.setChatLink("dev", it)
            link = it
        }
    }
}

/** Paste or clear the Claude chat link a project opens. */
@Composable
fun ChatLinkDialog(project: String, current: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Claude chat for $project") },
        text = {
            Column {
                Text("Paste the link of the chat this opens. Leave it empty to just open Claude.", fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("https://claude.ai/...", fontSize = 12.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val url = ClaudeChat.cleaned(draft)
                if (draft.isNotBlank() && url.isEmpty()) {
                    Toast.makeText(context, "That isn't a web link.", Toast.LENGTH_LONG).show()
                } else {
                    onSave(url)
                    onClose()
                }
            }) { Text("Save link") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** A small microphone in a lilac circle. */
@Composable
fun MicMark(size: Dp) {
    Canvas(Modifier.size(size).clip(CircleShape).background(Look.Lilac)) {
        val w = this.size.width
        val h = this.size.height
        val ink = Color(0xFF141317)
        val line = w * 0.06f
        drawRoundRect(ink, topLeft = Offset(w * 0.42f, h * 0.24f), size = Size(w * 0.16f, h * 0.3f), cornerRadius = CornerRadius(w * 0.08f))
        drawArc(ink, 0f, 180f, false, topLeft = Offset(w * 0.33f, h * 0.3f), size = Size(w * 0.34f, h * 0.32f), style = Stroke(width = line, cap = StrokeCap.Round))
        drawLine(ink, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.5f, h * 0.74f), strokeWidth = line, cap = StrokeCap.Round)
    }
}
