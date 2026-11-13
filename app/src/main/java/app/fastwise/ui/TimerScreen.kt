package app.fastwise.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fastwise.data.AppData
import app.fastwise.data.DayStats
import app.fastwise.data.DevicePrefs
import app.fastwise.data.Fast
import app.fastwise.data.FastRepository
import app.fastwise.data.Fmt
import app.fastwise.data.Goals
import app.fastwise.data.HOUR_MS
import app.fastwise.data.MINUTE_MS
import app.fastwise.data.Motivation
import app.fastwise.data.Stages
import app.fastwise.data.Stats
import java.time.Instant
import java.time.ZoneId

@Composable
fun TimerScreen(data: AppData) {
    val now by rememberNow(1000L)
    val ctx = LocalContext.current
    val is24 = DateFormat.is24HourFormat(ctx)
    val snack = LocalSnack.current
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val fc = LocalFastColors.current
    val active = data.activeFast
    val today = Fmt.date(now)
    val goalMinutes = active?.goalMinutes ?: data.settings.goalMinutes
    var showLog by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var editFast by remember { mutableStateOf<Fast?>(null) }
    var showPlan by remember { mutableStateOf(false) }
    val recentMeals = remember(data) { data.liveMeals.takeLast(12).map { it.time } }
    val minute = now / MINUTE_MS
    val streak = remember(data, minute) { Stats.streaks(data, now) }
    val week = remember(data, minute) { Motivation.week(data, today, data.settings.weekStartsMonday, now) }
    val reached = active != null && active.reachedGoal(now)
    var celebrateKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active?.id, reached) {
        val f = active
        if (f != null && reached && DevicePrefs.celebratedId(ctx) != f.id) {
            DevicePrefs.setCelebratedId(ctx, f.id)
            celebrateKey = f.id
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Gap(12.dp)
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                if (active != null) {
                    val elapsed = active.durationMs(now)
                    val next = Stages.next(elapsed)
                    Text(Stages.at(elapsed).title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (next != null) "${next.title} in ${Fmt.hm(next.hours * HOUR_MS - elapsed)}" else "You're in the longest stage",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                } else {
                    val last = data.lastMeal
                    Text("Not fasting", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (last != null) "Last meal ${Fmt.hm(now - last.time)} ago" else "Log a meal when you finish eating",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            AssistChip(
                onClick = { showGoal = true },
                label = { Text(Goals.name(goalMinutes)) },
                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = { Icon(Icons.Outlined.ExpandMore, contentDescription = "Change goal", modifier = Modifier.size(18.dp)) },
            )
        }

        Gap(16.dp)
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val dial = minOf(maxWidth, 420.dp)
            FastDial(
                start = active?.start,
                now = now,
                goalMs = goalMinutes * MINUTE_MS,
                mealTimes = recentMeals,
                is24 = is24,
                plan = data.settings.plan,
                modifier = Modifier.width(dial),
            ) {
                celebrateKey?.let { Celebration(it, Modifier.matchParentSize()) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (active != null) {
                        val elapsed = active.durationMs(now)
                        val goal = active.goalMs
                        Text(
                            Fmt.hms(elapsed),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = (dial.value * 0.13f).sp,
                                lineHeight = (dial.value * 0.15f).sp,
                            ),
                            color = scheme.onSurface,
                            maxLines = 1,
                        )
                        Gap(2.dp)
                        if (elapsed < goal) {
                            Text(
                                "${elapsed * 100 / goal}% of ${Fmt.hm(goal)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Goal reached, +${Fmt.hm(elapsed - goal)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = fc.success,
                            )
                        }
                    } else {
                        Text("Ready", style = MaterialTheme.typography.headlineMedium, color = scheme.onSurface)
                        Gap(4.dp)
                        Text(
                            "Your fast starts\nwhen you log a meal",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Gap(8.dp)
        Text(
            if (active != null) Motivation.line(active, now, streak.first)
            else Motivation.idleLine(streak.first, data.liveFasts.isNotEmpty()),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )

        if (active != null) {
            val goalAt = active.start + active.goalMs
            Gap(16.dp)
            Surface(
                onClick = { editFast = active },
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    InfoCell("Started", Fmt.dayTime(active.start, is24, today), "Tap to adjust", Modifier.weight(1f))
                    InfoCell(
                        "Goal",
                        Fmt.dayTime(goalAt, is24, today),
                        if (goalAt > now) "in ${Fmt.hm(goalAt - now)}" else "Reached",
                        Modifier.weight(1f),
                    )
                }
            }
        }

        if (reached) {
            Gap(12.dp)
            GoalReachedCard(streak.first)
        }

        Gap(24.dp)
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showLog = true
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = CircleShape,
        ) {
            Icon(Icons.Outlined.Restaurant, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("I just ate", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(
            onClick = {
                if (active != null) {
                    FastRepository.endFast()
                    snack.show("Fast ended at ${Fmt.hm(active.durationMs(System.currentTimeMillis()))}", "Undo") {
                        FastRepository.undo()
                    }
                } else {
                    FastRepository.startFast()
                    snack.show("Fast started", "Undo") { FastRepository.undo() }
                }
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(if (active != null) "End fast without a meal" else "Start fast without logging a meal")
        }

        Gap(24.dp)
        val plan = data.settings.plan
        if (plan != null) PlanTodayCard(plan, data, now, is24) { showPlan = true } else PlanPromptCard { showPlan = true }
        Gap(12.dp)
        WeekChain(week, today, data.settings.weeklyTargetDays, streak)

        Gap(28.dp)
        Text("Fasting stages", style = MaterialTheme.typography.titleLarge)
        Gap(14.dp)
        StageTimeline(active?.durationMs(now))

        Gap(28.dp)
        val todayStats = remember(data, now / MINUTE_MS) { Stats.days(data, today, today, now).first() }
        val z = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        Text("Today", style = MaterialTheme.typography.titleLarge)
        Gap(4.dp)
        Text(todaySummary(todayStats), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        Gap(14.dp)
        DayStrip(todayStats, nowFraction = (z.hour * 60 + z.minute) / 1440f)
        Gap(6.dp)
        HourAxis(is24)
        Gap(32.dp)
    }

    if (showLog) LogMealSheet(data) { showLog = false }
    if (showGoal) GoalSheet(goalMinutes) { showGoal = false }
    if (showPlan) PlanSheet(data.settings.plan) { showPlan = false }
    editFast?.let { f -> EditFastDialog(f) { editFast = null } }
}

private fun todaySummary(d: DayStats): String {
    val parts = mutableListOf(Fmt.plural(d.meals, "meal"), "${Fmt.hm(d.fastingMs)} fasting")
    d.eatingWindowMs?.let { parts += "eating window ${Fmt.hm(it)}" }
    return parts.joinToString(", ").replaceFirstChar { it.uppercase() }
}

@Composable
private fun InfoCell(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(2.dp)
        Text(value, style = MaterialTheme.typography.titleSmall)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Fasting stages as a vertical timeline; stages already reached are filled in. */
@Composable
private fun StageTimeline(elapsedMs: Long?) {
    val fc = LocalFastColors.current
    val scheme = MaterialTheme.colorScheme
    val current = elapsedMs?.let { Stages.at(it) }
    Column {
        Stages.all.forEachIndexed { i, stage ->
            val reached = elapsedMs != null && elapsedMs >= stage.hours * HOUR_MS
            val nextReached = elapsedMs != null && i < Stages.all.lastIndex &&
                elapsedMs >= Stages.all[i + 1].hours * HOUR_MS
            val isCurrent = stage == current
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(20.dp).fillMaxHeight()) {
                    if (i < Stages.all.lastIndex) {
                        Box(
                            Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(if (nextReached) fc.arcStart else fc.track),
                        )
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                            .size(if (isCurrent) 14.dp else 10.dp)
                            .clip(CircleShape)
                            .background(if (reached) fc.arcStart else fc.track),
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp, bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stage.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (reached) scheme.onSurface else scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${stage.hours}h",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (isCurrent || elapsedMs == null) {
                        Text(stage.blurb, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        Text(
            "Timings are typical and vary from person to person.",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 32.dp),
        )
    }
}
