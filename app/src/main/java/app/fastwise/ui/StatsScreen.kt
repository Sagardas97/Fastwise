@file:OptIn(ExperimentalMaterial3Api::class)

package app.fastwise.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fastwise.data.AppData
import app.fastwise.data.DayStats
import app.fastwise.data.Fmt
import app.fastwise.data.HOUR_MS
import app.fastwise.data.MINUTE_MS
import app.fastwise.data.MealPlan
import app.fastwise.data.Motivation
import app.fastwise.data.Plans
import app.fastwise.data.PeriodSummary
import app.fastwise.data.Stats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
fun StatsScreen(data: AppData) {
    val now by rememberNow(60_000L)
    val ctx = LocalContext.current
    val is24 = DateFormat.is24HourFormat(ctx)
    val scheme = MaterialTheme.colorScheme
    val fc = LocalFastColors.current
    val today = Fmt.date(now)
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    val monday = data.settings.weekStartsMonday

    val from: LocalDate
    val to: LocalDate
    val title: String
    if (mode == 0) {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(if (monday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY))
        from = weekStart.plusWeeks(offset.toLong())
        to = from.plusDays(6)
        title = if (offset == 0) "This week" else "${Fmt.shortDate(from)} to ${Fmt.shortDate(to)}"
    } else {
        val ym = YearMonth.from(today).plusMonths(offset.toLong())
        from = ym.atDay(1)
        to = ym.atEndOfMonth()
        title = Fmt.monthTitle(ym)
    }

    val days = remember(data, from, to, now) { Stats.days(data, from, to, now) }
    val firstDay = remember(data) { Stats.firstDay(data) }
    val summary = remember(days, today, firstDay) { Stats.summarize(days, today, firstDay) }
    val streak = remember(data, now) { Stats.streaks(data, now) }
    val goalMs = data.settings.goalMinutes * MINUTE_MS
    val goalHours = goalMs.toFloat() / HOUR_MS

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Gap(12.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Summary", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { shareSummary(ctx, title, summary, streak) }) {
                Icon(Icons.Outlined.Share, contentDescription = "Share summary")
            }
        }
        Gap(8.dp)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Week", "Month").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i,
                    onClick = {
                        mode = i
                        offset = 0
                        selected = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                    label = { Text(label) },
                )
            }
        }
        Gap(8.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                offset -= 1
                selected = null
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous") }
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(
                onClick = {
                    offset += 1
                    selected = null
                },
                enabled = offset < 0,
            ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next") }
        }

        Gap(8.dp)
        if (summary.trackedDays == 0) {
            Text(
                "No meals or fasts in this period yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        val plan = data.settings.plan
        // Today can still change (another meal may come), so only finished days are judged against the plan.
        val onPlanDays = plan?.let { p -> days.count { it.date.isBefore(today) && it.hasData && Plans.matches(it, p) } }
        FigureGrid(summary, streak, plan, onPlanDays)
        if (mode == 0) {
            val target = data.settings.weeklyTargetDays
            val metDays = days.count { it.goalMet && !it.date.isAfter(today) }
            Gap(18.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Weekly target", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (metDays >= target) "Reached, $metDays of $target days" else "$metDays of $target days",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (metDays >= target) fc.success else scheme.onSurfaceVariant,
                )
            }
            Gap(8.dp)
            LinearProgressIndicator(
                progress = { (metDays.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = if (metDays >= target) fc.success else fc.arcStart,
                trackColor = fc.track,
            )
        }

        Gap(28.dp)
        Text("Hours fasted each day", style = MaterialTheme.typography.titleLarge)
        Text(
            "Dashed line is your ${Fmt.hm(goalMs)} goal. Tap a day for details.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Gap(14.dp)
        FastingBars(
            days = days,
            goalHours = goalHours,
            today = today,
            selected = selected,
            showValues = mode == 0,
            onSelect = { d -> selected = if (selected == d) null else d },
            label = { d ->
                if (mode == 0) {
                    d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
                } else if (d.date.dayOfMonth == 1 || d.date.dayOfMonth % 5 == 0) {
                    d.date.dayOfMonth.toString()
                } else {
                    null
                }
            },
        )
        days.firstOrNull { it.date == selected }?.let { d ->
            Gap(12.dp)
            DayDetail(d, today, is24)
        }

        Gap(28.dp)
        if (mode == 0) {
            Text("Daily rhythm", style = MaterialTheme.typography.titleLarge)
            Text(
                "Fasting in amber, meals as dots, midnight to midnight.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Gap(14.dp)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                days.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (d.date == today) scheme.onSurface else scheme.onSurfaceVariant,
                            modifier = Modifier.width(44.dp),
                        )
                        DayStrip(d, Modifier.weight(1f))
                    }
                }
                Row {
                    Box(Modifier.width(44.dp))
                    HourAxis(is24, Modifier.weight(1f))
                }
            }
        } else {
            Text("Calendar", style = MaterialTheme.typography.titleLarge)
            Text(
                "Brighter days had more fasting. A dot means a fast hit its goal.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Gap(14.dp)
            MonthHeat(days, monday, today, goalMs, selected) { d -> selected = if (selected == d) null else d }
        }

        Gap(28.dp)
        val badges = remember(data, now) { Motivation.badges(data, now) }
        Text("Achievements", style = MaterialTheme.typography.titleLarge)
        Text(
            "${badges.count { it.unlocked }} of ${badges.size} unlocked. They reward showing up consistently, not fasting longer.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Gap(14.dp)
        BadgeGrid(badges)
        Gap(32.dp)
    }
}

@Composable
private fun FigureGrid(s: PeriodSummary, streak: Pair<Int, Int>, plan: MealPlan?, onPlanDays: Int?) {
    val mealsLabel = if (plan != null) {
        String.format(Locale.US, "Meals, %.1f a day (plan: %d)", s.avgMeals, plan.slots.size)
    } else {
        String.format(Locale.US, "Meals, %.1f a day", s.avgMeals)
    }
    val figures = buildList {
        add(Fmt.hm(s.avgFastingMs) to "Average fasting per day")
        add(Fmt.hm(s.longestFastMs) to "Longest fast")
        add(s.totalMeals.toString() to mealsLabel)
        add("${s.goalDays} of ${s.goalDaysOutOf}" to "Days you hit your goal")
        add((s.avgWindowMs?.let { Fmt.hm(it) } ?: "None") to "Average eating window")
        add(Fmt.plural(streak.first, "day") to "Goal streak, best ${streak.second}")
        if (plan != null && onPlanDays != null && s.finishedDays > 0) {
            add("$onPlanDays of ${s.finishedDays}" to "Days that matched your plan")
        }
    }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            figures.chunked(2).forEach { pair ->
                Row {
                    pair.forEach { (value, label) ->
                        Column(Modifier.weight(1f)) {
                            Text(value, style = MaterialTheme.typography.headlineSmall)
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FastingBars(
    days: List<DayStats>,
    goalHours: Float,
    today: LocalDate,
    selected: LocalDate?,
    showValues: Boolean,
    onSelect: (LocalDate) -> Unit,
    label: (DayStats) -> String?,
) {
    val fc = LocalFastColors.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val maxHours = maxOf(goalHours * 1.2f, days.maxOfOrNull { it.fastingMs.toFloat() / HOUR_MS } ?: 0f, 4f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .pointerInput(days) {
                detectTapGestures { pos ->
                    if (days.isNotEmpty()) {
                        val i = (pos.x / (size.width.toFloat() / days.size)).toInt().coerceIn(0, days.lastIndex)
                        if (!days[i].date.isAfter(today)) onSelect(days[i].date)
                    }
                }
            },
    ) {
        if (days.isEmpty()) return@Canvas
        val axisH = 20.dp.toPx()
        val valueH = if (showValues) 16.dp.toPx() else 0f
        val chartH = size.height - axisH - valueH
        val slot = size.width / days.size
        val barW = slot * (if (days.size > 10) 0.62f else 0.46f)
        val top = valueH

        val goalY = top + chartH - goalHours / maxHours * chartH
        drawLine(
            color = fc.success.copy(alpha = 0.8f),
            start = Offset(0f, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )

        days.forEachIndexed { i, d ->
            val hours = d.fastingMs.toFloat() / HOUR_MS
            val future = d.date.isAfter(today)
            val h = (hours / maxHours * chartH).coerceAtLeast(if (future) 0f else 3.dp.toPx())
            val x = i * slot + (slot - barW) / 2f
            val color = when {
                future -> fc.track
                d.date == selected -> fc.arcEnd
                d.date == today -> fc.arcStart
                else -> fc.arcStart.copy(alpha = 0.78f)
            }
            if (!future) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, top + chartH - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(minOf(barW / 2f, 6.dp.toPx())),
                )
            }
            if (showValues && !future && hours > 0f) {
                val v = measurer.measure(AnnotatedString(String.format(Locale.US, "%.1f", hours)), style = labelStyle)
                drawText(
                    v,
                    color = scheme.onSurfaceVariant,
                    topLeft = Offset(i * slot + slot / 2f - v.size.width / 2f, top + chartH - h - v.size.height - 2.dp.toPx()),
                )
            }
            label(d)?.let { text ->
                val l = measurer.measure(AnnotatedString(text), style = labelStyle)
                drawText(
                    l,
                    color = if (d.date == today) scheme.onSurface else scheme.onSurfaceVariant,
                    topLeft = Offset(i * slot + slot / 2f - l.size.width / 2f, top + chartH + 6.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun DayDetail(d: DayStats, today: LocalDate, is24: Boolean) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(Fmt.dayName(d.date, today), style = MaterialTheme.typography.titleMedium)
            Gap(4.dp)
            val lines = buildList {
                add("${Fmt.plural(d.meals, "meal")}, ${Fmt.hm(d.fastingMs)} fasting")
                if (d.longestFastMs > 0) add("Longest fast ending this day: ${Fmt.hm(d.longestFastMs)}" + if (d.goalMet) ", goal met" else "")
                val first = d.firstMeal
                val last = d.lastMeal
                if (first != null && last != null && d.meals >= 2) {
                    add("Ate between ${Fmt.clock(first, is24)} and ${Fmt.clock(last, is24)} (${Fmt.hm(last - first)})")
                }
            }
            lines.forEach {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Gap(10.dp)
            DayStrip(d)
        }
    }
}

@Composable
private fun MonthHeat(
    days: List<DayStats>,
    monday: Boolean,
    today: LocalDate,
    goalMs: Long,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    if (days.isEmpty()) return
    val fc = LocalFastColors.current
    val scheme = MaterialTheme.colorScheme
    val startDow = if (monday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val lead = (days.first().date.dayOfWeek.value - startDow.value + 7) % 7
    val cells: List<DayStats?> = List(lead) { null } + days
    val shape = RoundedCornerShape(10.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 7) {
                Text(
                    startDow.plus(i.toLong()).getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val d = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (d != null) {
                            val future = d.date.isAfter(today)
                            val intensity = if (future) 0f else (d.fastingMs.toFloat() / goalMs).coerceIn(0f, 1f)
                            val bright = intensity > 0.6f
                            var cell = Modifier
                                .fillMaxSize()
                                .clip(shape)
                                .background(if (future) Color.Transparent else lerp(fc.track, fc.arcStart, intensity))
                            if (d.date == today) cell = cell.border(1.5.dp, scheme.onSurface, shape)
                            if (d.date == selected) cell = cell.border(2.dp, fc.arcEnd, shape)
                            if (!future) cell = cell.clickable { onSelect(d.date) }
                            Box(cell, contentAlignment = Alignment.Center) {
                                Text(
                                    d.date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        future -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        bright -> Color(0xFF2B1A00)
                                        else -> scheme.onSurface
                                    },
                                )
                                if (d.goalMet) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 5.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(if (bright) Color(0xFF2B1A00) else fc.success),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareSummary(ctx: Context, title: String, s: PeriodSummary, streak: Pair<Int, Int>) {
    val text = buildString {
        appendLine("Fastwise summary, $title")
        appendLine("Average fasting: ${Fmt.hm(s.avgFastingMs)} a day")
        appendLine("Longest fast: ${Fmt.hm(s.longestFastMs)}")
        appendLine("Meals: ${s.totalMeals} (${String.format(Locale.US, "%.1f", s.avgMeals)} a day)")
        appendLine("Goal met on ${s.goalDays} of ${s.goalDaysOutOf} days")
        s.avgWindowMs?.let { appendLine("Average eating window: ${Fmt.hm(it)}") }
        appendLine("Current goal streak: ${Fmt.plural(streak.first, "day")}")
    }.trim()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(send, "Share summary"))
}
