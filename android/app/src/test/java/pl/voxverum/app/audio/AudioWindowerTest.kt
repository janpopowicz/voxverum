package pl.voxverum.app.audio

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioWindowerTest {
    private fun ramp(from: Int, until: Int) = FloatArray(until - from) { (from + it).toFloat() }

    @Test
    fun `emits first window when full, then every hop`() {
        val windower = AudioWindower(windowSize = 4, hopSize = 2)
        val out = mutableListOf<FloatArray>()
        windower.push(ramp(0, 8)) { out += it }

        assertEquals(3, out.size)
        assertArrayEquals(floatArrayOf(0f, 1f, 2f, 3f), out[0], 0f)
        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f), out[1], 0f)
        assertArrayEquals(floatArrayOf(4f, 5f, 6f, 7f), out[2], 0f)
    }

    @Test
    fun `chunk boundaries do not change the windows`() = runTest {
        val whole = flowOf(ramp(0, 10)).windowed(4, 3).toList()
        val split = flowOf(ramp(0, 3), ramp(3, 7), ramp(7, 10)).windowed(4, 3).toList()

        assertEquals(whole.size, split.size)
        whole.zip(split).forEach { (a, b) -> assertArrayEquals(a, b, 0f) }
    }

    @Test
    fun `clear resets state`() {
        val windower = AudioWindower(windowSize = 3, hopSize = 1)
        windower.push(ramp(0, 2)) { }
        windower.clear()
        val out = mutableListOf<FloatArray>()
        windower.push(ramp(10, 13)) { out += it }

        assertEquals(1, out.size)
        assertArrayEquals(floatArrayOf(10f, 11f, 12f), out[0], 0f)
    }
}
