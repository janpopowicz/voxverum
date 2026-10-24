package pl.voxverum.app.ml

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wynik obu modeli dla jednego okna. */
data class Scores(
    val clone: Float,
    val scam: Float,
    val latencyMs: Long,
)

/** Puszcza okno przez oba klasyfikatory poza wątkiem głównym. */
class WindowAnalyzer(private val classifiers: Map<Task, AudioClassifier>) {
    val voiceClone: AudioClassifier get() = classifiers.getValue(Task.VOICE_CLONE)
    val scam: AudioClassifier get() = classifiers.getValue(Task.SCAM)

    suspend fun analyze(window: FloatArray): Scores = withContext(Dispatchers.Default) {
        val start = SystemClock.elapsedRealtime()
        val clone = voiceClone.classify(window)
        val scamScore = scam.classify(window)
        Scores(clone, scamScore, SystemClock.elapsedRealtime() - start)
    }
}
