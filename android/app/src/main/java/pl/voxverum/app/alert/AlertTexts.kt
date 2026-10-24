package pl.voxverum.app.alert

import pl.voxverum.app.overlay.OverlayModel
import pl.voxverum.app.overlay.Tone
import pl.voxverum.app.risk.RiskAssessment
import pl.voxverum.app.risk.RiskLevel
import kotlin.math.roundToInt

/**
 * Teksty ostrzeżeń. Zgodnie z biznesplanem: konkretne kroki zamiast straszenia,
 * a detektor to sygnał pomocniczy, więc zawsze odsyłamy do weryfikacji „poza głosem”.
 */
object AlertTexts {
    /** [error] to błąd nasłuchu (np. brak uprawnienia do mikrofonu); wtedy nie udajemy, że chronimy. */
    fun overlay(risk: RiskAssessment?, silenced: Boolean, mockModels: Boolean, error: String? = null): OverlayModel {
        val suffix = if (mockModels) " (modele testowe)" else ""
        if (error != null) {
            return OverlayModel(
                Tone.WARN,
                "Analiza nie działa",
                "Nie udało się słuchać rozmowy: $error. Otwórz VoxVerum i sprawdź uprawnienia.",
            )
        }
        if (silenced) {
            return OverlayModel(
                Tone.NEUTRAL,
                "Analiza wstrzymana",
                "System wyciszył mikrofon dla VoxVerum. Zostaw ten baner widoczny.$suffix",
            )
        }
        if (risk == null || risk.overall == RiskLevel.UNKNOWN) {
            return OverlayModel(
                Tone.NEUTRAL,
                "VoxVerum słucha rozmowy",
                "Włącz głośnik, żeby analiza słyszała rozmówcę.$suffix",
            )
        }
        val clone = risk.clone.level
        val scam = risk.scam.level
        return when {
            clone == RiskLevel.DANGER && scam == RiskLevel.DANGER -> OverlayModel(
                Tone.DANGER,
                "Sklonowany głos i próba wyłudzenia!",
                "Rozłącz się. Oddzwoń na znany numer i zapytaj o hasło rodzinne.$suffix",
            )
            clone == RiskLevel.DANGER -> OverlayModel(
                Tone.DANGER,
                "Głos może być sklonowany!",
                "Zapytaj o Kod Rozmowy albo hasło rodzinne. W razie wątpliwości rozłącz się i oddzwoń.$suffix",
            )
            scam == RiskLevel.DANGER -> OverlayModel(
                Tone.DANGER,
                "Możliwa próba wyłudzenia!",
                "Nie przelewaj pieniędzy i nie podawaj kodów BLIK. Rozłącz się i oddzwoń.$suffix",
            )
            risk.overall == RiskLevel.WARN -> OverlayModel(
                Tone.WARN,
                "Uwaga: coś się nie zgadza",
                "Zapytaj o Kod Rozmowy. ${percentages(risk)}$suffix",
            )
            else -> OverlayModel(
                Tone.OK,
                "Nie wykryto zagrożenia",
                "${percentages(risk)} Głos to nie dowód tożsamości.$suffix",
            )
        }
    }

    fun notificationTitle(risk: RiskAssessment): String = overlay(risk, silenced = false, mockModels = false).title

    fun notificationText(risk: RiskAssessment): String = overlay(risk, silenced = false, mockModels = false).detail

    private fun percentages(risk: RiskAssessment): String =
        "Klon: ${pct(risk.clone.smoothed)} · Wyłudzenie: ${pct(risk.scam.smoothed)}."

    private fun pct(p: Float) = "${(p * 100).roundToInt()}%"
}
