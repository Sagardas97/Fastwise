package app.fastwise.service

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import app.fastwise.data.DevicePrefs
import app.fastwise.data.FastRepository
import app.fastwise.data.SyncManager

/**
 * Settings > Quit Fastwise. Stops everything that runs in the background (the timer service,
 * goal and meal alarms, notifications, a waiting sync), closes the app and ends its process.
 * Data is kept. Nothing starts again on its own, not even after a restart or an app update,
 * until the user opens Fastwise or logs a meal from the widget or Quick Settings tile.
 */
object AppQuit {
    fun quit(context: Context) {
        val ctx = context.applicationContext
        DevicePrefs.setQuit(ctx, true)
        SyncManager.flushPending()
        ctx.stopService(Intent(ctx, FastingService::class.java))
        GoalAlarm.cancel(ctx)
        PlanAlarm.cancel(ctx)
        NotificationManagerCompat.from(ctx).cancelAll()
        context.findActivity()?.finishAndRemoveTask()

        // Saves and syncs run one at a time on the data thread, so the exit waits behind them
        // and nothing is lost. A slow sync file gets 10 seconds at most.
        val main = Handler(Looper.getMainLooper())
        val exit = Runnable { Process.killProcess(Process.myPid()) }
        FastRepository.runOnIo { main.postDelayed(exit, 300L) }
        main.postDelayed(exit, 10_000L)
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
