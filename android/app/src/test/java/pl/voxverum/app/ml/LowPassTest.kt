package pl.voxverum.app.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class LowPassTest {
    @Test
    fun `impulse response matches scipy butter 4 at 7 kHz`() {
        // scipy.signal.sosfilt(butter(4, 7000, "lowpass", fs=16000, output="sos"), impuls)
        val expected = floatArrayOf(
            0.59630237f, 0.61011014f, -0.27912f, 0.0630609f, 0.06206152f, -0.11896471f, 0.12836096f, -0.10824804f,
            0.07336263f, -0.03489601f, 0.00051066f, 0.02536188f, -0.04100409f, 0.04676274f, -0.04435731f, 0.03622779f,
        )
        val impulse = FloatArray(16).also { it[0] = 1f }
        assertArrayEquals(expected, LowPass(7000f, 16_000).apply(impulse), 1e-6f)
    }

    @Test
    fun `passes speech band and cuts the top band`() {
        val filter = LowPass(7000f, 16_000)
        assertEquals(1.0, gain(filter, 1000.0), 0.01)
        assertEquals(true, gain(filter, 7800.0) < 0.1)
    }

    private fun gain(filter: LowPass, hz: Double): Double {
        val n = 16_000
        val x = FloatArray(n) { sin(2 * PI * hz * it / n).toFloat() }
        val y = filter.apply(x)
        // Pomijamy początek, gdzie filtr się rozpędza.
        return rms(y.copyOfRange(1000, n)) / rms(x.copyOfRange(1000, n))
    }

    private fun rms(x: FloatArray) = sqrt(x.sumOf { it.toDouble() * it } / x.size)
}
