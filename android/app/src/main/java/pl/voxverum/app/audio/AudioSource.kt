package pl.voxverum.app.audio

import kotlinx.coroutines.flow.Flow

/**
 * Źródło próbek mono float (−1…1). Mikrofon i plik WAV (tryb symulacji) dają ten sam
 * strumień, więc reszta pipeline'u nie wie, skąd pochodzi dźwięk.
 */
interface AudioSource {
    val sampleRate: Int
    val name: String

    /** Zimny strumień: nagrywanie startuje przy `collect` i kończy się przy anulowaniu. */
    fun frames(): Flow<FloatArray>
}
