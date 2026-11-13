package app.fastwise.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fastwise.R
import app.fastwise.data.AppData
import app.fastwise.data.FastRepository
import app.fastwise.data.Fmt
import app.fastwise.data.HOUR_MS
import app.fastwise.data.MINUTE_MS
import app.fastwise.data.Stats

/** Schedules the halfway, one-hour-to-go and goal-reached notifications for the running fast. */
object GoalAlarm {
    private val kinds = listOf(
        ActionReceiver.ACTION_GOAL to 30,
        ActionReceiver.ACTION_HALF to 31,
        ActionReceiver.ACTION_HOUR_LEFT to 32,
    )

    fun schedule(ctx: Context, d: AppData) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        cancel(ctx)
        val f = d.activeFast ?: return
        val goal = f.goalMs
        if (d.settings.goalAlert) setAlarm(am, f.start + goal, Notifier.actionIntent(ctx, ActionReceiver.ACTION_GOAL, 30))
        if (d.settings.nudges) {
            setAlarm(am, f.start + goal / 2, Notifier.actionIntent(ctx, ActionReceiver.ACTION_HALF, 31))
            if (goal >= 3 * HOUR_MS) {
                setAlarm(am, f.start + goal - HOUR_MS, Notifier.actionIntent(ctx, ActionReceiver.ACTION_HOUR_LEFT, 32))
            }
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        kinds.forEach { (action, code) -> am.cancel(Notifier.actionIntent(ctx, action, code)) }
    }

    fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent) {
        if (at <= System.currentTimeMillis()) return
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    @SuppressLint("MissingPermission")
    fun notifyMilestone(ctx: Context, action: String?) {
        val d = FastRepository.data.value
        val f = d.activeFast ?: return
        if (!Notifier.canPost(ctx)) return
        val now = System.currentTimeMillis()
        val elapsed = f.durationMs(now)
        val goal = f.goalMs
        val left = goal - elapsed
        val is24 = DateFormat.is24HourFormat(ctx)
        val streak = Stats.streaks(d, now).first
        val channel: String
        val id: Int
        val title: String
        val text: String
        when (action) {
            ActionReceiver.ACTION_HALF -> {
                if (!d.settings.nudges || elapsed < goal / 2 || left <= 0) return
                channel = Notifier.CHANNEL_NUDGES
                id = Notifier.ID_NUDGE
                title = "Halfway there"
                text = "${Fmt.hm(elapsed)} done, ${Fmt.hm(left)} to go. The hardest part is behind you."
            }
            ActionReceiver.ACTION_HOUR_LEFT -> {
                if (!d.settings.nudges || left <= 0 || left > HOUR_MS + 5 * MINUTE_MS) return
                channel = Notifier.CHANNEL_NUDGES
                id = Notifier.ID_NUDGE
                title = "One hour to go"
                text = "Your ${Fmt.hm(goal)} goal lands at ${Fmt.clock(f.start + goal, is24)}. " +
                    if (streak > 0) "Finish it to keep your streak going." else "Finish strong."
            }
            else -> {
                if (!d.settings.goalAlert || left > 0) return
                channel = Notifier.CHANNEL_ALERTS
                id = Notifier.ID_GOAL
                title = "${Fmt.hm(goal)} fast complete"
                text = if (streak > 1) {
                    "That's ${Fmt.plural(streak, "day")} in a row. Eat whenever you're ready."
                } else {
                    "Nice work. Eat whenever you're ready, and a new fast starts when you log it."
                }
            }
        }
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat_fast)
            .setColor(0xFFF4B860.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(Notifier.openAppIntent(ctx))
            .addAction(0, "I just ate", Notifier.actionIntent(ctx, ActionReceiver.ACTION_LOG_MEAL, 12))
            .build()
        val nm = NotificationManagerCompat.from(ctx)
        if (id == Notifier.ID_GOAL) nm.cancel(Notifier.ID_NUDGE)
        nm.notify(id, n)
    }
}
