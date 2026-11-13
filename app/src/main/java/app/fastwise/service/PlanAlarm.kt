package app.fastwise.service

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.fastwise.R
import app.fastwise.data.AppData
import app.fastwise.data.FastRepository
import app.fastwise.data.Fmt
import app.fastwise.data.MINUTE_MS
import app.fastwise.data.MealPlan
import app.fastwise.data.Plans

/** Meal-time and window-closing reminders for the chosen meal plan. One alarm at a time, re-armed after each. */
object PlanAlarm {
    private const val CODE = 40

    fun schedule(ctx: Context, d: AppData) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = Notifier.actionIntent(ctx, ActionReceiver.ACTION_PLAN, CODE)
        am.cancel(pi)
        val plan = d.settings.plan ?: return
        if (!d.settings.mealReminders) return
        val next = Plans.nextEvent(plan, System.currentTimeMillis()) ?: return
        GoalAlarm.setAlarm(am, next.time, pi)
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(Notifier.actionIntent(ctx, ActionReceiver.ACTION_PLAN, CODE))
    }

    fun fire(ctx: Context) {
        val d = FastRepository.data.value
        val plan = d.settings.plan
        if (plan != null && d.settings.mealReminders && Notifier.canPost(ctx)) {
            val now = System.currentTimeMillis()
            val event = Plans.eventsAround(plan, now)
                .filter { it.time in (now - 20 * MINUTE_MS)..(now + MINUTE_MS) }
                .maxByOrNull { it.time }
            if (event != null) notifyEvent(ctx, d, plan, event, now)
        }
        schedule(ctx, d)
    }

    @SuppressLint("MissingPermission")
    private fun notifyEvent(ctx: Context, d: AppData, plan: MealPlan, event: Plans.PlanEvent, now: Long) {
        val is24 = DateFormat.is24HourFormat(ctx)
        val active = d.activeFast
        val today = Fmt.date(now)
        val slot = event.slot
        val title: String
        val text: String
        if (slot != null) {
            title = "${slot.label} time"
            text = when {
                active == null -> "Your eating window is open until ${Fmt.clock(Plans.timeOn(today, plan.windowEnd), is24)}."
                active.reachedGoal(now) -> "Your ${Fmt.hm(active.goalMs)} fast is done. Enjoy your meal."
                else -> "You're ${Fmt.hm(active.goalMs - active.durationMs(now))} from your ${Fmt.hm(active.goalMs)} goal. " +
                    "Eat now or wait a little, either is fine."
            }
        } else {
            // Skip if the last meal is already logged and the fast is running.
            if (active != null && active.start >= Plans.timeOn(today, plan.windowStart)) return
            title = "Eating window closes in 30 minutes"
            text = "After your last meal, tap I just ate so your fast starts on time."
        }
        val builder = NotificationCompat.Builder(ctx, Notifier.CHANNEL_MEALS)
            .setSmallIcon(R.drawable.ic_stat_fast)
            .setColor(0xFFF4B860.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(Notifier.openAppIntent(ctx))
            .addAction(0, "I just ate", Notifier.actionIntent(ctx, ActionReceiver.ACTION_LOG_MEAL, 13))
        if (slot == null) builder.addAction(0, "Start fast now", Notifier.actionIntent(ctx, ActionReceiver.ACTION_START_FAST, 14))
        NotificationManagerCompat.from(ctx).notify(Notifier.ID_MEAL, builder.build())
    }
}
