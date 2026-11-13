package app.fastwise.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Encouragement tuned to where you are in the fast. Rewards consistency (streaks, goals hit),
 * never longer or harder fasts, and always leaves room to eat when you need to.
 */
object Motivation {
    fun line(f: Fast, now: Long, streak: Int): String {
        val elapsed = f.durationMs(now)
        val goal = f.goalMs
        val progress = elapsed.toDouble() / goal
        val left = goal - elapsed
        val pool = when {
            progress < 0.1 -> listOf(
                "Fresh start. The first hours are the easiest.",
                "Clock's running. Water, tea and black coffee are all fine.",
                "Good start. Your next meal will taste better for it.",
            )
            progress < 0.5 -> listOf(
                "Steady. Most of this stretch passes while you're busy or asleep.",
                "You're building the habit one hour at a time.",
                "Cravings come in waves and usually pass in about 20 minutes.",
            )
            progress < 0.75 -> listOf(
                "Past halfway. The hardest part is behind you.",
                "More than half done. Keep it easy and keep sipping water.",
                "Halfway and holding. Stay with it.",
            )
            left > HOUR_MS -> listOf(
                "Final stretch, ${Fmt.hm(left)} to go.",
                "Three quarters done. Your goal is in sight.",
                "Almost there. Plan something good for your first meal.",
            )
            left > 0 -> listOf(
                "Under an hour left. You've already done the hard part.",
                "${Fmt.hm(left)} to go. Finish strong.",
            )
            else -> listOf(
                if (streak > 1) "Goal reached. That's ${Fmt.plural(streak, "day")} in a row." else "Goal reached. Nice work.",
                "Done. Eat when you're ready, there's no prize for staying hungry.",
            )
        }
        // Stable per fast, changing every 3 hours, so the line doesn't flicker every second.
        val index = ((f.id.hashCode() and 0x7fffffff) + (elapsed / (3 * HOUR_MS)).toInt()) % pool.size
        return pool[index]
    }

    fun idleLine(streak: Int, hasHistory: Boolean): String = when {
        streak > 0 -> "Keep your ${Fmt.plural(streak, "day")} streak going. Log your next meal to start the clock."
        hasHistory -> "Ready when you are. Your fast starts when you log your next meal."
        else -> "Log your next meal and your first fast starts right away."
    }

    /** Time left if you're close enough to the goal that a gentle reminder helps; otherwise null. */
    fun nearGoal(f: Fast, at: Long): Long? {
        val elapsed = f.durationMs(at)
        val left = f.goalMs - elapsed
        val progress = elapsed.toDouble() / f.goalMs
        return if (left in 1L..(3 * HOUR_MS) && progress >= 0.6) left else null
    }

    data class Badge(val title: String, val detail: String, val current: Int, val target: Int) {
        val unlocked: Boolean get() = current >= target
        val progress: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
    }

    fun badges(d: AppData, now: Long): List<Badge> {
        val goalFasts = d.liveFasts.count { it.reachedGoal(now) }
        val best = Stats.streaks(d, now).second
        val meals = d.liveMeals.size
        return listOf(
            Badge("First goal", "Finish one fast at your goal", goalFasts, 1),
            Badge("Habit forming", "Hit your goal 3 days in a row", best, 3),
            Badge("One week strong", "A 7-day goal streak", best, 7),
            Badge("Two weeks", "A 14-day goal streak", best, 14),
            Badge("A month of it", "A 30-day goal streak", best, 30),
            Badge("Ten goals", "Reach your goal 10 times", goalFasts, 10),
            Badge("Fifty goals", "Reach your goal 50 times", goalFasts, 50),
            Badge("Hundred goals", "Reach your goal 100 times", goalFasts, 100),
            Badge("Steady logger", "Log 100 meals", meals, 100),
        )
    }

    /** Goal status for each day of the current week: true met, false not met, null still ahead. */
    fun week(d: AppData, today: LocalDate, monday: Boolean, now: Long): List<Pair<LocalDate, Boolean?>> {
        val start = today.with(TemporalAdjusters.previousOrSame(if (monday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY))
        return Stats.days(d, start, start.plusDays(6), now).map { day ->
            day.date to (if (day.date.isAfter(today)) null else day.goalMet)
        }
    }
}
