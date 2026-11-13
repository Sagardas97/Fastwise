@file:OptIn(ExperimentalMaterial3Api::class)

package app.fastwise.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fastwise.data.AppData
import app.fastwise.data.FastRepository
import app.fastwise.data.Fmt
import app.fastwise.data.MINUTE_MS
import app.fastwise.data.MealPlan
import app.fastwise.data.MealSlot
import app.fastwise.data.Plans
import kotlinx.coroutines.launch
import java.time.LocalTime

/** A plan as a 24h bar: fasting hours in amber, the eating window plain, planned meals as dots. */
@Composable
fun PlanStrip(plan: MealPlan, background: Color, modifier: Modifier = Modifier, nowMinute: Int? = null) {
    val fc = LocalFastColors.current
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(12.dp)) {
        val h = size.height
        val corner = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(fc.arcStart.copy(alpha = 0.55f), size = size, cornerRadius = corner)
        val x0 = plan.windowStart / 1440f * size.width
        val w = plan.windowMinutes / 1440f * size.width
        drawRoundRect(fc.track, topLeft = Offset(x0, 0f), size = Size(w, h), cornerRadius = corner)
        plan.slots.forEach { s ->
            val c = Offset(s.minute / 1440f * size.width, h / 2f)
            drawCircle(background, h * 0.62f, c)
            drawCircle(fc.meal, h * 0.42f, c)
        }
        if (nowMinute != null) {
            val x = nowMinute / 1440f * size.width
            drawLine(marker, Offset(x, -h * 0.3f), Offset(x, h * 1.3f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

/** Timer-screen card: today's planned meals, which are done, and the eating window status. */
@Composable
fun PlanTodayCard(plan: MealPlan, data: AppData, now: Long, is24: Boolean, onEdit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val today = Fmt.date(now)
    val minute = Plans.minuteOf(now)
    val mealsToday = data.liveMeals.count { Fmt.date(it.time) == today }
    val next = plan.slots.getOrNull(mealsToday)
    val windowStatus = when {
        minute < plan.windowStart ->
            "Eating window opens at ${Plans.clock(plan.windowStart, is24)}, in ${Fmt.hm((plan.windowStart - minute) * MINUTE_MS)}"
        minute < plan.windowEnd ->
            "Eating window open until ${Plans.clock(plan.windowEnd, is24)}, ${Fmt.hm((plan.windowEnd - minute) * MINUTE_MS)} left"
        else -> "Eating window closed for today"
    }
    val nextLine = when {
        next == null -> "All planned meals done today"
        next.minute > minute -> "Next: ${next.label} at ${Plans.clock(next.minute, is24)}"
        minute < plan.windowEnd -> "${next.label} was planned for ${Plans.clock(next.minute, is24)}"
        else -> null
    }
    Surface(onClick = onEdit, shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today's plan", style = MaterialTheme.typography.titleMedium)
                    Text(plan.name, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Text("Change", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
            Gap(14.dp)
            PlanStrip(plan, scheme.surfaceContainerLow, nowMinute = minute)
            Gap(12.dp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.slots.forEachIndexed { i, s -> SlotPill(s, done = i < mealsToday, isNext = i == mealsToday, is24 = is24) }
            }
            Gap(10.dp)
            if (nextLine != null) Text(nextLine, style = MaterialTheme.typography.bodyMedium)
            Text(windowStatus, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SlotPill(slot: MealSlot, done: Boolean, isNext: Boolean, is24: Boolean) {
    val fc = LocalFastColors.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when {
            done -> fc.meal.copy(alpha = 0.16f)
            isNext -> fc.arcStart.copy(alpha = 0.16f)
            else -> scheme.surfaceContainer
        },
        border = if (isNext) BorderStroke(1.dp, fc.arcStart) else null,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (done) {
                Icon(Icons.Filled.Check, contentDescription = "Eaten", tint = fc.meal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text("${slot.label} ${Plans.clock(slot.minute, is24)}", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Shown when no plan is set. */
@Composable
fun PlanPromptCard(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = LocalFastColors.current.meal)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Pick a meal plan", style = MaterialTheme.typography.titleMedium)
                Text(
                    "One meal a day, two meals, three meals and more. Sets your goal and eating window.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}

/** Choose a template, shift its eating window to fit your day, and apply it. */
@Composable
fun PlanSheet(current: MealPlan?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val is24 = DateFormat.is24HourFormat(ctx)
    val snack = LocalSnack.current
    val fc = LocalFastColors.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(current ?: Plans.templates.first { it.id == "two_16_8" }) }
    var pickStart by remember { mutableStateOf(false) }

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = scheme.surfaceContainerLow) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Meal plan", style = MaterialTheme.typography.headlineSmall)
            Gap(4.dp)
            Text(
                "Pick a daily rhythm. It sets your fasting goal, marks your eating window on the dial, " +
                    "and can remind you at meal times.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Gap(14.dp)
            Plans.templates.forEach { t ->
                val isSelected = selected.id == t.id
                val shown = if (isSelected) selected else t
                val cardColor = if (isSelected) fc.arcStart.copy(alpha = 0.14f) else scheme.surfaceContainer
                Surface(
                    onClick = { selected = if (current?.id == t.id) current else t },
                    shape = MaterialTheme.shapes.medium,
                    color = cardColor,
                    border = if (isSelected) BorderStroke(1.5.dp, fc.arcStart) else null,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${t.fastMinutes / 60}h fast", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                        }
                        Text(Plans.describe(shown, is24), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        Gap(10.dp)
                        PlanStrip(shown, if (isSelected) scheme.surfaceContainerLow else cardColor)
                    }
                }
            }

            Gap(16.dp)
            Text("Fit it to your day", style = MaterialTheme.typography.titleMedium)
            Gap(6.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Eating window starts at", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { pickStart = true }) { Text(Plans.clock(selected.windowStart, is24)) }
            }
            Text(
                selected.slots.joinToString(", ") { "${it.label} ${Plans.clock(it.minute, is24)}" } +
                    ". Window closes at ${Plans.clock(selected.windowEnd, is24)}.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )

            Gap(18.dp)
            Button(
                onClick = {
                    val chosen = selected
                    FastRepository.applyPlan(chosen)
                    snack.show("${chosen.name} plan set. Your goal is now ${chosen.fastMinutes / 60} hours.")
                    close()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = CircleShape,
            ) { Text("Use this plan", style = MaterialTheme.typography.titleMedium) }
            if (current != null) {
                TextButton(
                    onClick = {
                        FastRepository.applyPlan(null)
                        snack.show("Meal plan removed")
                        close()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Remove plan") }
            }
            Gap(8.dp)
            Text(
                "Long fasts and one meal a day aren't right for everyone. If you're pregnant, have diabetes, " +
                    "or have a history of disordered eating, check with a doctor first.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }

    if (pickStart) {
        TimePickDialog(
            initial = LocalTime.of(selected.windowStart / 60, selected.windowStart % 60),
            is24 = is24,
            onDismiss = { pickStart = false },
        ) { t ->
            selected = selected.shiftedTo(t.hour * 60 + t.minute)
            pickStart = false
        }
    }
}
