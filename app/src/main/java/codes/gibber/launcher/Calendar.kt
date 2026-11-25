package codes.gibber.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One event on the phone's calendar. */
data class CalEvent(val title: String, val start: Long, val day: LocalDate, val allDay: Boolean)

object Cal {
    fun allowed(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Every event from `from` to `to`, across all of the phone's calendars. */
    suspend fun events(context: Context, from: LocalDate, to: LocalDate): List<CalEvent> = withContext(Dispatchers.IO) {
        if (!allowed(context)) return@withContext emptyList()
        val zone = ZoneId.systemDefault()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val cols = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.ALL_DAY)
        val out = mutableListOf<CalEvent>()
        try {
            context.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val start = c.getLong(1)
                    val allDay = c.getInt(2) == 1
                    // All-day events are stored at midnight UTC.
                    val day = Instant.ofEpochMilli(start).atZone(if (allDay) ZoneId.of("UTC") else zone).toLocalDate()
                    out += CalEvent(c.getString(0).orEmpty().ifBlank { "Busy" }, start, day, allDay)
                }
            }
        } catch (e: Exception) { }
        out
    }

    /** Opens the phone's calendar at a day. */
    fun open(context: Context, day: LocalDate) {
        val millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val uri = Uri.parse("content://com.android.calendar/time/$millis")
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }
}

/** Home's month: it fills the space it's given, with each day's first event and the next one under it. */
@Composable
fun CalendarCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var shift by remember { mutableIntStateOf(0) }
    var allowed by remember { mutableStateOf(Cal.allowed(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    val today = LocalDate.now()
    val month = YearMonth.from(today).plusMonths(shift.toLong())
    val first = month.atDay(1)
    // Sunday first, like the design.
    val lead = first.dayOfWeek.value % 7
    val weeks = (lead + month.lengthOfMonth() + 6) / 7

    val events by produceState(emptyList<CalEvent>(), month, allowed) {
        value = Cal.events(context, first, month.atEndOfMonth().plusDays(31))
    }
    val byDay = remember(events) { events.groupBy { it.day } }
    val next = remember(events) {
        val now = System.currentTimeMillis()
        events.firstOrNull { !it.allDay && it.start >= now } ?: events.firstOrNull { it.allDay && !it.day.isBefore(today) }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .frost()
            .padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())).uppercase(),
                color = Color.White,
                fontFamily = Dark.Mono,
                fontSize = 12.sp,
                letterSpacing = 0.72.sp,
                modifier = Modifier.weight(1f).clickable { shift = 0 },
            )
            TapGlyph(Glyphs.Back, 14.dp, Dark.W62) { shift-- }
            TapGlyph(Glyphs.Forward, 14.dp, Dark.W62) { shift++ }
        }
        Column(Modifier.weight(1f).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                    Text(it, color = Dark.W62, fontFamily = Dark.Mono, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            for (w in 0 until weeks) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (d in 0 until 7) {
                        val n = w * 7 + d - lead + 1
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (n in 1..month.lengthOfMonth()) {
                                val day = month.atDay(n)
                                DayCell(day, today, byDay[day].orEmpty()) { Cal.open(context, day) }
                            } else {
                                Box(Modifier.fillMaxSize().ruleAbove())
                            }
                        }
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .ruleAbove()
                .clickable { if (!allowed) ask.launch(Manifest.permission.READ_CALENDAR) else Cal.open(context, next?.day ?: today) }
                .padding(top = 8.dp, start = 2.dp, end = 2.dp),
        ) {
            Box(Modifier.width(3.dp).height(16.dp).background(Brush.verticalGradient(listOf(Dark.Blue, Dark.Red))))
            when {
                !allowed -> Text("Show your events", color = Color.White, fontSize = 13.sp)
                next == null -> Text("Nothing coming up", color = Dark.W62, fontSize = 13.sp)
                else -> {
                    Text(whenLabel(next, today), color = Dark.W62, fontFamily = Dark.Mono, fontSize = 11.sp)
                    Text(next.title, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun whenLabel(e: CalEvent, today: LocalDate): String {
    val dayWord = when (e.day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> e.day.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))
    }
    if (e.allDay) return dayWord
    val time = Instant.ofEpochMilli(e.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    return "$dayWord $time"
}

@Composable
private fun DayCell(day: LocalDate, today: LocalDate, events: List<CalEvent>, onTap: () -> Unit) {
    val isToday = day == today
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxSize()
            .ruleAbove()
            .clickable(onClick = onTap)
            .padding(horizontal = 2.dp, vertical = 3.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .widthIn(min = 20.dp)
                .then(if (isToday) Modifier.background(Color.White) else Modifier),
        ) {
            Text(
                "${day.dayOfMonth}",
                color = when {
                    isToday -> Color(0xFF111111)
                    day.isBefore(today) -> Dark.W62
                    else -> Color.White
                },
                fontSize = 12.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
        events.firstOrNull()?.let { e ->
            Text(
                e.title,
                color = Color.White,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isToday) Dark.Red.copy(alpha = 0.55f) else Dark.Blue.copy(alpha = 0.45f))
                    .padding(horizontal = 3.dp, vertical = 2.dp),
            )
        }
    }
}
