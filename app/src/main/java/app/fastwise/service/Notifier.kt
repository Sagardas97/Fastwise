package app.fastwise.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.fastwise.R
import app.fastwise.data.AppData
import app.fastwise.data.Fmt
import app.fastwise.data.MINUTE_MS
import app.fastwise.data.Motivation
import app.fastwise.data.Stages
import app.fastwise.data.Stats
import app.fastwise.ui.MainActivity

object Notifier {
    const val CHANNEL_TIMER = "fasting_timer"
    const val CHANNEL_ALERTS = "goal_alerts"
    const val CHANNEL_NUDGES = "encouragement"
    const val CHANNEL_MEALS = "meal_reminders"
    const val ID_TIMER = 1001
    const val ID_GOAL = 1002
    const val ID_NUDGE = 1003
    const val ID_MEAL = 1004
    private const val AMBER = 0xFFF4B860.toInt()

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val timer = NotificationChannel(CHANNEL_TIMER, "Fasting timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows your running fast in the notification bar"
            setShowBadge(false)
        }
        val alerts = NotificationChannel(CHANNEL_ALERTS, "Goal alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Tells you when you reach your fasting goal"
        }
        val nudges = NotificationChannel(CHANNEL_NUDGES, "Encouragement", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Halfway and one-hour-to-go nudges during a fast"
        }
        val meals = NotificationChannel(CHANNEL_MEALS, "Meal reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Planned meal times and when your eating window is about to close"
        }
        nm.createNotificationChannels(listOf(timer, alerts, nudges, meals))
    }

    fun canPost(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun actionIntent(ctx: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, requestCode,
        Intent(ctx, ActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The live timer: the header chronometer counts up by itself, so no per-second updates are needed. */
    fun buildTimer(ctx: Context, d: AppData, now: Long): Notification? {
        val f = d.activeFast ?: return null
        val elapsed = f.durationMs(now)
        val goal = f.goalMs.coerceAtLeast(MINUTE_MS)
        val pct = (elapsed * 100 / goal).toInt()
        val reached = elapsed >= goal
        val is24 = DateFormat.is24HourFormat(ctx)
        val stage = Stages.at(elapsed)
        val title = if (reached) "Goal reached" else "Fasting, $pct% of ${Fmt.hm(goal)}"
        val text = if (reached) {
            "${Fmt.hm(elapsed - goal)} past your ${Fmt.hm(goal)} goal. ${stage.title}."
        } else {
            "${stage.title}. Goal at ${Fmt.clock(f.start + goal, is24)}."
        }
        val cheer = Motivation.line(f, now, Stats.streaks(d, now).first)
        return NotificationCompat.Builder(ctx, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_fast)
            .setColor(AMBER)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$cheer"))
            .setUsesChronometer(true)
            .setWhen(f.start)
            .setShowWhen(true)
            .setProgress(100, pct.coerceIn(0, 100), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(ctx))
            .addAction(0, "I just ate", actionIntent(ctx, ActionReceiver.ACTION_LOG_MEAL, 10))
            .addAction(0, "End fast", actionIntent(ctx, ActionReceiver.ACTION_END_FAST, 11))
            .build()
    }

    fun placeholder(ctx: Context): Notification = NotificationCompat.Builder(ctx, CHANNEL_TIMER)
        .setSmallIcon(R.drawable.ic_stat_fast)
        .setContentTitle("Fastwise")
        .setSilent(true)
        .build()

    /** Shows, updates or removes the timer notification to match the data. */
    @SuppressLint("MissingPermission")
    fun sync(ctx: Context, d: AppData) {
        val nm = NotificationManagerCompat.from(ctx)
        val n = if (d.settings.notificationOn) buildTimer(ctx, d, System.currentTimeMillis()) else null
        if (n == null) {
            ctx.stopService(Intent(ctx, FastingService::class.java))
            nm.cancel(ID_TIMER)
            return
        }
        if (FastingService.running) {
            if (canPost(ctx)) nm.notify(ID_TIMER, n)
            return
        }
        try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, FastingService::class.java))
        } catch (e: Exception) {
            // Android can refuse to start a foreground service from the background.
            // Fall back to a regular ongoing notification; the chronometer still ticks.
            if (canPost(ctx)) nm.notify(ID_TIMER, n)
        }
    }
}
