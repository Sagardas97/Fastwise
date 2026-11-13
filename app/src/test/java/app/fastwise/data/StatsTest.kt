package app.fastwise.data

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

class StatsTest {
    @Before
    fun useUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private fun at(date: String, hour: Int, minute: Int = 0): Long =
        LocalDate.parse(date).atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun day(date: String): LocalDate = LocalDate.parse(date)
    private fun fast(start: Long, end: Long?) = Fast(start = start, end = end, goalMinutes = 16 * 60)
    private fun meal(time: Long) = Meal(time = time, label = "Meal")

    /** Lunch at 12:00 and dinner at 20:00 from Monday 2 Nov to Wednesday, fasting in between, still fasting now. */
    private val sixteenEight = AppData(
        meals = listOf("2026-11-02", "2026-11-03", "2026-11-04").flatMap { listOf(meal(at(it, 12)), meal(at(it, 20))) },
        fasts = listOf(
            fast(at("2026-11-02", 20), at("2026-11-03", 12)),
            fast(at("2026-11-03", 20), at("2026-11-04", 12)),
            fast(at("2026-11-04", 20), null),
        ),
    )
    private val thursday = day("2026-11-05")
    private val week = day("2026-11-02") to day("2026-11-08")

    private fun weekSummary(now: Long) =
        Stats.summarize(Stats.days(sixteenEight, week.first, week.second, now), thursday, Stats.firstDay(sixteenEight))

    @Test
    fun theSameFastLoggedOnTwoDevicesCountsOnce() {
        val d = AppData(
            fasts = listOf(
                fast(at("2026-11-02", 20), at("2026-11-03", 12)),
                fast(at("2026-11-02", 20, 5), at("2026-11-03", 12, 10)),
            ),
        )
        val tuesday = Stats.days(d, day("2026-11-03"), day("2026-11-03"), at("2026-11-10", 0)).single()
        assertEquals(12 * HOUR_MS + 10 * MINUTE_MS, tuesday.fastingMs)
        assertEquals(1, tuesday.segments.size)
    }

    @Test
    fun aDayNeverHasMoreThan24HoursOfFasting() {
        // A 36-hour fast added by hand on top of fasts that were already logged.
        val d = AppData(
            fasts = listOf(
                fast(at("2026-11-02", 20), at("2026-11-03", 12)),
                fast(at("2026-11-03", 20), at("2026-11-04", 12)),
                fast(at("2026-11-02", 18), at("2026-11-04", 6)),
            ),
        )
        val tuesday = Stats.days(d, day("2026-11-03"), day("2026-11-03"), at("2026-11-10", 0)).single()
        assertEquals(24 * HOUR_MS, tuesday.fastingMs)
    }

    @Test
    fun averagesLeaveOutTodayAndTheFirstDay() {
        // Thursday 09:00. Tuesday and Wednesday are the whole days: 16 hours fasting, 2 meals and an
        // 8-hour eating window each. Counting Monday (from noon) and Thursday (until 09:00) as full
        // days used to give an average of 11h 15m and 1.5 meals.
        val s = weekSummary(at("2026-11-05", 9))
        assertEquals(16 * HOUR_MS, s.avgFastingMs)
        assertEquals(2.0f, s.avgMeals, 0.001f)
        assertEquals(8 * HOUR_MS, s.avgWindowMs)
        assertEquals(4, s.trackedDays)
        assertEquals(6, s.totalMeals)
        assertEquals((4 + 16 + 16 + 9) * HOUR_MS, s.totalFastingMs)
    }

    @Test
    fun todayCountsTowardGoalDaysOnlyOnceItsGoalIsMet() {
        val morning = weekSummary(at("2026-11-05", 9))
        assertEquals(2, morning.goalDays) // Tuesday and Wednesday
        assertEquals(3, morning.goalDaysOutOf) // Monday to Wednesday; Thursday isn't decided yet
        assertEquals(3, morning.finishedDays)

        val noon = weekSummary(at("2026-11-05", 12)) // the fast since Wednesday 20:00 reaches 16 hours
        assertEquals(3, noon.goalDays)
        assertEquals(4, noon.goalDaysOutOf)
    }

    @Test
    fun aPeriodWithoutAWholeDayFallsBackToTheDaysItHas() {
        // First ever entry: dinner on Monday at 20:00, fasting since. Now it's Tuesday 09:00.
        val d = AppData(meals = listOf(meal(at("2026-11-02", 20))), fasts = listOf(fast(at("2026-11-02", 20), null)))
        val days = Stats.days(d, week.first, week.second, at("2026-11-03", 9))
        val s = Stats.summarize(days, day("2026-11-03"), Stats.firstDay(d))
        assertEquals((4 + 9) * HOUR_MS / 2, s.avgFastingMs)
    }

    @Test
    fun mergeSpansJoinsOverlapsAndKeepsGaps() {
        val spans = Stats.mergeSpans(listOf(10L to 20L, 15L to 18L, 30L to 40L, 19L to 25L, 50L to 45L))
        assertEquals(listOf(10L to 25L, 30L to 40L), spans)
    }
}
