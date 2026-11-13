package app.fastwise.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val MINUTE_MS = 60_000L
const val HOUR_MS = 3_600_000L
const val DAY_MS = 86_400_000L

/** A meal you finished at [time]. Records are never removed, only marked [deleted], so sync can merge safely. */
data class Meal(
    val id: String = UUID.randomUUID().toString(),
    val time: Long,
    val label: String,
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
)

/** A fast from [start] to [end]. A null [end] means the fast is still running. */
data class Fast(
    val id: String = UUID.randomUUID().toString(),
    val start: Long,
    val end: Long? = null,
    val goalMinutes: Int,
    val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
) {
    val isActive: Boolean get() = end == null && !deleted
    val goalMs: Long get() = goalMinutes.coerceAtLeast(1) * MINUTE_MS
    fun durationMs(now: Long): Long = ((end ?: now) - start).coerceAtLeast(0L)
    fun reachedGoal(now: Long): Boolean = durationMs(now) >= goalMs
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val goalMinutes: Int = 16 * 60,
    val notificationOn: Boolean = true,
    val goalAlert: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = false,
    val weekStartsMonday: Boolean = true,
    /** Halfway and one-hour-to-go encouragement notifications. */
    val nudges: Boolean = true,
    /** How many days a week you aim to hit your goal. */
    val weeklyTargetDays: Int = 5,
    /** The chosen meal plan template (already shifted to your times), or null for none. */
    val plan: MealPlan? = null,
    val mealReminders: Boolean = true,
)

data class AppData(
    val meals: List<Meal> = emptyList(),
    val fasts: List<Fast> = emptyList(),
    val settings: UserSettings = UserSettings(),
) {
    val liveMeals: List<Meal> by lazy { meals.filter { !it.deleted }.sortedBy { it.time } }
    val liveFasts: List<Fast> by lazy { fasts.filter { !it.deleted }.sortedBy { it.start } }
    val activeFast: Fast? by lazy { liveFasts.lastOrNull { it.end == null } }
    val lastMeal: Meal? get() = liveMeals.lastOrNull()
}

/** JSON format shared by the data file, backups and the sync file. */
object Codec {
    private const val FORMAT = "fastwise"
    private const val VERSION = 1

