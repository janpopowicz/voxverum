package pl.voxverum.app.sim

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import pl.voxverum.app.audio.AudioSource
import pl.voxverum.app.audio.WavIo
import java.io.InputStream

/**
 * Tryb symulacji: podaje plik WAV do pipeline'u tak, jakby leciał z mikrofonu.
 * Przy [realtime] = true zachowuje tempo nagrania (wynik co ~1 s jak w rozmowie),
 * przy false przetwarza plik najszybciej, jak się da (testy, porównanie modeli).
 */
class WavAudioSource(
    private val open: () -> InputStream,
    override val name: String = "WAV",
    private val realtime: Boolean = true,
) : AudioSource {
    override val sampleRate = TARGET_RATE

    override fun frames(): Flow<FloatArray> = flow {
        val wav = open().use { WavIo.read(it) }
        val samples = Resampler.linear(wav.samples, wav.sampleRate, TARGET_RATE)
        var pos = 0
        while (pos < samples.size) {
            val end = minOf(pos + CHUNK, samples.size)
            emit(samples.copyOfRange(pos, end))
            pos = end
            if (realtime) delay(CHUNK * 1000L / TARGET_RATE)
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val TARGET_RATE = 16_000
        const val CHUNK = TARGET_RATE / 10
    }
}

object Resampler {
    /** Interpolacja liniowa. Wystarcza do symulacji; modele i tak dostają 16 kHz. */
    fun linear(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input
        val outSize = (input.size.toLong() * toRate / fromRate).toInt()
        val ratio = fromRate.toDouble() / toRate
        return FloatArray(outSize) { i ->
            val src = i * ratio
            val idx = src.toInt()
            val frac = (src - idx).toFloat()
            val a = input[idx]
            val b = input[minOf(idx + 1, input.size - 1)]
            a + (b - a) * frac
        }
    }
}
