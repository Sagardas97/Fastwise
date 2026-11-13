package app.fastwise.service

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.fastwise.data.FastRepository
import app.fastwise.data.Fmt

/** Quick Settings tile: one tap logs a meal and starts a new fast. */
class FastTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { logMeal() } else logMeal()
    }

    private fun logMeal() {
        FastRepository.logMeal()
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val f = FastRepository.data.value.activeFast
        tile.state = if (f != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "I just ate"
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = if (f != null) "Fasting ${Fmt.hm(f.durationMs(System.currentTimeMillis()))}" else "Starts a fast"
        }
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(ctx: Context) {
            try {
                requestListeningState(ctx, ComponentName(ctx, FastTileService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
