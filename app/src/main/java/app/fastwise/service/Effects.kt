package app.fastwise.service

import android.content.Context
import android.util.Log
import app.fastwise.data.AppData
import app.fastwise.data.DevicePrefs
import app.fastwise.widget.FastWidgetProvider

/** Keeps everything outside the app (notification, alarm, widget, tile) in step with the data. */
object Effects {
    fun apply(context: Context, d: AppData) {
        val ctx = context.applicationContext
        // After Quit nothing starts again on its own (restart, app update, a sync landing) until the user comes back.
        if (DevicePrefs.isQuit(ctx)) return
        runCatching { Notifier.sync(ctx, d) }.onFailure { Log.w("Effects", "notification", it) }
        runCatching { GoalAlarm.schedule(ctx, d) }.onFailure { Log.w("Effects", "alarm", it) }
        runCatching { PlanAlarm.schedule(ctx, d) }.onFailure { Log.w("Effects", "plan", it) }
        runCatching { FastWidgetProvider.updateAll(ctx, d) }.onFailure { Log.w("Effects", "widget", it) }
        runCatching { FastTileService.requestUpdate(ctx) }.onFailure { Log.w("Effects", "tile", it) }
    }
}
