package pl.voxverum.app.sim

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ResamplerTest {
    @Test
    fun `same rate returns input`() {
        val input = floatArrayOf(1f, 2f)
        assertSame(input, Resampler.linear(input, 16_000, 16_000))
    }

    @Test
    fun `downsampling by two keeps every other sample`() {
        val out = Resampler.linear(floatArrayOf(0f, 1f, 2f, 3f), 32_000, 16_000)
        assertArrayEquals(floatArrayOf(0f, 2f), out, 1e-6f)
    }

    @Test
    fun `upsampling interpolates`() {
        val out = Resampler.linear(floatArrayOf(0f, 2f), 8_000, 16_000)
        assertEquals(4, out.size)
        assertArrayEquals(floatArrayOf(0f, 1f, 2f, 2f), out, 1e-6f)
    }
}
