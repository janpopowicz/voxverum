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

/**
 * Jedna sesja ochrony = jedna rozmowa (albo jeden plik w trybie symulacji).
 * Czyta dźwięk ze [source], tnie na okna, odrzuca ciszę i przekazuje okna mowy do [onSpeechWindow].
 * Dźwięk żyje tylko w pamięci i znika razem z sesją.
 */
class ProtectionSession(
    private val scope: CoroutineScope,
    private val source: AudioSource,
    private val debugDumper: DebugDumper? = null,
    private val onSpeechWindow: suspend (FloatArray) -> Unit = {},
) {
    private var job: Job? = null
    private var silencedJob: Job? = null

    fun start() {
        if (job != null) return
        VoxState.updateAudio { AudioStatus(running = true, sourceName = source.name) }
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
                        if (speech) onSpeechWindow(window)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Session failed", e)
                VoxState.updateAudio { it.copy(error = e.message ?: e.toString()) }
            } finally {
                debugDumper?.flush()
                VoxState.updateAudio { it.copy(running = false, levelDb = Vad.SILENCE_DB, speech = false) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        silencedJob?.cancel()
        job = null
        silencedJob = null
    }

    companion object {
        private const val TAG = "VoxVerum"
        const val SAMPLE_RATE = MicAudioSource.SAMPLE_RATE
        /** Okno 4 s z krokiem 1 s: wynik odświeża się co sekundę. */
        const val WINDOW_SAMPLES = SAMPLE_RATE * 4
        const val HOP_SAMPLES = SAMPLE_RATE
    }
}
