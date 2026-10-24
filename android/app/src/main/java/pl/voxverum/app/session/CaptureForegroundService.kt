package pl.voxverum.app.session

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import pl.voxverum.app.MainActivity
import pl.voxverum.app.core.Notifications

/**
 * Pusta usługa foreground typu `microphone`. Nie nagrywa sama (robi to sesja w procesie
 * usługi ułatwień dostępu), tylko podnosi priorytet procesu, żeby Android nie odciął
 * mikrofonu w tle, i pokazuje użytkownikowi stałe powiadomienie „analizuję rozmowę”.
 */
class CaptureForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, Notifications.CHANNEL_SESSION)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("VoxVerum analizuje rozmowę")
            .setContentText("Dźwięk jest analizowany na telefonie i nie jest zapisywany.")
            .setOngoing(true)
            .setContentIntent(openApp)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startForeground(Notifications.ID_SESSION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(Notifications.ID_SESSION, notification)
            }
        } catch (e: RuntimeException) {
            // Android 14+ może odmówić startu z tła; sesja próbuje wtedy nagrywać bez tej usługi.
            Log.w(TAG, "startForeground(microphone) refused", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "VoxVerum"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, CaptureForegroundService::class.java)) }
                .onFailure { Log.w(TAG, "Could not start CaptureForegroundService", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CaptureForegroundService::class.java))
        }
    }
}
