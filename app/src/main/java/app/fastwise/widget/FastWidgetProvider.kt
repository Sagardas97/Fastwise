package app.fastwise.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import app.fastwise.R
import app.fastwise.data.AppData
import app.fastwise.data.FastRepository
import app.fastwise.data.Fmt
import app.fastwise.data.Goals
import app.fastwise.data.Stages
import app.fastwise.service.ActionReceiver
import app.fastwise.service.Notifier

/** Home screen widget. The Chronometer view ticks on its own, so it stays live without updates. */
class FastWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        render(context, appWidgetManager, appWidgetIds, FastRepository.data.value)
    }

    companion object {
        fun updateAll(ctx: Context, d: AppData) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, FastWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids, d)
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray, d: AppData) {
            val now = System.currentTimeMillis()
            val v = RemoteViews(ctx.packageName, R.layout.widget_fast)
            val f = d.activeFast
            if (f != null) {
                val elapsed = f.durationMs(now)
                val pct = (elapsed * 100 / f.goalMs).toInt()
                v.setViewVisibility(R.id.widget_chrono, View.VISIBLE)
                v.setViewVisibility(R.id.widget_idle, View.GONE)
                v.setChronometer(R.id.widget_chrono, SystemClock.elapsedRealtime() - elapsed, null, true)
                v.setTextViewText(R.id.widget_label, Stages.at(elapsed).title)
                v.setTextViewText(R.id.widget_goal, Goals.name(f.goalMinutes))
                v.setProgressBar(R.id.widget_progress, 100, pct.coerceIn(0, 100), false)
                v.setTextViewText(
                    R.id.widget_detail,
                    if (elapsed >= f.goalMs) "Goal reached" else "$pct% of ${Fmt.hm(f.goalMs)}",
                )
            } else {
                v.setChronometer(R.id.widget_chrono, SystemClock.elapsedRealtime(), null, false)
                v.setViewVisibility(R.id.widget_chrono, View.GONE)
                v.setViewVisibility(R.id.widget_idle, View.VISIBLE)
                v.setTextViewText(R.id.widget_label, "Fastwise")
                v.setTextViewText(R.id.widget_goal, Goals.name(d.settings.goalMinutes))
                v.setProgressBar(R.id.widget_progress, 100, 0, false)
                val last = d.lastMeal
                v.setTextViewText(
                    R.id.widget_detail,
                    if (last != null) "Last meal ${Fmt.hm(now - last.time)} ago" else "Tap after your next meal",
                )
            }
            v.setOnClickPendingIntent(R.id.widget_button, Notifier.actionIntent(ctx, ActionReceiver.ACTION_LOG_MEAL, 20))
            v.setOnClickPendingIntent(R.id.widget_root, Notifier.openAppIntent(ctx))
            mgr.updateAppWidget(ids, v)
        }
    }
}
