package pl.voxverum.app.risk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RiskEngineTest {
    private fun engine() = RiskEngine(cloneThreshold = 0.8f, scamThreshold = 0.8f)

    @Test
    fun `low scores stay ok`() {
        val e = engine()
        repeat(5) { assertEquals(RiskLevel.OK, e.update(0.1f, 0.1f).overall) }
    }

    @Test
    fun `single spike warns but does not alarm`() {
        val e = engine()
        e.update(0.1f, 0.1f)
        val r = e.update(0.95f, 0.1f)
        assertEquals(RiskLevel.WARN, r.clone.level)
        assertEquals(RiskLevel.OK, r.scam.level)
    }

    @Test
    fun `one short sound seen by three overlapping windows only warns`() {
        val e = engine()
        // Trzask w jednym miejscu nagrania trafia w 3 kolejne okna (model widzi ostatnie 3 s).
        repeat(3) { assertEquals(RiskLevel.WARN, e.update(0.95f, 0.1f).clone.level) }
        repeat(6) { assertNotEquals(RiskLevel.DANGER, e.update(0.0f, 0.1f).clone.level) }
        assertEquals(RiskLevel.OK, e.update(0.0f, 0.1f).clone.level)
    }

    @Test
    fun `sustained clone is danger after four windows and it latches`() {
        val e = engine()
        repeat(3) { assertEquals(RiskLevel.WARN, e.update(0.9f, 0.1f).clone.level) }
        assertEquals(RiskLevel.DANGER, e.update(0.9f, 0.1f).clone.level)
        repeat(10) { assertEquals(RiskLevel.DANGER, e.update(0.0f, 0.1f).clone.level) }
    }

    @Test
    fun `two separate hits are danger`() {
        val e = engine()
        e.update(0.9f, 0.1f)
        e.update(0.1f, 0.1f)
        e.update(0.1f, 0.1f)
        assertEquals(RiskLevel.DANGER, e.update(0.9f, 0.1f).clone.level)
    }

    @Test
    fun `warn releases only after ema drops below release ratio`() {
        val e = engine()
        assertEquals(RiskLevel.WARN, e.update(0.1f, 0.85f).scam.level)
        // Okno ≥ progu wciąż jest w historii (3 okna), więc WARN trzyma się przez kolejne dwa.
        assertEquals(RiskLevel.WARN, e.update(0.1f, 0.5f).scam.level)
        assertEquals(RiskLevel.WARN, e.update(0.1f, 0.3f).scam.level)
        // Wypadło z historii; EMA ok. 0.33 < 0.4 (= 0.5·0.8), więc schodzimy do OK.
        assertEquals(RiskLevel.OK, e.update(0.1f, 0.0f).scam.level)
    }

    @Test
    fun `overall is the worse of both models`() {
        val e = engine()
        repeat(3) { e.update(0.1f, 0.9f) }
        val r = e.update(0.1f, 0.9f)
        assertEquals(RiskLevel.OK, r.clone.level)
        assertEquals(RiskLevel.DANGER, r.overall)
        assertEquals(4, r.windows)
    }
}
