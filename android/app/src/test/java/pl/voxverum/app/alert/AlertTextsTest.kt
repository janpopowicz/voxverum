package pl.voxverum.app.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.voxverum.app.overlay.Tone
import pl.voxverum.app.risk.ModelRisk
import pl.voxverum.app.risk.RiskAssessment
import pl.voxverum.app.risk.RiskLevel

class AlertTextsTest {
    private fun risk(clone: RiskLevel, scam: RiskLevel) =
        RiskAssessment(ModelRisk(clone, 0.5f), ModelRisk(scam, 0.2f), windows = 3)

    @Test
    fun `no assessment yet asks for speakerphone`() {
        val m = AlertTexts.overlay(null, silenced = false, mockModels = false)
        assertEquals(Tone.NEUTRAL, m.tone)
        assertTrue(m.detail.contains("głośnik"))
    }

    @Test
    fun `silenced mic overrides ok and warn`() {
        val m = AlertTexts.overlay(risk(RiskLevel.WARN, RiskLevel.OK), silenced = true, mockModels = false)
        assertEquals("Analiza wstrzymana", m.title)
    }

    @Test
    fun `current danger stays on top of silenced mic and capture error`() {
        val danger = risk(RiskLevel.DANGER, RiskLevel.OK)
        assertEquals(Tone.DANGER, AlertTexts.overlay(danger, silenced = true, mockModels = false).tone)
        assertEquals(Tone.DANGER, AlertTexts.overlay(danger, false, false, error = "AudioRecord").tone)
    }

    @Test
    fun `missing clone model does not claim there is no clone`() {
        val m = AlertTexts.overlay(risk(RiskLevel.OK, RiskLevel.OK), false, false, cloneMissing = true)
        assertEquals("Wykrywanie klonu niedostępne", m.title)
        assertFalse(m.title.contains("Brak oznak"))
    }

    @Test
    fun `capture error is shown instead of pretending to listen`() {
        val m = AlertTexts.overlay(null, silenced = false, mockModels = false, error = "Brak uprawnienia RECORD_AUDIO")
        assertEquals("Analiza nie działa", m.title)
        assertTrue(m.detail.contains("RECORD_AUDIO"))
    }

    @Test
    fun `danger maps to red with matching message`() {
        assertEquals(Tone.DANGER, AlertTexts.overlay(risk(RiskLevel.DANGER, RiskLevel.OK), false, false).tone)
        assertTrue(AlertTexts.overlay(risk(RiskLevel.OK, RiskLevel.DANGER), false, false).title.contains("wyłudzenia"))
        assertTrue(AlertTexts.overlay(risk(RiskLevel.DANGER, RiskLevel.DANGER), false, false).title.contains("i próba"))
    }

    @Test
    fun `warn and ok show no percentages but mark mock models`() {
        val warn = AlertTexts.overlay(risk(RiskLevel.WARN, RiskLevel.OK), false, mockModels = true)
        assertEquals(Tone.WARN, warn.tone)
        assertFalse("wyniki nie są skalibrowane", warn.detail.contains("%"))
        assertTrue(warn.detail.endsWith("(modele testowe)"))
        // Bez zieleni: brak oznak klonu nie potwierdza tożsamości.
        val ok = AlertTexts.overlay(risk(RiskLevel.OK, RiskLevel.OK), false, false)
        assertEquals(Tone.NEUTRAL, ok.tone)
        assertTrue(ok.detail.contains("innym kanałem"))
    }

    @Test
    fun `warnings do not require a conversation code or family password`() {
        for (clone in RiskLevel.entries) for (scam in RiskLevel.entries) {
            val detail = AlertTexts.overlay(risk(clone, scam), false, false).detail
            assertFalse(detail.contains("Kod Rozmowy"))
            assertFalse(detail.contains("hasło rodzinne"))
        }
    }
}