    fun encode(d: AppData, deviceId: String): String = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("deviceId", deviceId)
        put("meals", JSONArray().apply { d.meals.forEach { put(mealJson(it)) } })
        put("fasts", JSONArray().apply { d.fasts.forEach { put(fastJson(it)) } })
        put("settings", settingsJson(d.settings))
    }.toString()

    private fun mealJson(m: Meal) = JSONObject().apply {
        put("id", m.id)
        put("time", m.time)
        put("label", m.label)
        put("note", m.note)
        put("updatedAt", m.updatedAt)
        put("deleted", m.deleted)
    }

    private fun fastJson(f: Fast) = JSONObject().apply {
        put("id", f.id)
        put("start", f.start)
        put("end", f.end ?: JSONObject.NULL)
        put("goalMinutes", f.goalMinutes)
        put("updatedAt", f.updatedAt)
        put("deleted", f.deleted)
    }

    private fun settingsJson(s: UserSettings) = JSONObject().apply {
        put("goalMinutes", s.goalMinutes)
        put("notificationOn", s.notificationOn)
        put("goalAlert", s.goalAlert)
        put("themeMode", s.themeMode.name)
        put("pureBlack", s.pureBlack)
        put("dynamicColor", s.dynamicColor)
        put("weekStartsMonday", s.weekStartsMonday)
        put("nudges", s.nudges)
        put("weeklyTargetDays", s.weeklyTargetDays)
        put("plan", s.plan?.let { planJson(it) } ?: JSONObject.NULL)
        put("mealReminders", s.mealReminders)
    }

    private fun planJson(p: MealPlan) = JSONObject().apply {
        put("id", p.id)
        put("name", p.name)
        put("windowStart", p.windowStart)
        put("windowMinutes", p.windowMinutes)
        put("slots", JSONArray().apply {
            p.slots.forEach { slot -> put(JSONObject().apply { put("label", slot.label); put("minute", slot.minute) }) }
        })
    }

    private fun planFrom(o: JSONObject): MealPlan? = runCatching {
        MealPlan(
            id = o.getString("id"),
            name = o.getString("name"),
            windowStart = o.getInt("windowStart"),
            windowMinutes = o.getInt("windowMinutes"),
            slots = o.optJSONArray("slots").objects().map { MealSlot(it.getString("label"), it.getInt("minute")) },
        )
    }.getOrNull()

    fun decode(text: String): AppData {
        val o = JSONObject(text)
        require(o.optString("format") == FORMAT) { "Not a Fastwise file" }
        val meals = o.optJSONArray("meals").objects().map { m ->
            Meal(
                id = m.getString("id"),
                time = m.getLong("time"),
                label = m.optString("label", "Meal"),
                note = m.optString("note", ""),
                updatedAt = m.optLong("updatedAt", 0L),
                deleted = m.optBoolean("deleted", false),
            )
        }
        val fasts = o.optJSONArray("fasts").objects().map { f ->
            Fast(
                id = f.getString("id"),
                start = f.getLong("start"),
                end = if (f.isNull("end")) null else f.getLong("end"),
                goalMinutes = f.optInt("goalMinutes", 16 * 60),
                updatedAt = f.optLong("updatedAt", 0L),
                deleted = f.optBoolean("deleted", false),
            )
        }
        val s = o.optJSONObject("settings")
        val def = UserSettings()
        val settings = if (s == null) def else UserSettings(
            goalMinutes = s.optInt("goalMinutes", def.goalMinutes),
            notificationOn = s.optBoolean("notificationOn", def.notificationOn),
            goalAlert = s.optBoolean("goalAlert", def.goalAlert),
            themeMode = runCatching { ThemeMode.valueOf(s.optString("themeMode", "SYSTEM")) }
                .getOrDefault(ThemeMode.SYSTEM),
            pureBlack = s.optBoolean("pureBlack", def.pureBlack),
            dynamicColor = s.optBoolean("dynamicColor", def.dynamicColor),
            weekStartsMonday = s.optBoolean("weekStartsMonday", def.weekStartsMonday),
            nudges = s.optBoolean("nudges", def.nudges),
            weeklyTargetDays = s.optInt("weeklyTargetDays", def.weeklyTargetDays).coerceIn(1, 7),
            plan = s.optJSONObject("plan")?.let { planFrom(it) },
            mealReminders = s.optBoolean("mealReminders", def.mealReminders),
        )
        return AppData(meals, fasts, settings)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else List(length()) { getJSONObject(it) }
}

/** Record-level merge: newest [updatedAt] wins, deletions travel as tombstones. Settings stay per device. */
object Merge {
    data class Result(val data: AppData, val added: Int, val updated: Int) {
        val changes: Int get() = added + updated
    }

    fun merge(local: AppData, remote: AppData): Result {
        var added = 0
        var updated = 0
        val meals = LinkedHashMap<String, Meal>()
        local.meals.forEach { meals[it.id] = it }
        remote.meals.forEach { r ->
            val l = meals[r.id]
            if (l == null) {
                meals[r.id] = r
                if (!r.deleted) added++
            } else if (r.updatedAt > l.updatedAt) {
                meals[r.id] = r
                updated++
            }
        }
        val fasts = LinkedHashMap<String, Fast>()
        local.fasts.forEach { fasts[it.id] = it }
        remote.fasts.forEach { r ->
            val l = fasts[r.id]
            if (l == null) {
                fasts[r.id] = r
                if (!r.deleted) added++
            } else if (r.updatedAt > l.updatedAt) {
                fasts[r.id] = r
                updated++
            }
        }
        return Result(local.copy(meals = meals.values.toList(), fasts = fasts.values.toList()), added, updated)
    }
}
