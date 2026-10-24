package pl.voxverum.app.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreMathTest {
    @Test
    fun `softmax of logits picks positive class`() {
        val p = ScoreMath.toProbability(floatArrayOf(0f, 0f), OutputType.LOGITS, positiveIndex = 1)
        assertEquals(0.5f, p, 1e-6f)
        val high = ScoreMath.toProbability(floatArrayOf(-2f, 3f), OutputType.LOGITS, positiveIndex = 1)
        assertEquals(0.9933f, high, 1e-3f)
    }

    @Test
    fun `single logit uses sigmoid`() {
        assertEquals(0.5f, ScoreMath.toProbability(floatArrayOf(0f), OutputType.LOGITS, 0), 1e-6f)
    }

    @Test
    fun `probability is clamped`() {
        assertEquals(1f, ScoreMath.toProbability(floatArrayOf(1.2f), OutputType.PROBABILITY, 0), 0f)
        assertEquals(0.3f, ScoreMath.toProbability(floatArrayOf(0.7f, 0.3f), OutputType.PROBABILITY, 1), 0f)
    }

    @Test
    fun `softmax is numerically stable`() {
        val p = ScoreMath.softmax(floatArrayOf(1000f, 1000f))
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), p, 1e-6f)
    }

    @Test
    fun `tail takes last samples and left pads`() {
        assertArrayEquals(floatArrayOf(3f, 4f), ScoreMath.tail(floatArrayOf(1f, 2f, 3f, 4f), 2), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, 1f, 2f), ScoreMath.tail(floatArrayOf(1f, 2f), 4), 0f)
    }
}
