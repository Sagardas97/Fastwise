package app.fastwise

import android.app.Application
import app.fastwise.data.FastRepository
import app.fastwise.data.SyncManager
import app.fastwise.service.Notifier

class FastwiseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        FastRepository.init(this)
        SyncManager.init(this)
    }
}
