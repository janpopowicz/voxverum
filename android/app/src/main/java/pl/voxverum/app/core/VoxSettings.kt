package pl.voxverum.app.core

import android.content.Context
import android.media.MediaRecorder

/** Ustawienia użytkownika w SharedPreferences. Bez audio, tylko przełączniki. */
class VoxSettings(context: Context) {
    private val prefs = context.getSharedPreferences("vox_settings", Context.MODE_PRIVATE)

    var protectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION, true)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION, value).apply()

    var consentAccepted: Boolean
        get() = prefs.getBoolean(KEY_CONSENT, false)
        set(value) = prefs.edit().putBoolean(KEY_CONSENT, value).apply()

    /** `MediaRecorder.AudioSource.*`; do porównania w teście wykonalności. */
    var audioSource: Int
        get() = prefs.getInt(KEY_SOURCE, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        set(value) = prefs.edit().putInt(KEY_SOURCE, value).apply()

    /** Tylko wersja debug: zapisuje pierwsze sekundy rozmowy do WAV, do testu wykonalności. */
    var debugDumpEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_DUMP, false)
        set(value) = prefs.edit().putBoolean(KEY_DEBUG_DUMP, value).apply()

    private companion object {
        const val KEY_PROTECTION = "protection_enabled"
        const val KEY_CONSENT = "consent_accepted"
        const val KEY_SOURCE = "audio_source"
        const val KEY_DEBUG_DUMP = "debug_dump"
    }
}
