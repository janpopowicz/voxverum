package pl.voxverum.app

import android.app.Application
import pl.voxverum.app.core.Notifications

class VoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
