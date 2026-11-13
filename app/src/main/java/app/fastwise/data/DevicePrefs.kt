package app.fastwise.data

import android.content.Context
import android.net.Uri
import java.util.UUID

/** Per-device settings. Excluded from Android backup because file permissions don't transfer. */
object DevicePrefs {
    private fun sp(c: Context) = c.getSharedPreferences("device", Context.MODE_PRIVATE)

    fun deviceId(c: Context): String {
        val p = sp(c)
        p.getString("id", null)?.let { return it }
        val id = UUID.randomUUID().toString().take(8)
        p.edit().putString("id", id).apply()
        return id
    }

    fun syncUri(c: Context): Uri? = sp(c).getString("syncUri", null)?.let { Uri.parse(it) }
    fun syncName(c: Context): String? = sp(c).getString("syncName", null)
    fun lastSync(c: Context): Long = sp(c).getLong("lastSync", 0L)

    fun setSync(c: Context, uri: Uri?, name: String?) {
        sp(c).edit().putString("syncUri", uri?.toString()).putString("syncName", name).putLong("lastSync", 0L).apply()
    }

    fun celebratedId(c: Context): String? = sp(c).getString("celebrated", null)

    fun setCelebratedId(c: Context, id: String) {
        sp(c).edit().putString("celebrated", id).apply()
    }

    fun setLastSync(c: Context, t: Long) {
        sp(c).edit().putLong("lastSync", t).apply()
    }

    /** True after Settings > Quit, until the user opens the app or logs a meal from the widget or tile. */
    fun isQuit(c: Context): Boolean = sp(c).getBoolean("quit", false)

    /** Saved with commit(), not apply(), because quitting ends the process right after. */
    fun setQuit(c: Context, quit: Boolean) {
        if (isQuit(c) != quit) sp(c).edit().putBoolean("quit", quit).commit()
    }
}
