package app.fastwise.data

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporter {
    private fun zdt(t: Long): ZonedDateTime =
        Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS)

    private fun ts(t: Long): String = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zdt(t))
    private fun hhmm(t: Long): String = DateTimeFormatter.ofPattern("HH:mm").format(zdt(t))

    /** A zip with meals.csv, fasts.csv and daily_summary.csv. */
    fun writeCsvZip(ctx: Context, uri: Uri, d: AppData, now: Long) {
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw IOException("Couldn't open the file")
        ZipOutputStream(out).use { zip ->
            zip.add("meals.csv", mealsCsv(d))
            zip.add("fasts.csv", fastsCsv(d, now))
            zip.add("daily_summary.csv", dailyCsv(d, now))
        }
    }

    fun writeBackup(ctx: Context, uri: Uri, d: AppData) {
        val out = ctx.contentResolver.openOutputStream(uri) ?: throw IOException("Couldn't open the file")
        out.use { it.write(Codec.encode(d, FastRepository.deviceId).toByteArray(Charsets.UTF_8)) }
    }

    fun readBackup(ctx: Context, uri: Uri): AppData {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Couldn't open the file")
        return Codec.decode(text)
    }

    private fun ZipOutputStream.add(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun cell(v: Any?): String {
        val s = v?.toString() ?: ""
        val needsQuotes = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private fun row(vararg values: Any?): String = values.joinToString(",") { cell(it) }

    private fun mealsCsv(d: AppData) = buildString {
        appendLine(row("meal_id", "date", "time", "timestamp", "meal", "note"))
        d.liveMeals.forEach { m ->
            appendLine(row(m.id, Fmt.date(m.time), hhmm(m.time), ts(m.time), m.label, m.note))
        }
    }

    private fun fastsCsv(d: AppData, now: Long) = buildString {
        appendLine(row("fast_id", "start", "end", "duration_hours", "goal_hours", "goal_met", "status"))
        d.liveFasts.forEach { f ->
            appendLine(
                row(
                    f.id, ts(f.start), f.end?.let { ts(it) } ?: "",
                    Fmt.hours(f.durationMs(now)), Fmt.hours(f.goalMs),
                    if (f.reachedGoal(now)) "yes" else "no",
                    if (f.end == null) "running" else "completed",
                )
            )
        }
    }

    private fun dailyCsv(d: AppData, now: Long) = buildString {
        appendLine(
            row(
                "date", "meals", "fasting_hours", "longest_fast_hours", "fasts_completed",
                "goal_met", "first_meal", "last_meal", "eating_window_hours",
            )
        )
        val firstTimes = listOfNotNull(d.liveMeals.firstOrNull()?.time, d.liveFasts.firstOrNull()?.start)
        if (firstTimes.isEmpty()) return@buildString
        val from: LocalDate = Fmt.date(firstTimes.min())
        Stats.days(d, from, Fmt.date(now), now).forEach { s ->
            appendLine(
                row(
                    s.date, s.meals, Fmt.hours(s.fastingMs), Fmt.hours(s.longestFastMs), s.fastsCompleted,
                    if (s.goalMet) "yes" else "no",
                    s.firstMeal?.let { hhmm(it) } ?: "", s.lastMeal?.let { hhmm(it) } ?: "",
                    s.eatingWindowMs?.let { Fmt.hours(it) } ?: "",
                )
            )
        }
    }
}
