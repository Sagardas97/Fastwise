package app.fastwise.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.fastwise.data.Fmt
import app.fastwise.data.Motivation
import java.time.LocalDate
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** "Don't break the chain": this week's days, ticked when a fast hit its goal. */
@Composable
fun WeekChain(week: List<Pair<LocalDate, Boolean?>>, today: LocalDate, target: Int, streak: Pair<Int, Int>) {
    val fc = LocalFastColors.current
    val scheme = MaterialTheme.colorScheme
    val met = week.count { it.second == true }
    val checkTint = if (fc.success.luminance() > 0.4f) Color(0xFF10301D) else Color.White
    Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("This week", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (met >= target) "Weekly target reached, $met of $target days" else "$met of $target target days so far",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (met >= target) fc.success else scheme.onSurfaceVariant,
                    )
                }
                if (streak.first > 0) {
                    Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = fc.arcStart, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${Fmt.plural(streak.first, "day")} streak", style = MaterialTheme.typography.labelLarge)
                }
            }
            Gap(14.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { (date, ok) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val ring = when {
                            ok == true -> fc.success
                            date == today -> fc.arcStart
                            else -> fc.track
                        }
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (ok == true) fc.success else Color.Transparent)
                                .border(2.dp, ring, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ok == true) {
                                Icon(Icons.Filled.Check, contentDescription = "Goal met", tint = checkTint, modifier = Modifier.size(18.dp))
                            }
                        }
                        Gap(4.dp)
                        Text(
                            date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (date == today) scheme.onSurface else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (streak.second >= 2 && streak.second > streak.first) {
                Gap(10.dp)
                Text(
                    "Your best streak is ${Fmt.plural(streak.second, "day")}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun GoalReachedCard(streak: Int) {
    val fc = LocalFastColors.current
    Surface(shape = MaterialTheme.shapes.large, color = fc.success.copy(alpha = 0.14f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = fc.success, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    if (streak > 1) "Goal reached, ${Fmt.plural(streak, "day")} in a row" else "Goal reached",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Eat whenever you're ready. Logging your meal starts the next fast.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Shown in the meal sheet when you're close to your goal. Encourages, never blocks. */
@Composable
fun NearGoalCard(leftMs: Long, goalMs: Long, streak: Int) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Only ${Fmt.hm(leftMs)} to your ${Fmt.hm(goalMs)} goal", style = MaterialTheme.typography.titleSmall)
            Gap(2.dp)
            Text(
                (if (streak > 0) "Finishing keeps your ${Fmt.plural(streak, "day")} streak going. " else "Finishing counts toward a new streak. ") +
                    "If you're truly hungry or feel unwell, eat. That always comes first.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private class Particle(val angle: Float, val speed: Float, val size: Float, val color: Int, val spin: Float)

/** One-time confetti burst from the dial when a fast reaches its goal. */
@Composable
fun Celebration(key: String, modifier: Modifier = Modifier) {
    val fc = LocalFastColors.current
    val colors = listOf(fc.arcStart, fc.arcEnd, fc.meal, fc.success)
    val progress = remember(key) { Animatable(0f) }
    val particles = remember(key) {
        val r = Random(key.hashCode())
        List(72) {
            Particle(
                angle = r.nextFloat() * 360f,
                speed = 0.35f + r.nextFloat() * 0.65f,
                size = 4f + r.nextFloat() * 5f,
                color = r.nextInt(colors.size),
                spin = r.nextFloat() * 720f - 360f,
            )
        }
    }
    LaunchedEffect(key) { progress.animateTo(1f, tween(durationMillis = 1900, easing = LinearOutSlowInEasing)) }
    Canvas(modifier) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val reach = size.minDimension * 0.6f
        val alpha = 1f - t
        particles.forEach { p ->
            val rad = Math.toRadians(p.angle.toDouble())
            val dist = reach * p.speed * t
            val x = center.x + cos(rad).toFloat() * dist
            val y = center.y + sin(rad).toFloat() * dist + t * t * size.minDimension * 0.2f
            val w = p.size.dp.toPx()
            rotate(degrees = p.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[p.color].copy(alpha = alpha),
                    topLeft = Offset(x - w / 2f, y - w / 4f),
                    size = Size(w, w / 2f),
                )
            }
        }
    }
}

@Composable
fun BadgeGrid(badges: List<Motivation.Badge>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        badges.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { b -> BadgeTile(b, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BadgeTile(b: Motivation.Badge, modifier: Modifier = Modifier) {
    val fc = LocalFastColors.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (b.unlocked) fc.arcStart.copy(alpha = 0.14f) else scheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(
                if (b.unlocked) Icons.Filled.EmojiEvents else Icons.Outlined.EmojiEvents,
                contentDescription = if (b.unlocked) "Unlocked" else "Locked",
                tint = if (b.unlocked) fc.arcStart else scheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
            Gap(8.dp)
            Text(
                b.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (b.unlocked) scheme.onSurface else scheme.onSurfaceVariant,
            )
            Text(b.detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, minLines = 2)
            if (!b.unlocked) {
                Gap(8.dp)
                LinearProgressIndicator(
                    progress = { b.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = fc.arcStart,
                    trackColor = fc.track,
                )
                Gap(4.dp)
                Text("${b.current} of ${b.target}", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}
