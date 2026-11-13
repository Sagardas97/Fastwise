package app.fastwise.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.fastwise.data.DAY_MS
import app.fastwise.data.DayStats
import app.fastwise.data.MealPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Gap(height: Dp) {
    Spacer(Modifier.height(height))
}

/** Current time, ticking on the period boundary while the screen is visible. */
@Composable
fun rememberNow(periodMs: Long): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(System.currentTimeMillis(), lifecycle, periodMs) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val t = System.currentTimeMillis()
                value = t
                delay(periodMs - t % periodMs)
            }
        }
    }
}

class SnackController(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = message,
                actionLabel = action,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

val LocalSnack = staticCompositionLocalOf<SnackController> { error("SnackController not provided") }

private fun minuteOfDay(t: Long, zone: ZoneId): Float {
    val z = Instant.ofEpochMilli(t).atZone(zone)
    return z.hour * 60f + z.minute + z.second / 60f
}

/** Compose arc angle for a time of day: noon at the top, midnight at the bottom, clockwise. */
private fun dialAngle(minute: Float): Float = (minute - 720f) / 1440f * 360f - 90f

/**
 * The signature element: a 24-hour dial. The fast is drawn where it really sits in your day,
 * from the meal that started it to now, with the night half of the clock shaded and the goal marked.
 */
@Composable
fun FastDial(
    start: Long?,
    now: Long,
    goalMs: Long,
    mealTimes: List<Long>,
    is24: Boolean,
    plan: MealPlan? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val fc = LocalFastColors.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val labelColor = scheme.onSurfaceVariant
    val bg = scheme.background
    val intro = remember(start) { Animatable(if (start == null) 1f else 0f) }
    LaunchedEffect(start) { intro.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing)) }
    val zone = ZoneId.systemDefault()
    val labels = if (is24) {
        listOf("12" to 720f, "18" to 1080f, "00" to 0f, "06" to 360f)
    } else {
        listOf("12p" to 720f, "6p" to 1080f, "12a" to 0f, "6a" to 360f)
    }

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.05f
            val radius = size.minDimension / 2f - stroke * 2.9f
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)
            fun at(deg: Float, r: Float): Offset {
                val rad = Math.toRadians(deg.toDouble())
                return Offset(center.x + r * cos(rad).toFloat(), center.y + r * sin(rad).toFloat())
            }

            drawCircle(fc.track, radius, center, style = Stroke(stroke))

            // Night half of the clock, 18:00 through midnight to 06:00.
            val nightR = radius - stroke * 2.1f
            drawArc(
                color = fc.night,
                startAngle = dialAngle(1080f),
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(center.x - nightR, center.y - nightR),
                size = Size(nightR * 2f, nightR * 2f),
                style = Stroke(stroke * 0.3f, cap = StrokeCap.Round),
            )

            for (h in 0 until 24) {
                val major = h % 6 == 0
                val deg = dialAngle(h * 60f)
                val r0 = radius - stroke * 0.95f
                val r1 = r0 - stroke * (if (major) 0.75f else 0.35f)
                drawLine(
                    color = if (major) labelColor.copy(alpha = 0.7f) else fc.track,
                    start = at(deg, r0),
                    end = at(deg, r1),
                    strokeWidth = stroke * (if (major) 0.14f else 0.1f),
                    cap = StrokeCap.Round,
                )
            }

            for ((text, minute) in labels) {
                val layout = measurer.measure(AnnotatedString(text), style = labelStyle)
                val p = at(dialAngle(minute), radius + stroke * 2.2f)
                drawText(
                    layout,
                    color = labelColor,
                    topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f),
                )
            }

            if (plan != null) {
                // Planned eating window, just outside the track; logged meals land on it as dots.
                val r = radius + stroke * 1.15f
                drawArc(
                    color = fc.meal.copy(alpha = 0.3f),
                    startAngle = dialAngle(plan.windowStart.toFloat()),
                    sweepAngle = plan.windowMinutes / 1440f * 360f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(stroke * 0.32f, cap = StrokeCap.Round),
                )
            }

            for (t in mealTimes) {
                if (now - t in 0 until DAY_MS) {
                    drawCircle(fc.meal, stroke * 0.26f, at(dialAngle(minuteOfDay(t, zone)), radius + stroke * 1.15f))
                }
            }

            if (start != null) {
                val elapsed = (now - start).coerceAtLeast(0L)
                val sweep = elapsed.toFloat() / DAY_MS * 360f * intro.value
                val a0 = dialAngle(minuteOfDay(start, zone))
                val firstLap = sweep.coerceAtMost(359.9f)
                rotate(degrees = a0, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to fc.arcStart.copy(alpha = 0.45f),
                            (firstLap / 360f).coerceAtLeast(0.001f) to fc.arcStart,
                            1f to fc.arcStart.copy(alpha = 0.45f),
                            center = center,
                        ),
                        startAngle = 0f,
                        sweepAngle = firstLap,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                if (sweep > 360f) {
                    drawArc(
                        color = fc.arcEnd,
                        startAngle = a0,
                        sweepAngle = (sweep - 360f).coerceAtMost(359.9f),
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(stroke * 0.45f, cap = StrokeCap.Round),
                    )
                }

                val reached = elapsed >= goalMs
                val goalPoint = at(dialAngle(minuteOfDay(start + goalMs, zone)), radius)
                drawCircle(bg, stroke * 0.55f, goalPoint)
                drawCircle(if (reached) fc.success else labelColor, stroke * 0.55f, goalPoint, style = Stroke(stroke * 0.16f))
                if (reached) drawCircle(fc.success, stroke * 0.3f, goalPoint)

                val head = at(a0 + sweep, radius)
                drawCircle(fc.arcStart.copy(alpha = 0.22f), stroke * 1.05f, head)
                drawCircle(fc.arcStart, stroke * 0.58f, head)
                drawCircle(bg, stroke * 0.2f, head)
            } else {
                drawCircle(labelColor, stroke * 0.3f, at(dialAngle(minuteOfDay(now, zone)), radius))
            }
        }
        content()
    }
}

