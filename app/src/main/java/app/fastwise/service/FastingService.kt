package app.fastwise.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import app.fastwise.data.DevicePrefs
import app.fastwise.data.FastRepository
import app.fastwise.widget.FastWidgetProvider

/**
 * Holds the timer notification as a foreground service so it can't be swiped away
 * and Samsung's battery manager leaves it alone. The service does no work besides
 * refreshing the progress text once a minute.
 */
class FastingService : Service() {
    companion object {
        @Volatile
        var running: Boolean = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val d = FastRepository.data.value
        // If Android restarts this service after Quit, it shuts down again instead of bringing the timer back.
        val show = d.settings.notificationOn && !DevicePrefs.isQuit(this)
        val n = if (show) Notifier.buildTimer(this, d, System.currentTimeMillis()) else null
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(this, Notifier.ID_TIMER, n ?: Notifier.placeholder(this), type)
        } catch (e: Exception) {
            if (n != null && Notifier.canPost(this)) NotificationManagerCompat.from(this).notify(Notifier.ID_TIMER, n)
            stopSelf()
            return START_NOT_STICKY
        }
        if (n == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 60_000L)
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun refresh() {
        val d = FastRepository.data.value
        val n = if (d.settings.notificationOn) Notifier.buildTimer(this, d, System.currentTimeMillis()) else null
        if (n == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (Notifier.canPost(this)) NotificationManagerCompat.from(this).notify(Notifier.ID_TIMER, n)
        FastWidgetProvider.updateAll(this, d)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        running = false
        super.onDestroy()
    }
}
