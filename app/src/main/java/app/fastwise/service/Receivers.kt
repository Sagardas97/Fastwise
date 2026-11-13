package app.fastwise.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import app.fastwise.data.FastRepository
import app.fastwise.data.Fmt
import app.fastwise.data.MINUTE_MS

/** Handles taps on notification, widget and alarm actions. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_LOG_MEAL -> {
                val previous = FastRepository.data.value.activeFast
                val meal = FastRepository.logMeal()
                val nm = NotificationManagerCompat.from(context)
                nm.cancel(Notifier.ID_GOAL)
                nm.cancel(Notifier.ID_NUDGE)
                nm.cancel(Notifier.ID_MEAL)
                val started = FastRepository.data.value.activeFast?.start == meal.time
                val saved = previous?.durationMs(meal.time)?.takeIf { it >= 2 * MINUTE_MS }
                val message = buildString {
                    append("${meal.label} logged.")
                    if (saved != null) append(" ${Fmt.hm(saved)} fast saved.")
                    append(if (started) " New fast started." else " The next fast starts after your last planned meal.")
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            ACTION_END_FAST -> {
                val active = FastRepository.data.value.activeFast
                FastRepository.endFast()
                NotificationManagerCompat.from(context).cancel(Notifier.ID_GOAL)
                NotificationManagerCompat.from(context).cancel(Notifier.ID_NUDGE)
                if (active != null) {
                    Toast.makeText(
                        context,
                        "Fast ended at ${Fmt.hm(active.durationMs(System.currentTimeMillis()))}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            ACTION_GOAL, ACTION_HALF, ACTION_HOUR_LEFT -> GoalAlarm.notifyMilestone(context, intent.action)
            ACTION_PLAN -> PlanAlarm.fire(context)
            ACTION_START_FAST -> {
                FastRepository.startFast()
                NotificationManagerCompat.from(context).cancel(Notifier.ID_MEAL)
                Toast.makeText(context, "Fast started", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        const val ACTION_LOG_MEAL = "app.fastwise.action.LOG_MEAL"
        const val ACTION_END_FAST = "app.fastwise.action.END_FAST"
        const val ACTION_GOAL = "app.fastwise.action.GOAL"
        const val ACTION_HALF = "app.fastwise.action.HALF"
        const val ACTION_HOUR_LEFT = "app.fastwise.action.HOUR_LEFT"
        const val ACTION_PLAN = "app.fastwise.action.PLAN"
        const val ACTION_START_FAST = "app.fastwise.action.START_FAST"
    }
}

/** Brings the timer notification, widget and goal alarm back after a restart or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Effects.apply(context, FastRepository.data.value)
        }
    }
}