/** One day as a 24h bar: fasting in amber, meals as dots. */
@Composable
fun DayStrip(day: DayStats, modifier: Modifier = Modifier, nowFraction: Float? = null) {
    val fc = LocalFastColors.current
    val bg = MaterialTheme.colorScheme.background
    val marker = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        val h = size.height
        val corner = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(fc.track, size = size, cornerRadius = corner)
        for ((a, b) in day.segments) {
            val x0 = a * size.width
            val w = ((b - a) * size.width).coerceAtLeast(h * 0.5f).coerceAtMost(size.width - x0)
            drawRoundRect(fc.arcStart, topLeft = Offset(x0, 0f), size = Size(w, h), cornerRadius = corner)
        }
        for (m in day.mealMarks) {
            val c = Offset(m * size.width, h / 2f)
            drawCircle(bg, h * 0.55f, c)
            drawCircle(fc.meal, h * 0.38f, c)
        }
        if (nowFraction != null) {
            val x = nowFraction * size.width
            drawLine(marker, Offset(x, -h * 0.3f), Offset(x, h * 1.3f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
fun HourAxis(is24: Boolean, modifier: Modifier = Modifier) {
    val labels = if (is24) listOf("00", "06", "12", "18", "24") else listOf("12a", "6a", "12p", "6p", "12a")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small progress ring used in lists. */
@Composable
fun MiniRing(fraction: Float, met: Boolean, modifier: Modifier = Modifier) {
    val fc = LocalFastColors.current
    Canvas(modifier.size(36.dp)) {
        val w = 3.5.dp.toPx()
        val inset = w / 2f
        val arcSize = Size(size.width - w, size.height - w)
        drawArc(fc.track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(w))
        drawArc(
            color = if (met) fc.success else fc.arcStart,
            startAngle = -90f,
            sweepAngle = 360f * fraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }
}
