package pl.voxverum.app.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_SESSION = "session"
    const val CHANNEL_ALERTS = "alerts"
    const val ID_SESSION = 1
    const val ID_ALERT = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SESSION, "Analiza rozmowy", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Widoczne, gdy VoxVerum analizuje trwającą rozmowę."
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Ostrzeżenia", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ostrzeżenia o sklonowanym głosie i próbie wyłudzenia."
                enableVibration(true)
            },
        )
    }
}
