package pl.voxverum.app.alert

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import pl.voxverum.app.MainActivity
import pl.voxverum.app.core.Notifications
import pl.voxverum.app.risk.RiskAssessment
import pl.voxverum.app.risk.RiskLevel

/**
 * Wibracja i powiadomienie przy wzroście ryzyka. Odpala się tylko, gdy poziom rośnie
 * (OK → WARN → DANGER), a nie co sekundę, żeby nie zagłuszać rozmowy.
 */
class Alerter(private val context: Context) {
    private var lastAlerted = RiskLevel.OK

    fun onAssessment(risk: RiskAssessment) {
        val level = risk.overall
        if (level <= lastAlerted) return
        lastAlerted = level
        when (level) {
            RiskLevel.WARN -> vibrate(longArrayOf(0, 200))
            RiskLevel.DANGER -> {
                vibrate(longArrayOf(0, 400, 150, 400, 150, 400))
                notify(risk)
            }
            else -> Unit
        }
    }

    fun reset() {
        lastAlerted = RiskLevel.OK
    }

    private fun vibrate(pattern: LongArray) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun notify(risk: RiskAssessment) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, Notifications.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(AlertTexts.notificationTitle(risk))
            .setContentText(AlertTexts.notificationText(risk))
            .setStyle(Notification.BigTextStyle().bigText(AlertTexts.notificationText(risk)))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(Notifications.ID_ALERT, notification)
    }
}
