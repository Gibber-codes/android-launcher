package codes.gibber.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** One idea card from the board. `kind` is "feature" or "research". */
data class Idea(val key: String, val title: String, val kind: String)

/** What the hub last sent, and when; null while it hasn't answered. */
private class IdeasRead(val ideas: List<Idea>?, val at: Long)

/** Reads the ideas from the hub now and every two minutes while they're on screen. */
@Composable
private fun rememberIdeas(hubUrl: String): IdeasRead? {
    val read by produceState<IdeasRead?>(null, hubUrl) {
        val api = HubApi(hubUrl)
        while (true) {
            value = IdeasRead(api.ideas(), System.currentTimeMillis())
            delay(120_000)
        }
    }
    return read
}

@Composable
private fun KindAndKey(idea: Idea) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(idea.key, color = Dark.W62, fontFamily = Dark.Mono, fontSize = 10.sp)
        if (idea.kind.isNotBlank()) {
            Text(
                idea.kind.uppercase(),
                color = if (idea.kind.startsWith("research", ignoreCase = true)) Dark.Research else Dark.Feature,
                fontFamily = Dark.Mono,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/** The widget on Dev: your newest three ideas. Tap it to open Ideas. */
@Composable
fun IdeasWidget(hubUrl: String, onOpen: () -> Unit) {
    val read = rememberIdeas(hubUrl)
    val ideas = read?.ideas
    Column(
        Modifier
            .fillMaxWidth()
            .frost()
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Glyph(Glyphs.Bulb, 16.dp, Color(0xFFF2C230))
            HSpace(8.dp)
            Text(
                if (ideas == null) "IDEAS" else "IDEAS · ${ideas.size}",
                color = Color.White,
                fontFamily = Dark.Mono,
                fontSize = 12.sp,
                letterSpacing = 0.72.sp,
                modifier = Modifier.weight(1f),
            )
            Glyph(Glyphs.Forward, 16.dp, Dark.W62)
        }
        when {
            read == null -> IdeaLine("Asking the hub…")
            ideas == null -> IdeaLine("Not read: the hub doesn't send ideas yet")
            ideas.isEmpty() -> IdeaLine("No ideas on the board")
            else -> ideas.take(3).forEach { idea ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth().ruleAbove().padding(vertical = 6.dp),
                ) {
                    KindAndKey(idea)
                    Text(idea.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun IdeaLine(text: String) {
    Text(text, color = Dark.W62, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().ruleAbove().padding(vertical = 8.dp))
}

/** Ideas, its own app: the board's Idea column, newest first, with Features and Research filters. Read-only. */
@Composable
fun IdeasScreen(hubUrl: String, onClose: () -> Unit) {
    BackHandler(enabled = true) { onClose() }
    HideSystemBars()
    val read = rememberIdeas(hubUrl)
    var filter by remember { mutableStateOf("All") }
    val all = read?.ideas.orEmpty()
    val shown = when (filter) {
        "Features" -> all.filter { it.kind.startsWith("feature", ignoreCase = true) }
        "Research" -> all.filter { it.kind.startsWith("research", ignoreCase = true) }
        else -> all
    }
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        OrbBackground()
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(start = 22.dp, end = 22.dp, top = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { BackLink("Back", onClose) }
                Text("Gabriel board", color = Dark.W62, fontSize = 12.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppTile(IdeasTile, 40.dp, corner = 11.dp)
                ScreenTitle("Ideas")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("All", "Features", "Research").forEach { f ->
                    Text(
                        if (f == "All" && read?.ideas != null) "All ${all.size}" else f,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.frost(hi = filter == f).clickable { filter = f }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                if (read?.ideas == null) {
                    item { IdeaLine(if (read == null) "Asking the hub…" else "Not read: the hub doesn't send ideas yet") }
                }
                items(shown, key = { it.key + it.title }) { idea ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxWidth().ruleAbove().padding(vertical = 11.dp),
                    ) {
                        KindAndKey(idea)
                        Text(idea.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                item {
                    val at = read?.at
                    Text(
                        "From Silk Boards, the Idea column" + if (at != null && read?.ideas != null) " · updated ${ago(at)}" else "",
                        color = Dark.W40,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                    )
                }
            }
        }
    }
}

/** Ideas' own app entry, used to draw its yellow tile. */
private val IdeasTile = AppEntry(label = "Ideas", packageName = "codes.gibber.launcher", activityName = "gibber-ideas")

private fun ago(at: Long): String {
    val min = ((System.currentTimeMillis() - at) / 60_000).toInt()
    return if (min < 1) "just now" else "$min min ago"
}
