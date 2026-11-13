package app.fastwise.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayStats(
    val date: LocalDate,
    val meals: Int,
    /** Time spent fasting inside this calendar day. */
    val fastingMs: Long,
    /** Longest fast that ended (or is running) on this day. */
    val longestFastMs: Long,
    val fastsCompleted: Int,
    val goalMet: Boolean,
    val firstMeal: Long?,
    val lastMeal: Long?,
    /** Fasting periods as fractions (0..1) of the day, for the 24h strip. */
    val segments: List<Pair<Float, Float>>,
    val mealMarks: List<Float>,
    val hasData: Boolean,
) {
    val eatingWindowMs: Long?
        get() = if (meals >= 2 && firstMeal != null && lastMeal != null) lastMeal - firstMeal else null
}

data class PeriodSummary(
    val trackedDays: Int,
    /** Tracked days before today, so already over. */
    val finishedDays: Int,
    val totalMeals: Int,
    val totalFastingMs: Long,
    val avgFastingMs: Long,
    val longestFastMs: Long,
    val fastsCompleted: Int,
    val goalDays: Int,
    /** Days the goal could be judged on: every finished day, plus today once its goal is met. */
    val goalDaysOutOf: Int,
    val avgMeals: Float,
    val avgWindowMs: Long?,
)

object Stats {
    fun days(d: AppData, from: LocalDate, to: LocalDate, now: Long): List<DayStats> {
        val zone = ZoneId.systemDefault()
        val fasts = d.liveFasts
        val meals = d.liveMeals
        // Fasts can overlap: the same fast logged on two synced devices, or one added or edited by hand.
        // Joining them first counts each fasting hour once, so a day never shows more than 24 hours.
        val spans = mergeSpans(fasts.map { it.start to (it.end ?: now) })
        val out = ArrayList<DayStats>()
        var day = from
        while (!day.isAfter(to)) {
            val s = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val e = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val len = (e - s).toFloat()
            var fasting = 0L
            var longest = 0L
            var completed = 0
            var goalMet = false
            val segs = ArrayList<Pair<Float, Float>>()
            for (f in fasts) {
                val fe = f.end ?: now
                if (fe >= s && fe < e) {
                    val dur = fe - f.start
                    if (f.end != null) completed++
                    if (dur > longest) longest = dur
                    if (dur >= f.goalMs) goalMet = true
                }
            }
            for ((start, end) in spans) {
                val a = maxOf(start, s)
                val b = minOf(end, e)
                if (b > a) {
                    fasting += b - a
                    segs += ((a - s) / len) to ((b - s) / len)
                }
            }
            val dm = meals.filter { it.time >= s && it.time < e }
            out += DayStats(
                date = day,
                meals = dm.size,
                fastingMs = fasting,
                longestFastMs = longest,
                fastsCompleted = completed,
                goalMet = goalMet,
                firstMeal = dm.firstOrNull()?.time,
                lastMeal = dm.lastOrNull()?.time,
                segments = segs,
                mealMarks = dm.map { (it.time - s) / len },
                hasData = dm.isNotEmpty() || fasting > 0L,
            )
            day = day.plusDays(1)
        }
        return out
    }

    /**
     * Totals cover every day with data up to today. Averages use whole days only: today isn't over yet,
     * and the first day you used Fastwise ([firstDay]) started partway through, so counting either as a
     * full day pulled the averages down (on a Monday morning the week's average was just the hours since
     * midnight). A period without a whole day yet falls back to the days it has.
     */
    fun summarize(days: List<DayStats>, today: LocalDate, firstDay: LocalDate? = null): PeriodSummary {
        val tracked = days.filter { !it.date.isAfter(today) && it.hasData }
        val whole = tracked.filter { it.date.isBefore(today) && (firstDay == null || it.date.isAfter(firstDay)) }
        val basis = whole.ifEmpty { tracked }
        val n = basis.size
        val windows = basis.mapNotNull { it.eatingWindowMs }
        return PeriodSummary(
            trackedDays = tracked.size,
            finishedDays = tracked.count { it.date.isBefore(today) },
            totalMeals = tracked.sumOf { it.meals },
            totalFastingMs = tracked.sumOf { it.fastingMs },
            avgFastingMs = if (n == 0) 0L else basis.sumOf { it.fastingMs } / n,
            longestFastMs = tracked.maxOfOrNull { it.longestFastMs } ?: 0L,
            fastsCompleted = tracked.sumOf { it.fastsCompleted },
            goalDays = tracked.count { it.goalMet },
            goalDaysOutOf = tracked.count { it.date.isBefore(today) || it.goalMet },
            avgMeals = if (n == 0) 0f else basis.sumOf { it.meals } / n.toFloat(),
            avgWindowMs = if (windows.isEmpty()) null else windows.sum() / windows.size,
        )
    }

    /** The date of the earliest meal or fast, or null when there's no data. */
    fun firstDay(d: AppData): LocalDate? =
        listOfNotNull(d.liveMeals.firstOrNull()?.time, d.liveFasts.firstOrNull()?.start).minOrNull()?.let { Fmt.date(it) }

    /** Joins overlapping or touching time spans, so every moment is counted once. Empty spans are dropped. */
    internal fun mergeSpans(spans: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        for ((a, b) in spans.filter { it.second > it.first }.sortedBy { it.first }) {
            val last = out.lastOrNull()
            if (last != null && a <= last.second) {
                out[out.size - 1] = last.first to maxOf(last.second, b)
            } else {
                out += a to b
            }
        }
        return out
    }

    /** Current and best run of consecutive days on which a fast reached its goal. */
    fun streaks(d: AppData, now: Long): Pair<Int, Int> {
        val zone = ZoneId.systemDefault()
        val dates = d.liveFasts
            .filter { it.durationMs(now) >= it.goalMs }
            .map { Instant.ofEpochMilli(it.end ?: now).atZone(zone).toLocalDate() }
            .toSortedSet()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (date in dates) {
            run = if (prev != null && prev.plusDays(1) == date) run + 1 else 1
            if (run > best) best = run
            prev = date
        }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        var cursor = if (today in dates) today else today.minusDays(1)
        var current = 0
        while (cursor in dates) {
            current++
            cursor = cursor.minusDays(1)
        }
        return current to best
    }
}
