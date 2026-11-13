package app.fastwise.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** A planned meal at [minute] of the day (0..1439). */
data class MealSlot(val label: String, val minute: Int)

/** A daily rhythm: an eating window with planned meals inside it. The rest of the day is the fast. */
data class MealPlan(
    val id: String,
    val name: String,
    val windowStart: Int,
    val windowMinutes: Int,
    val slots: List<MealSlot>,
) {
    val windowEnd: Int get() = windowStart + windowMinutes
    val fastMinutes: Int get() = 24 * 60 - windowMinutes

    /** Moves the whole window (and every meal in it) to start at [start]. */
    fun shiftedTo(start: Int): MealPlan {
        val s = start.coerceIn(0, 24 * 60 - windowMinutes)
        val delta = s - windowStart
        return copy(windowStart = s, slots = slots.map { it.copy(minute = it.minute + delta) })
    }
}

object Plans {
    private fun at(h: Int, m: Int = 0) = h * 60 + m

    val templates = listOf(
        MealPlan("omad", "One meal a day", at(18), 60, listOf(MealSlot("Dinner", at(18)))),
        MealPlan("two_20_4", "Two meals, 20:4", at(15), 240, listOf(MealSlot("Lunch", at(15)), MealSlot("Dinner", at(18, 30)))),
        MealPlan("two_18_6", "Two meals, 18:6", at(13), 360, listOf(MealSlot("Lunch", at(13)), MealSlot("Dinner", at(18, 30)))),
        MealPlan("two_16_8", "Two meals, 16:8", at(12), 480, listOf(MealSlot("Lunch", at(12)), MealSlot("Dinner", at(19, 30)))),
        MealPlan("early_16_8", "Two meals, early window", at(8), 480, listOf(MealSlot("Breakfast", at(8)), MealSlot("Lunch", at(15, 30)))),
        MealPlan(
            "two_snack_16_8", "Two meals and a snack, 16:8", at(12), 480,
            listOf(MealSlot("Lunch", at(12)), MealSlot("Snack", at(16)), MealSlot("Dinner", at(19, 30))),
        ),
        MealPlan(
            "three_14_10", "Three meals, 14:10", at(9), 600,
            listOf(MealSlot("Breakfast", at(9)), MealSlot("Lunch", at(13, 30)), MealSlot("Dinner", at(18, 30))),
        ),
    )

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun minuteOf(t: Long): Int {
        val z = Instant.ofEpochMilli(t).atZone(zone)
        return z.hour * 60 + z.minute
    }

    fun timeOn(day: LocalDate, minute: Int): Long =
        day.atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()

    fun clock(minute: Int, is24: Boolean): String = Fmt.clock(timeOn(LocalDate.now(zone), minute), is24)

    fun describe(p: MealPlan, is24: Boolean): String =
        "${Fmt.plural(p.slots.size, "meal")}, eating window ${clock(p.windowStart, is24)} to ${clock(p.windowEnd, is24)}"

    /** The planned meal closest to [time] (within 2.5 hours), else a time-of-day guess. */
    fun suggestLabel(plan: MealPlan?, time: Long): String {
        val m = minuteOf(time)
        val slot = plan?.slots?.minByOrNull { abs(it.minute - m) }
        return if (slot != null && abs(slot.minute - m) <= 150) slot.label else MealLabels.suggest(time)
    }

    fun mealsBefore(d: AppData, time: Long): Int {
        val day = Fmt.date(time)
        return d.liveMeals.count { it.time < time && Fmt.date(it.time) == day }
    }

    /**
     * True when the plan still has meals coming today, so eating now should end the fast
     * without starting a new one (the new fast starts after the last meal).
     */
    fun expectMoreMeals(plan: MealPlan?, time: Long, mealsBefore: Int): Boolean {
        if (plan == null) return false
        val m = minuteOf(time)
        if (mealsBefore + 1 >= plan.slots.size) return false
        if (m >= plan.windowEnd - 30) return false
        if (m < plan.windowStart - 120) return false
        return plan.slots.any { it.minute > m + 45 }
    }

    /** A day matched the plan if it had the planned number of meals, all inside the window (30 min grace). */
    fun matches(day: DayStats, plan: MealPlan): Boolean {
        val first = day.firstMeal ?: return false
        val last = day.lastMeal ?: return false
        return day.meals == plan.slots.size &&
            minuteOf(first) >= plan.windowStart - 30 &&
            minuteOf(last) <= plan.windowEnd + 30
    }

    /** A reminder moment: a planned meal, or (slot = null) 30 minutes before the window closes. */
    data class PlanEvent(val time: Long, val slot: MealSlot?)

    fun events(plan: MealPlan, day: LocalDate): List<PlanEvent> =
        plan.slots.map { PlanEvent(timeOn(day, it.minute), it) } + PlanEvent(timeOn(day, plan.windowEnd - 30), null)

    fun nextEvent(plan: MealPlan, now: Long): PlanEvent? {
        val today = Fmt.date(now)
        return (events(plan, today) + events(plan, today.plusDays(1)))
            .filter { it.time > now + 30_000L }
            .minByOrNull { it.time }
    }

    fun eventsAround(plan: MealPlan, now: Long): List<PlanEvent> {
        val today = Fmt.date(now)
        return events(plan, today.minusDays(1)) + events(plan, today)
    }
}
