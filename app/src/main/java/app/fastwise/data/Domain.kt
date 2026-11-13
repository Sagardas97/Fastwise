package app.fastwise.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Stages {
    data class Stage(val hours: Int, val title: String, val blurb: String)

    val all = listOf(
        Stage(0, "Digesting", "Your body is still processing your last meal."),
        Stage(4, "Settling", "Blood sugar and insulin drift back toward baseline."),
        Stage(12, "Fat burning", "Stored fat becomes a bigger share of your fuel."),
        Stage(18, "Ketosis", "Ketone levels typically start to climb."),
        Stage(24, "Deep fast", "Extended fasting. Stay hydrated and listen to your body."),
    )

    fun at(elapsedMs: Long): Stage = all.last { elapsedMs >= it.hours * HOUR_MS }
    fun next(elapsedMs: Long): Stage? = all.firstOrNull { elapsedMs < it.hours * HOUR_MS }
}

object MealLabels {
    val all = listOf("Breakfast", "Lunch", "Snack", "Dinner")

    fun suggest(time: Long): String {
        val hour = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).hour
        return when (hour) {
            in 4..10 -> "Breakfast"
            in 11..15 -> "Lunch"
            in 18..22 -> "Dinner"
            else -> "Snack"
        }
    }
}

object Goals {
    data class Preset(val name: String, val hours: Int, val note: String)

    val presets = listOf(
        Preset("13:11", 13, "A gentle start"),
        Preset("14:10", 14, "Easy daily rhythm"),
        Preset("16:8", 16, "The classic"),
        Preset("18:6", 18, "More time in fat burning"),
        Preset("20:4", 20, "Short eating window"),
        Preset("OMAD", 23, "One meal a day"),
        Preset("36 hours", 36, "Extended fast"),
    )

    fun name(minutes: Int): String =
        presets.firstOrNull { it.hours * 60 == minutes }?.name ?: Fmt.hm(minutes * MINUTE_MS)
}

object Fmt {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun hms(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        return String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    fun hm(ms: Long): String {
        val m = (ms / MINUTE_MS).coerceAtLeast(0L)
        val h = m / 60
        val r = m % 60
        return when {
            h == 0L -> "${r}m"
            r == 0L -> "${h}h"
            else -> "${h}h ${r}m"
        }
    }

    fun hours(ms: Long): String = String.format(Locale.US, "%.2f", ms / HOUR_MS.toDouble())

    fun date(t: Long): LocalDate = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()

    fun clock(t: Long, is24: Boolean): String =
        DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault())
            .format(Instant.ofEpochMilli(t).atZone(zone))

    fun dayName(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    }

    fun dayTime(t: Long, is24: Boolean, today: LocalDate): String = "${dayName(date(t), today)}, ${clock(t, is24)}"

    fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

    fun monthTitle(ym: YearMonth): String = ym.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

    fun plural(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"
}
