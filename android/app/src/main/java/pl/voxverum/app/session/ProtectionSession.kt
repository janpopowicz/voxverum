package pl.voxverum.app.session

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import pl.voxverum.app.audio.AudioSource
import pl.voxverum.app.audio.MicAudioSource
import pl.voxverum.app.audio.Vad
import pl.voxverum.app.audio.windowed
import pl.voxverum.app.core.AudioStatus
import pl.voxverum.app.core.VoxState
import pl.voxverum.app.ml.Scores
import pl.voxverum.app.ml.WindowAnalyzer
import pl.voxverum.app.risk.RiskAssessment
import pl.voxverum.app.risk.RiskEngine

/**
 * Jedna sesja ochrony = jedna rozmowa (albo jeden plik w trybie symulacji).
 * Czyta dźwięk ze [source], tnie na okna, odrzuca ciszę, puszcza okna mowy przez oba modele
 * i wygładza wynik w [RiskEngine]. Dźwięk żyje tylko w pamięci i znika razem z sesją.
 */
class ProtectionSession(
    private val scope: CoroutineScope,
    private val source: AudioSource,
    private val analyzer: WindowAnalyzer,
    private val debugDumper: DebugDumper? = null,
    private val onAssessment: (RiskAssessment, Scores) -> Unit = { _, _ -> },
    private val onFinished: (RiskAssessment) -> Unit = {},
) {
    private val engine = RiskEngine(analyzer.voiceClone.threshold, analyzer.scam.threshold)
    private var job: Job? = null
    private var silencedJob: Job? = null
    private var latest = RiskAssessment()

    fun start() {
        if (job != null) return
        VoxState.updateAudio { AudioStatus(running = true, sourceName = source.name) }
        VoxState.setRisk(latest)
        if (source is MicAudioSource) {
            silencedJob = scope.launch {
                source.silenced.collect { silenced -> VoxState.updateAudio { it.copy(silenced = silenced) } }
            }
        }
        job = scope.launch {
            try {
                source.frames()
                    .onEach { chunk ->
                        debugDumper?.add(chunk)
                        val level = Vad.rmsDb(chunk)
                        VoxState.updateAudio { it.copy(levelDb = level, sourceName = source.name) }
                    }
                    .windowed(WINDOW_SAMPLES, HOP_SAMPLES)
                    .collect { window ->
                        val speech = Vad.isSpeech(window, source.sampleRate)
                        VoxState.updateAudio { it.copy(speech = speech, windows = it.windows + 1) }
                        if (speech) analyze(window)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Session failed", e)
                VoxState.updateAudio { it.copy(error = e.message ?: e.toString()) }
            } finally {
                debugDumper?.flush()
                VoxState.updateAudio { it.copy(running = false, levelDb = Vad.SILENCE_DB, speech = false) }
                onFinished(latest)
            }
        }
    }

    fun stop() {
        job?.cancel()
        silencedJob?.cancel()
        job = null
        silencedJob = null
    }

    private suspend fun analyze(window: FloatArray) {
        val scores = analyzer.analyze(window)
        latest = engine.update(scores.clone, scores.scam)
        VoxState.setScores(scores)
        VoxState.setRisk(latest)
        onAssessment(latest, scores)
    }

    companion object {
        private const val TAG = "VoxVerum"
        const val SAMPLE_RATE = MicAudioSource.SAMPLE_RATE
        /** Okno 4 s z krokiem 1 s: wynik odświeża się co sekundę. */
        const val WINDOW_SAMPLES = SAMPLE_RATE * 4
        const val HOP_SAMPLES = SAMPLE_RATE
    }
}
