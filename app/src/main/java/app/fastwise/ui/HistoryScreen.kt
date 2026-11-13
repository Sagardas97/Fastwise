@file:OptIn(ExperimentalFoundationApi::class)

package app.fastwise.ui

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.fastwise.data.AppData
import app.fastwise.data.Fast
import app.fastwise.data.Fmt
import app.fastwise.data.Meal
import java.time.LocalDate

private sealed interface Entry {
    val key: String
}

private data class FastEntry(val fast: Fast) : Entry {
    override val key: String get() = "f" + fast.id
}

private data class MealEntry(val meal: Meal) : Entry {
    override val key: String get() = "m" + meal.id
}

private fun buildGroups(d: AppData, today: LocalDate): List<Pair<LocalDate, List<Entry>>> {
    val list = ArrayList<Pair<Long, Entry>>()
    d.liveFasts.forEach { f -> list += (f.end ?: Long.MAX_VALUE) to FastEntry(f) }
    d.liveMeals.forEach { m -> list += m.time to MealEntry(m) }
    list.sortByDescending { it.first }
    return list
        .groupBy({ if (it.first == Long.MAX_VALUE) today else Fmt.date(it.first) }, { it.second })
        .toList()
}

@Composable
fun HistoryScreen(data: AppData) {
    val now by rememberNow(30_000L)
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val today = Fmt.date(now)
    var editFast by remember { mutableStateOf<Fast?>(null) }
    var editMeal by remember { mutableStateOf<Meal?>(null) }
    var addMeal by remember { mutableStateOf(false) }
    var addFast by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val groups = remember(data, today) { buildGroups(data, today) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item(key = "title") {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp)) {
                    Text("History", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${Fmt.plural(data.liveMeals.size, "meal")} and ${Fmt.plural(data.liveFasts.size, "fast")} logged",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (groups.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.padding(24.dp)) {
                        Text("Nothing logged yet", style = MaterialTheme.typography.titleLarge)
                        Gap(6.dp)
                        Text(
                            "After your next meal, tap I just ate on the Timer tab. Meals and fasts show up here, " +
                                "and you can tap any of them to fix the time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            groups.forEach { (date, entries) ->
                stickyHeader(key = "d$date") { DayHeader(date, today, entries) }
                items(entries, key = { it.key }) { entry ->
                    when (entry) {
                        is FastEntry -> FastRow(entry.fast, now, is24, today) { editFast = entry.fast }
                        is MealEntry -> MealRow(entry.meal, is24) { editMeal = entry.meal }
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            ExtendedFloatingActionButton(
                text = { Text("Add entry") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { menu = true },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Past meal") },
                    leadingIcon = { Icon(Icons.Outlined.Restaurant, contentDescription = null) },
                    onClick = {
                        menu = false
                        addMeal = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Past fast") },
                    leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = null) },
                    onClick = {
                        menu = false
                        addFast = true
                    },
                )
            }
        }
    }

    editFast?.let { f -> EditFastDialog(f) { editFast = null } }
    editMeal?.let { m -> MealDialog(m) { editMeal = null } }
    if (addMeal) MealDialog(null) { addMeal = false }
    if (addFast) AddPastFastDialog(data.settings.goalMinutes) { addFast = false }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, entries: List<Entry>) {
    val meals = entries.count { it is MealEntry }
    val fasts = entries.count { it is FastEntry }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 6.dp),
        ) {
            Text(Fmt.dayName(date, today), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text(
                listOfNotNull(
                    if (meals > 0) Fmt.plural(meals, "meal") else null,
                    if (fasts > 0) Fmt.plural(fasts, "fast") else null,
                ).joinToString(", "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FastRow(f: Fast, now: Long, is24: Boolean, today: LocalDate, onClick: () -> Unit) {
    val fc = LocalFastColors.current
    val duration = f.durationMs(now)
    val met = duration >= f.goalMs
    val end = f.end
    val range = when {
        end == null -> "Since ${Fmt.dayTime(f.start, is24, today)}"
        Fmt.date(end) == Fmt.date(f.start) -> "${Fmt.clock(f.start, is24)} to ${Fmt.clock(end, is24)}"
        else -> "${Fmt.dayTime(f.start, is24, today)} to ${Fmt.clock(end, is24)}"
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { MiniRing(duration.toFloat() / f.goalMs, met) },
        headlineContent = {
            Text(
                if (end == null) "Fasting now, ${Fmt.hm(duration)}" else "${Fmt.hm(duration)} fast",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = { Text(range) },
        trailingContent = {
            if (met) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Goal met", tint = fc.success)
            } else {
                Text("${Fmt.hm(f.goalMs)} goal", style = MaterialTheme.typography.labelMedium)
            }
        },
    )
}

@Composable
private fun MealRow(m: Meal, is24: Boolean, onClick: () -> Unit) {
    val fc = LocalFastColors.current
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(fc.meal.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = fc.meal, modifier = Modifier.size(18.dp))
            }
        },
        headlineContent = { Text(m.label, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                if (m.note.isBlank()) Fmt.clock(m.time, is24) else "${Fmt.clock(m.time, is24)}, ${m.note}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}
