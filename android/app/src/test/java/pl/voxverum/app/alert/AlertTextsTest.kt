package pl.voxverum.app.alert

import org.junit.Assert.assertEquals
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
    fun `silenced mic overrides risk`() {
        val m = AlertTexts.overlay(risk(RiskLevel.DANGER, RiskLevel.OK), silenced = true, mockModels = false)
        assertEquals("Analiza wstrzymana", m.title)
    }

    @Test
    fun `danger maps to red with matching message`() {
        assertEquals(Tone.DANGER, AlertTexts.overlay(risk(RiskLevel.DANGER, RiskLevel.OK), false, false).tone)
        assertTrue(AlertTexts.overlay(risk(RiskLevel.OK, RiskLevel.DANGER), false, false).title.contains("wyłudzenia"))
        assertTrue(AlertTexts.overlay(risk(RiskLevel.DANGER, RiskLevel.DANGER), false, false).title.contains("i próba"))
    }

    @Test
    fun `warn and ok show percentages and mock marker`() {
        val warn = AlertTexts.overlay(risk(RiskLevel.WARN, RiskLevel.OK), false, mockModels = true)
        assertEquals(Tone.WARN, warn.tone)
        assertTrue(warn.detail.contains("Klon: 50%"))
        assertTrue(warn.detail.endsWith("(modele testowe)"))
        assertEquals(Tone.OK, AlertTexts.overlay(risk(RiskLevel.OK, RiskLevel.OK), false, false).tone)
    }
}
