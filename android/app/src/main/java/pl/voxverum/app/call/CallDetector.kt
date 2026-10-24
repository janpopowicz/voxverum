package pl.voxverum.app.call

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.voxverum.app.core.CallState

/**
 * Wykrywa rozmowę WhatsApp. Łączy dwa sygnały:
 * 1. zdarzenia ułatwień dostępu z pakietu WhatsApp (okno połączenia albo powiadomienie o połączeniu),
 * 2. tryb audio systemu `MODE_IN_COMMUNICATION`, który komunikator ustawia na czas rozmowy VoIP.
 *
 * Rozmowa zaczyna się, gdy tryb audio przechodzi na komunikację, a WhatsApp był aktywny
 * w ciągu ostatnich [APP_SEEN_WINDOW_MS] ms. Kończy się, gdy tryb nie wraca do komunikacji
 * przez [END_GRACE_MS] ms.
 */
class CallDetector(
    context: Context,
    private val scope: CoroutineScope,
    private val onStateChanged: (CallState) -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainExecutor = context.mainExecutor
    private val modeListener = AudioManager.OnModeChangedListener { evaluate() }
    private var pollJob: Job? = null
    private var endJob: Job? = null

    private var lastAppPackage: String? = null
    private var lastAppSeenAtMs = 0L
    private var state: CallState = CallState.Idle

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.addOnModeChangedListener(mainExecutor, modeListener)
        } else {
            pollJob = scope.launch {
                while (isActive) {
                    evaluate()
                    delay(POLL_INTERVAL_MS)
                }
            }
        }
        evaluate()
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.removeOnModeChangedListener(modeListener)
        }
        pollJob?.cancel()
        pollJob = null
        endJob?.cancel()
        endJob = null
        updateState(CallState.Idle)
    }

    /** Wołane przez usługę dla każdego zdarzenia z pakietu komunikatora. */
    fun onVoipAppEvent(packageName: String) {
        if (packageName !in SUPPORTED_PACKAGES) return
        lastAppPackage = packageName
        lastAppSeenAtMs = SystemClock.elapsedRealtime()
        evaluate()
    }

    private fun evaluate() {
        val inCommunication = isInCommunication()
        when (state) {
            CallState.Idle -> if (inCommunication && appSeenRecently()) {
                updateState(CallState.Active(lastAppPackage!!, System.currentTimeMillis()))
            }
            is CallState.Active -> if (inCommunication) {
                endJob?.cancel()
                endJob = null
            } else if (endJob == null) {
                // Tryb audio potrafi na chwilę zmienić się w trakcie rozmowy (np. przełączenie
                // urządzenia audio). Kończymy dopiero, gdy nie wraca przez END_GRACE_MS, żeby nie
                // zerować oceny ryzyka (i zatrzaśniętego alarmu) w połowie tej samej rozmowy.
                endJob = scope.launch {
                    delay(END_GRACE_MS)
                    endJob = null
                    if (!isInCommunication()) updateState(CallState.Idle)
                }
            }
        }
    }

    private fun isInCommunication() = audioManager.mode == AudioManager.MODE_IN_COMMUNICATION

    private fun appSeenRecently(): Boolean =
        lastAppPackage != null &&
            SystemClock.elapsedRealtime() - lastAppSeenAtMs <= APP_SEEN_WINDOW_MS

    private fun updateState(newState: CallState) {
        if (newState == state) return
        state = newState
        Log.i(TAG, "Call state -> $newState")
        onStateChanged(newState)
    }

    companion object {
        private const val TAG = "VoxVerum"
        private const val POLL_INTERVAL_MS = 1_000L
        private const val APP_SEEN_WINDOW_MS = 60_000L
        private const val END_GRACE_MS = 2_000L

        val SUPPORTED_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
