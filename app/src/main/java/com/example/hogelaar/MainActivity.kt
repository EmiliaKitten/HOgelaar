package com.example.hogelaar

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter

// ---------- Model ----------

data class Event(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val title: String,
    val location: String
)

// ---------- Minimal iCal parser (no RRULE support; TimeEdit exports individual events) ----------

fun parseIcs(text: String): List<Event> {
    // Unfold wrapped lines: a line starting with space/tab continues the previous one
    val lines = mutableListOf<String>()
    for (raw in text.lines()) {
        if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) {
            lines[lines.lastIndex] = lines.last() + raw.substring(1)
        } else lines += raw.trimEnd('\r')
    }

    val events = mutableListOf<Event>()
    var props: MutableMap<String, Pair<String, Map<String, String>>>? = null

    for (line in lines) {
        when {
            line == "BEGIN:VEVENT" -> props = mutableMapOf()
            line == "END:VEVENT" -> {
                props?.let { p ->
                    val s = p["DTSTART"]?.let { parseTime(it.first, it.second) }
                    val e = p["DTEND"]?.let { parseTime(it.first, it.second) } ?: s
                    if (s != null && e != null) {
                        events += Event(
                            s, e,
                            unescape(p["SUMMARY"]?.first ?: "(no title)"),
                            unescape(p["LOCATION"]?.first ?: "")
                        )
                    }
                }
                props = null
            }
            props != null && ':' in line -> {
                val colon = line.indexOf(':')
                val keyParts = line.substring(0, colon).split(';')
                val params = keyParts.drop(1).mapNotNull {
                    it.split('=', limit = 2).takeIf { kv -> kv.size == 2 }?.let { kv -> kv[0] to kv[1] }
                }.toMap()
                props[keyParts[0]] = line.substring(colon + 1) to params
            }
        }
    }
    return events.sortedBy { it.start }
}

private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

fun parseTime(value: String, params: Map<String, String>): ZonedDateTime? = try {
    val local = ZoneId.systemDefault()
    when {
        value.length == 8 ->
            LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(local)
        value.endsWith("Z") ->
            LocalDateTime.parse(value.dropLast(1), dateTimeFmt)
                .atZone(ZoneOffset.UTC).withZoneSameInstant(local)
        params["TZID"] != null ->
            LocalDateTime.parse(value, dateTimeFmt)
                .atZone(ZoneId.of(params["TZID"]!!)).withZoneSameInstant(local)
        else -> LocalDateTime.parse(value, dateTimeFmt).atZone(local)
    }
} catch (e: Exception) {
    null
}

fun unescape(s: String) = s.replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

fun weekStartFor(date: LocalDate): LocalDate {
    val currentWeekStart = date.with(DayOfWeek.MONDAY)
    return if (date.dayOfWeek >= DayOfWeek.SATURDAY) {
        currentWeekStart.plusWeeks(1)
    } else {
        currentWeekStart
    }
}

// ---------- Loading ----------

fun loadTodayEvents(context: Context, fileName: String = "schedule.ics"): List<Event> {
    val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
    val today = LocalDate.now()
    return parseIcs(text).filter { it.start.toLocalDate() == today }
}

// ---------- UI ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                TodayScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val events = remember { runCatching { parseIcs(context.assets.open("schedule.ics").bufferedReader().use { it.readText() }) } }
    var showWeekView by remember { mutableStateOf(false) }

    if (showWeekView) {
        WeekScreen(
            events = events,
            onShowToday = { showWeekView = false }
        )
    } else {
        val dateTitle = remember {
            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(dateTitle) },
                    actions = {
                        TextButton(onClick = { showWeekView = true }) {
                            Text("Week view")
                        }
                    }
                )
            }
        ) { padding ->
            val list = events.getOrNull()?.filter { it.start.toLocalDate() == LocalDate.now() }
            when {
                events.isFailure -> Message("Couldn't read schedule.ics", padding)
                list.isNullOrEmpty() -> Message("Nothing scheduled today", padding)
                else -> EventList(list, padding)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekScreen(events: Result<List<Event>>, onShowToday: () -> Unit) {
    val weekStart = remember { weekStartFor(LocalDate.now()) }
    val weekEnd = weekStart.plusDays(6)
    val rangeFormatter = DateTimeFormatter.ofPattern("d MMM")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${weekStart.format(rangeFormatter)} – ${weekEnd.format(rangeFormatter)}")
                },
                navigationIcon = {
                    TextButton(onClick = onShowToday) {
                        Text("Today")
                    }
                }
            )
        }
    ) { padding ->
        val allEvents = events.getOrNull()
        if (allEvents == null) {
            Message("Couldn't read schedule.ics", padding)
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items((0L..6L).toList()) { dayOffset ->
                    val date = weekStart.plusDays(dayOffset)
                    val dayEvents = allEvents.filter { it.start.toLocalDate() == date }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (dayEvents.isEmpty()) {
                            Text(
                                "Nothing scheduled",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            dayEvents.forEach { EventCard(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventList(list: List<Event>, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list) { EventCard(it) }
    }
}

@Composable
fun Message(text: String, padding: PaddingValues) {
    Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text)
    }
}

@Composable
fun EventCard(event: Event) {
    val time = DateTimeFormatter.ofPattern("HH:mm")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "${event.start.format(time)} – ${event.end.format(time)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            if (event.location.isNotBlank()) {
                Text(event.location, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}