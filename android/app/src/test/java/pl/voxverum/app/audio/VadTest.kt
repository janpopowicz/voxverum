package pl.voxverum.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class VadTest {
    private val sr = 16_000
    private val n = sr * 3

    private fun sine(amplitude: Float, hz: Double = 220.0, seconds: Float = 3f) =
        FloatArray((sr * seconds).toInt()) { (amplitude * sin(2 * PI * hz * it / sr)).toFloat() }

    /**
     * Coś na kształt mowy: odcinki 60–200 ms z harmonicznymi zmieniającej się częstotliwości
     * podstawowej (90–260 Hz), przerwy między „sylabami” i szum tła.
     */
    private fun speechLike(seed: Int = 1): FloatArray {
        val rnd = Random(seed)
        val out = FloatArray(n)
        var pos = 0
        while (pos < n) {
            val len = sr * (60 + rnd.nextInt(140)) / 1000
            val voiced = rnd.nextFloat() < 0.8f
            val f0 = 90.0 + rnd.nextDouble() * 170
            val glide = (rnd.nextDouble() - 0.5) * 60
            val amp = 0.05f + rnd.nextFloat() * 0.2f
            for (i in 0 until len) {
                if (pos + i >= n) break
                val t = i.toDouble() / sr
                val f = f0 + glide * t / (len.toDouble() / sr)
                var s = 0.0
                if (voiced) for (h in 1..12) s += sin(2 * PI * f * h * t) / h
                out[pos + i] = (amp * s * 0.3).toFloat()
            }
            pos += len + sr * rnd.nextInt(80) / 1000
        }
        for (i in out.indices) out[i] += (rnd.nextFloat() - 0.5f) * 1e-3f
        return out
    }

    private fun mix(a: FloatArray, b: FloatArray) = FloatArray(a.size) { a[it] + b[it] }

    @Test
    fun `silence is not speech`() {
        assertFalse(Vad.isSpeech(FloatArray(n), sr))
        assertEquals(Vad.SILENCE_DB, Vad.rmsDb(FloatArray(100)), 0f)
    }

    @Test
    fun `very quiet sound is below threshold`() {
        assertFalse(Vad.isSpeech(sine(0.001f), sr))
    }

    @Test
    fun `speech like signal goes to the model`() {
        for (seed in 1..5) {
            val r = Vad.analyze(speechLike(seed), sr)
            assertTrue("seed $seed: $r", r.isSpeech)
        }
    }

    @Test
    fun `steady tone is not speech`() {
        // Sygnał wołania i brum Mother Voice ocenia jako klon z wynikiem 1,0: nie mogą trafić do modelu.
        val ring = sine(0.2f, hz = 425.0)
        val r = Vad.analyze(ring, sr)
        assertFalse(r.isSpeech)
        assertEquals(1f, r.toneRatio, 0.05f)
    }

    @Test
    fun `ringback beeps are rejected`() {
        val beeps = sine(0.2f, hz = 425.0).also { for (i in sr until it.size) it[i] = 0f }
        assertFalse(Vad.isSpeech(beeps, sr))
    }

    @Test
    fun `mains hum is rejected`() {
        val hum = FloatArray(n) { (0.01 * sin(2 * PI * 50 * it / sr) + 0.005 * sin(2 * PI * 150 * it / sr)).toFloat() }
        assertFalse(Vad.isSpeech(hum, sr))
    }

    @Test
    fun `speech over a tone is rejected`() {
        val beeps = sine(0.2f, hz = 425.0).also { for (i in sr until it.size) it[i] = 0f }
        assertFalse(Vad.isSpeech(mix(speechLike(), beeps), sr))
    }

    @Test
    fun `full scale sine rms is about minus 3 dB`() {
        assertEquals(-3f, Vad.rmsDb(sine(1f)), 0.1f)
    }

    @Test
    fun `fft finds the right bin`() {
        val re = DoubleArray(512) { sin(2 * PI * 32 * it / 512) }
        val im = DoubleArray(512)
        Vad.fft(re, im)
        val power = DoubleArray(257) { re[it] * re[it] + im[it] * im[it] }
        assertEquals(32, power.indices.maxBy { power[it] })
        assertEquals(256.0, abs(im[32]), 1e-6)
    }
}
