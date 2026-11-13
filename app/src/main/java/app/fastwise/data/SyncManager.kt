package app.fastwise.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException

/**
 * Two-way sync through one shared JSON file (for example in Google Drive).
 * Each sync reads the file, merges it into local data, and writes the merged result back.
 * Any number of devices can link the same file.
 */
object SyncManager {
    data class Status(
        val linked: Boolean = false,
        val fileName: String? = null,
        val lastSync: Long = 0L,
        val error: String? = null,
        val busy: Boolean = false,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private lateinit var app: Context
    private val main = Handler(Looper.getMainLooper())
    private var autoPending = false
    private val autoSync = Runnable {
        autoPending = false
        syncNow(null)
    }
    private var lastOpenSync = 0L
    private const val RW = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun init(context: Context) {
        app = context.applicationContext
        _status.value = Status(
            linked = DevicePrefs.syncUri(app) != null,
            fileName = DevicePrefs.syncName(app),
            lastSync = DevicePrefs.lastSync(app),
        )
    }

    fun link(uri: Uri, onResult: (String) -> Unit) {
        try {
            app.contentResolver.takePersistableUriPermission(uri, RW)
        } catch (e: SecurityException) {
            onResult("That location doesn't allow ongoing access. Pick a file in Google Drive or on this phone.")
            return
        }
        val name = displayName(uri) ?: "Sync file"
        DevicePrefs.setSync(app, uri, name)
        _status.value = Status(linked = true, fileName = name)
        syncNow(onResult)
    }

    fun unlink() {
        DevicePrefs.syncUri(app)?.let { uri ->
            runCatching { app.contentResolver.releasePersistableUriPermission(uri, RW) }
        }
        DevicePrefs.setSync(app, null, null)
        main.removeCallbacks(autoSync)
        autoPending = false
        _status.value = Status()
    }

    /** Called after every local change; batches quick edits into one sync. */
    fun scheduleAuto() {
        if (!_status.value.linked) return
        main.removeCallbacks(autoSync)
        autoPending = true
        main.postDelayed(autoSync, 4_000L)
    }

    /** On Quit: runs a sync that's still waiting right away, so the last changes reach the sync file. */
    fun flushPending() {
        main.removeCallbacks(autoSync)
        if (autoPending) {
            autoPending = false
            syncNow(null)
        }
    }

    fun syncOnOpen() {
        if (!_status.value.linked) return
        val t = System.currentTimeMillis()
        if (t - lastOpenSync < 60_000L) return
        lastOpenSync = t
        syncNow(null)
    }

    fun syncNow(onResult: ((String) -> Unit)?) {
        val uri = DevicePrefs.syncUri(app) ?: return
        _status.update { it.copy(busy = true) }
        FastRepository.runOnIo {
            val message = try {
                val remote = readRemote(uri)
                val result = FastRepository.applySync(remote)
                writeRemote(uri, Codec.encode(result.data, FastRepository.deviceId).toByteArray(Charsets.UTF_8))
                val t = System.currentTimeMillis()
                DevicePrefs.setLastSync(app, t)
                _status.update { it.copy(busy = false, lastSync = t, error = null) }
                if (result.changes > 0) "Synced. ${Fmt.plural(result.changes, "change")} from your other device."
                else "Synced. Everything is up to date."
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _status.update { it.copy(busy = false, error = reason) }
                "Sync failed: $reason"
            }
            if (onResult != null) main.post { onResult(message) }
        }
    }

    private fun readRemote(uri: Uri): AppData? {
        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Can't open the sync file")
        val text = bytes.toString(Charsets.UTF_8).trim()
        if (text.isEmpty()) return null
        return Codec.decode(text)
    }

    private fun writeRemote(uri: Uri, bytes: ByteArray) {
        var lastError: Exception? = null
        // Not every storage app supports truncate mode, so try the safest modes first.
        for (mode in listOf("wt", "rwt", "w")) {
            try {
                val out = app.contentResolver.openOutputStream(uri, mode) ?: throw IOException("Can't write the sync file")
                out.use {
                    it.write(bytes)
                    it.flush()
                }
                return
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IOException("Can't write the sync file")
    }

    private fun displayName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}
