package app.fastwise.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import app.fastwise.service.Effects
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Single source of truth. Data lives in memory as a StateFlow and is saved to
 * files/fastwise.json (atomic writes) on a background thread after every change.
 */
object FastRepository {
    private const val TAG = "FastRepository"

    private lateinit var app: Context
    private lateinit var file: AtomicFile
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    var deviceId: String = "device"
        private set

    @Volatile
    private var undoBase: AppData? = null
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext
        deviceId = DevicePrefs.deviceId(app)
        file = AtomicFile(File(app.filesDir, "fastwise.json"))
        _data.value = load()
        initialized = true
    }

    fun runOnIo(block: () -> Unit) {
        io.execute(block)
    }

    private fun load(): AppData {
        val base = file.baseFile
        if (!base.exists()) return AppData()
        return try {
            Codec.decode(String(file.readFully(), Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't read the data file; keeping a copy aside", e)
            runCatching {
                base.copyTo(File(app.filesDir, "fastwise-unreadable-${System.currentTimeMillis()}.json"), overwrite = true)
            }
            AppData()
        }
    }

    private fun write(d: AppData) {
        val bytes = Codec.encode(d, deviceId).toByteArray(Charsets.UTF_8)
        var out: FileOutputStream? = null
        try {
            val stream: FileOutputStream = file.startWrite()
            out = stream
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: IOException) {
            Log.e(TAG, "Save failed", e)
            if (out != null) file.failWrite(out)
        }
    }

    private fun now() = System.currentTimeMillis()

    private fun commit(
        undoable: Boolean = true,
        fromSync: Boolean = false,
        transform: (AppData) -> AppData,
    ): AppData {
        val next = synchronized(lock) {
            val prev = _data.value
            val n = normalize(transform(prev))
            if (n == prev) return prev
            if (undoable) undoBase = prev
            _data.value = n
            io.execute { write(n) }
            n
        }
        // A change the user makes (in the app, widget, tile or notification) undoes Quit.
        if (!fromSync) DevicePrefs.setQuit(app, false)
        Effects.apply(app, next)
        if (!fromSync) SyncManager.scheduleAuto()
        return next
    }

    /** Never more than one running fast (can happen after merging two devices). */
    private fun normalize(d: AppData): AppData {
        val actives = d.fasts.filter { it.isActive }
        if (actives.size <= 1) return d
        val keep = actives.maxBy { it.start }
        val t = now()
        return d.copy(fasts = d.fasts.map { f ->
            if (f.isActive && f.id != keep.id) f.copy(end = maxOf(f.start, keep.start), updatedAt = t) else f
        })
    }

    // ---- Actions -------------------------------------------------------------------------

    /**
     * Log a meal finished at [time]. Eating always ends the running fast. A new fast starts unless
     * [startNewFast] is false; when it's null, the meal plan decides (more meals still planned today
     * means the timer waits for your last one). [touchFasts] = false only records the meal.
     */
    fun logMeal(
        time: Long = now(),
        label: String? = null,
        note: String = "",
        startNewFast: Boolean? = null,
        touchFasts: Boolean = true,
    ): Meal {
        val t = now()
        val snapshot = _data.value
        val plan = snapshot.settings.plan
        val meal = Meal(time = time, label = label ?: Plans.suggestLabel(plan, time), note = note.trim(), updatedAt = t)
        val start = startNewFast ?: !Plans.expectMoreMeals(plan, time, Plans.mealsBefore(snapshot, time))
        commit { d ->
            var fasts = d.fasts
            if (touchFasts) {
                val active = d.activeFast
                if (active != null && time >= active.start) {
                    fasts = fasts.map { f ->
                        when {
                            f.id != active.id -> f
                            // A fast shorter than 2 minutes is almost certainly a double tap.
                            time - f.start < 2 * MINUTE_MS -> f.copy(deleted = true, updatedAt = t)
                            else -> f.copy(end = time, updatedAt = t)
                        }
                    }
                }
                if (start && (active == null || time >= active.start)) {
                    fasts = fasts + Fast(start = time, goalMinutes = d.settings.goalMinutes, updatedAt = t)
                }
            }
            d.copy(meals = d.meals + meal, fasts = fasts)
        }
        return meal
    }

    /** Sets (or clears) the meal plan. A plan also sets the fasting goal to match its window. */
    fun applyPlan(plan: MealPlan?) = commit(undoable = false) { d ->
        val t = now()
        if (plan == null) {
            d.copy(settings = d.settings.copy(plan = null))
        } else {
            d.copy(
                settings = d.settings.copy(plan = plan, goalMinutes = plan.fastMinutes),
                fasts = d.fasts.map { if (it.isActive) it.copy(goalMinutes = plan.fastMinutes, updatedAt = t) else it },
            )
        }
    }

    fun startFast(time: Long = now()) = commit { d ->
        if (d.activeFast != null) d
        else d.copy(fasts = d.fasts + Fast(start = time, goalMinutes = d.settings.goalMinutes))
    }

    fun endFast(time: Long = now()) = commit { d ->
        val a = d.activeFast ?: return@commit d
        val t = now()
        d.copy(fasts = d.fasts.map { if (it.id == a.id) it.copy(end = maxOf(time, it.start), updatedAt = t) else it })
    }

    fun updateFast(f: Fast) = commit { d ->
        val t = now()
        d.copy(fasts = d.fasts.map { if (it.id == f.id) f.copy(updatedAt = t) else it })
    }

    fun updateMeal(m: Meal) = commit { d ->
        val t = now()
        d.copy(meals = d.meals.map { if (it.id == m.id) m.copy(updatedAt = t) else it })
    }

    fun deleteFast(id: String) = commit { d ->
        val t = now()
        d.copy(fasts = d.fasts.map { if (it.id == id) it.copy(deleted = true, updatedAt = t) else it })
    }

    fun deleteMeal(id: String) = commit { d ->
        val t = now()
        d.copy(meals = d.meals.map { if (it.id == id) it.copy(deleted = true, updatedAt = t) else it })
    }

    fun addPastFast(start: Long, end: Long, goalMinutes: Int) = commit { d ->
        d.copy(fasts = d.fasts + Fast(start = start, end = end, goalMinutes = goalMinutes))
    }

    /** Sets the goal for future fasts and the one that's running now. */
    fun setGoal(minutes: Int) = commit(undoable = false) { d ->
        val t = now()
        d.copy(
            settings = d.settings.copy(goalMinutes = minutes),
            fasts = d.fasts.map { if (it.isActive) it.copy(goalMinutes = minutes, updatedAt = t) else it },
        )
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) = commit(undoable = false) { d ->
        d.copy(settings = transform(d.settings))
    }

    fun deleteAll() = commit(undoable = false) { d ->
        val t = now()
        d.copy(
            meals = d.meals.map { if (it.deleted) it else it.copy(deleted = true, updatedAt = t) },
            fasts = d.fasts.map { if (it.deleted) it else it.copy(deleted = true, updatedAt = t) },
        )
    }

    /** Reverts the last meal/fast action. Done as new edits (not a rewind) so it syncs correctly. */
    fun undo(): Boolean {
        val base = undoBase ?: return false
        undoBase = null
        commit(undoable = false) { cur -> revert(cur, base) }
        return true
    }

    private fun revert(cur: AppData, base: AppData): AppData {
        val t = now()
        val oldMeals = base.meals.associateBy { it.id }
        val oldFasts = base.fasts.associateBy { it.id }
        val meals = cur.meals.map { m ->
            val old = oldMeals[m.id]
            when {
                old == null -> m.copy(deleted = true, updatedAt = t)
                old != m -> old.copy(updatedAt = t)
                else -> m
            }
        }
        val fasts = cur.fasts.map { f ->
            val old = oldFasts[f.id]
            when {
                old == null -> f.copy(deleted = true, updatedAt = t)
                old != f -> old.copy(updatedAt = t)
                else -> f
            }
        }
        return cur.copy(meals = meals, fasts = fasts)
    }

    fun importMerge(remote: AppData): Merge.Result {
        var result = Merge.Result(_data.value, 0, 0)
        commit(undoable = false) { cur -> Merge.merge(cur, remote).also { result = it }.data }
        return result
    }

    fun applySync(remote: AppData?): Merge.Result {
        if (remote == null) return Merge.Result(_data.value, 0, 0)
        var result = Merge.Result(_data.value, 0, 0)
        val out = commit(undoable = false, fromSync = true) { cur -> Merge.merge(cur, remote).also { result = it }.data }
        return result.copy(data = out)
    }
}
