package pl.voxverum.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class VadTest {
    private val sr = 16_000

    private fun sine(amplitude: Float, seconds: Float = 1f) =
        FloatArray((sr * seconds).toInt()) { (amplitude * sin(2 * PI * 220 * it / sr)).toFloat() }

    @Test
    fun `silence is not speech`() {
        assertFalse(Vad.isSpeech(FloatArray(sr), sr))
        assertEquals(Vad.SILENCE_DB, Vad.rmsDb(FloatArray(100)), 0f)
    }

    @Test
    fun `loud tone counts as speech`() {
        assertTrue(Vad.isSpeech(sine(0.3f), sr))
    }

    @Test
    fun `very quiet tone is below threshold`() {
        assertFalse(Vad.isSpeech(sine(0.001f), sr))
    }

    @Test
    fun `full scale sine rms is about minus 3 dB`() {
        assertEquals(-3f, Vad.rmsDb(sine(1f)), 0.1f)
    }
}
