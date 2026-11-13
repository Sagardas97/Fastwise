@file:OptIn(ExperimentalMaterial3Api::class)

package app.fastwise.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fastwise.data.AppData
import app.fastwise.data.Fast
import app.fastwise.data.FastRepository
import app.fastwise.data.Fmt
import app.fastwise.data.Goals
import app.fastwise.data.HOUR_MS
import app.fastwise.data.MINUTE_MS
import app.fastwise.data.Meal
import app.fastwise.data.MealLabels
import app.fastwise.data.Motivation
import app.fastwise.data.Plans
import app.fastwise.data.Stats
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** "I just ate" sheet: when you finished, what it was, and whether a new fast starts. */
@Composable
fun LogMealSheet(data: AppData, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val is24 = DateFormat.is24HourFormat(ctx)
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val zone = ZoneId.systemDefault()
    val openedAt = remember { System.currentTimeMillis() }
    var minutesAgo by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf<Long?>(null) }
    val time = custom ?: (openedAt - minutesAgo * MINUTE_MS)
    val plan = data.settings.plan
    var label by remember { mutableStateOf(Plans.suggestLabel(plan, openedAt)) }
    var labelTouched by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var startFast by remember { mutableStateOf(!Plans.expectMoreMeals(plan, openedAt, Plans.mealsBefore(data, openedAt))) }
    var startTouched by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val active = data.activeFast
    val today = Fmt.date(openedAt)
    val tooEarly = startFast && active != null && time < active.start
    val activeMs = if (active != null && time >= active.start) active.durationMs(time) else null
    val morePlanned = Plans.expectMoreMeals(plan, time, Plans.mealsBefore(data, time))
    val streak = remember(data) { Stats.streaks(data, openedAt).first }
    val nearGoal = if (active != null && activeMs != null) Motivation.nearGoal(active, time) else null
    LaunchedEffect(time) {
        if (!labelTouched) label = Plans.suggestLabel(plan, time)
        if (!startTouched) startFast = !Plans.expectMoreMeals(plan, time, Plans.mealsBefore(data, time))
    }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Log a meal", style = MaterialTheme.typography.headlineSmall)
            Gap(4.dp)
            Text(
                "Your new fast starts from the time you finished eating.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Gap(20.dp)
            Text("Finished eating", style = MaterialTheme.typography.labelLarge)
            Gap(8.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Now", 15 to "15 min ago", 30 to "30 min ago", 60 to "1 hour ago").forEach { (m, text) ->
                    FilterChip(
                        selected = custom == null && minutesAgo == m,
                        onClick = {
                            custom = null
                            minutesAgo = m
                        },
                        label = { Text(text) },
                    )
                }
                val picked = custom
                FilterChip(
                    selected = picked != null,
                    onClick = { pickTime = true },
                    label = { Text(if (picked != null) Fmt.clock(picked, is24) else "Pick a time") },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
            Gap(4.dp)
            Text(
                Fmt.dayTime(time, is24, today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Gap(20.dp)
            Text("Meal", style = MaterialTheme.typography.labelLarge)
            Gap(8.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealLabels.all.forEach { l ->
                    FilterChip(
                        selected = label == l,
                        onClick = {
                            label = l
                            labelTouched = true
                        },
                        label = { Text(l) },
                    )
                }
            }

            Gap(16.dp)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("What did you eat?") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Gap(12.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        startFast = !startFast
                        startTouched = true
                    }
                    .padding(vertical = 6.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Start a new fast", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        switchHint(startFast, morePlanned, activeMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = startFast, onCheckedChange = {
                    startFast = it
                    startTouched = true
                })
            }
            if (tooEarly && active != null) {
                Gap(8.dp)
                Text(
                    "That's before your current fast started (${Fmt.clock(active.start, is24)}). " +
                        "Pick a later time, or add this meal from History.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (nearGoal != null && active != null) {
                Gap(16.dp)
                NearGoalCard(nearGoal, active.goalMs, streak)
            }

            Gap(20.dp)
            Button(
                onClick = {
                    FastRepository.logMeal(time = time, label = label, note = note, startNewFast = startFast)
                    snack.show(if (startFast) "$label logged. Fast started." else "$label logged.", "Undo") {
                        FastRepository.undo()
                    }
                    close()
                },
                enabled = !tooEarly,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
            ) {
                Text("Save meal", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (pickTime) {
        TimePickDialog(
            initial = Instant.ofEpochMilli(time).atZone(zone).toLocalTime(),
            is24 = is24,
            onDismiss = { pickTime = false },
        ) { t ->
            var candidate = ZonedDateTime.of(LocalDate.now(zone), t, zone)
            if (candidate.toInstant().toEpochMilli() > System.currentTimeMillis() + MINUTE_MS) {
                candidate = candidate.minusDays(1)
            }
            custom = candidate.toInstant().toEpochMilli()
            pickTime = false
        }
    }
}

private fun switchHint(start: Boolean, morePlanned: Boolean, activeMs: Long?): String {
    val ends = if (activeMs != null) "Ends your current ${Fmt.hm(activeMs)} fast. " else ""
    return ends + when {
        start -> "A new fast starts from the time above."
        morePlanned -> "More meals are planned today, so the timer waits for your last one."
        else -> "No new fast will start."
    }
}

@Composable
fun GoalSheet(current: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customHours by remember { mutableFloatStateOf((current / 60f).coerceIn(12f, 72f)) }

    fun choose(minutes: Int) {
        FastRepository.setGoal(minutes)
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Fasting goal", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                "Applies to the fast you're on now and every fast after it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Gap(12.dp)
            Goals.presets.forEach { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { choose(p.hours * 60) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    RadioButton(selected = current == p.hours * 60, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text(p.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${p.hours}h fast", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Gap(16.dp)
            Text(
                "Custom: ${customHours.roundToInt()} hours",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Slider(
                value = customHours,
                onValueChange = { customHours = it },
                valueRange = 12f..72f,
                steps = 59,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            OutlinedButton(
                onClick = { choose(customHours.roundToInt() * 60) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                Text("Use a ${customHours.roundToInt()}-hour goal")
            }
        }
    }
}

@Composable
fun EditFastDialog(fast: Fast, onDismiss: () -> Unit) {
    val snack = LocalSnack.current
    var start by remember { mutableLongStateOf(fast.start) }
    var end by remember { mutableStateOf(fast.end) }
    var goalHours by remember { mutableIntStateOf((fast.goalMinutes / 60).coerceAtLeast(1)) }
    val now = System.currentTimeMillis()
    val endValue = end
    val error = when {
        start > now -> "The start can't be in the future."
        endValue != null && endValue <= start -> "The end has to be after the start."
        endValue != null && endValue > now -> "The end can't be in the future."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (fast.end == null) "Current fast" else "Edit fast") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DateTimeField("Started", start) { start = it }
                if (endValue != null) DateTimeField("Ended", endValue) { end = it }
                GoalStepper(goalHours) { goalHours = it }
                Text(
                    "Length ${Fmt.hm(((endValue ?: now) - start).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = error == null,
                onClick = {
                    FastRepository.updateFast(fast.copy(start = start, end = end, goalMinutes = goalHours * 60))
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    FastRepository.deleteFast(fast.id)
                    onDismiss()
                    snack.show("Fast deleted", "Undo") { FastRepository.undo() }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun AddPastFastDialog(defaultGoalMinutes: Int, onDismiss: () -> Unit) {
    val now = remember { System.currentTimeMillis() }
    var start by remember { mutableLongStateOf(now - 18 * HOUR_MS) }
    var end by remember { mutableLongStateOf(now - 2 * HOUR_MS) }
    var goalHours by remember { mutableIntStateOf((defaultGoalMinutes / 60).coerceAtLeast(1)) }
    val error = when {
        end <= start -> "The end has to be after the start."
        end > System.currentTimeMillis() -> "The end can't be in the future."
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a past fast") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DateTimeField("Started", start) { start = it }
                DateTimeField("Ended", end) { end = it }
                GoalStepper(goalHours) { goalHours = it }
                Text(
                    "Length ${Fmt.hm((end - start).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = {
                FastRepository.addPastFast(start, end, goalHours * 60)
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Edit an existing meal, or add a past one when [meal] is null. */
@Composable
fun MealDialog(meal: Meal?, onDismiss: () -> Unit) {
    val snack = LocalSnack.current
    var time by remember { mutableLongStateOf(meal?.time ?: System.currentTimeMillis()) }
    var label by remember { mutableStateOf(meal?.label ?: MealLabels.suggest(System.currentTimeMillis())) }
    var note by remember { mutableStateOf(meal?.note ?: "") }
    val options = remember(meal) { (MealLabels.all + listOfNotNull(meal?.label)).distinct() }
    val error = if (time > System.currentTimeMillis() + MINUTE_MS) "The time can't be in the future." else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (meal == null) "Add a past meal" else "Edit meal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DateTimeField("Finished eating", time) { time = it }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { l -> FilterChip(selected = label == l, onClick = { label = l }, label = { Text(l) }) }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (meal == null) {
                    Text(
                        "Past meals don't change your fasts. Add the fast separately if you need it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = {
                if (meal == null) {
                    FastRepository.logMeal(time = time, label = label, note = note, touchFasts = false)
                } else {
                    FastRepository.updateMeal(meal.copy(time = time, label = label, note = note.trim()))
                }
                onDismiss()
            }) { Text(if (meal == null) "Add" else "Save") }
        },
        dismissButton = {
            Row {
                if (meal != null) {
                    TextButton(onClick = {
                        FastRepository.deleteMeal(meal.id)
                        onDismiss()
                        snack.show("Meal deleted", "Undo") { FastRepository.undo() }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun DateTimeField(label: String, value: Long, onChange: (Long) -> Unit) {
    val ctx = LocalContext.current
    val is24 = DateFormat.is24HourFormat(ctx)
    val zone = ZoneId.systemDefault()
    val zdt = Instant.ofEpochMilli(value).atZone(zone)
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(6.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDate = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text(zdt.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())))
            }
            OutlinedButton(onClick = { pickTime = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Text(Fmt.clock(value, is24))
            }
        }
    }
    if (pickDate) {
        DatePickDialog(zdt.toLocalDate(), onDismiss = { pickDate = false }) { d ->
            pickDate = false
            onChange(ZonedDateTime.of(d, zdt.toLocalTime(), zone).toInstant().toEpochMilli())
        }
    }
    if (pickTime) {
        TimePickDialog(zdt.toLocalTime(), is24, onDismiss = { pickTime = false }) { t ->
            pickTime = false
            onChange(ZonedDateTime.of(zdt.toLocalDate(), t, zone).toInstant().toEpochMilli())
        }
    }
}

@Composable
fun DatePickDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val todayUtc = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) onPick(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun TimePickDialog(initial: LocalTime, is24: Boolean, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

@Composable
fun GoalStepper(hours: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Goal",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = { onChange((hours - 1).coerceAtLeast(1)) }) {
            Icon(Icons.Outlined.Remove, contentDescription = "Shorter goal")
        }
        Text("${hours}h", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
        FilledTonalIconButton(onClick = { onChange((hours + 1).coerceAtMost(96)) }) {
            Icon(Icons.Outlined.Add, contentDescription = "Longer goal")
        }
    }
}
