package pl.voxverum.app.alert

import pl.voxverum.app.overlay.OverlayModel
import pl.voxverum.app.overlay.Tone
import pl.voxverum.app.risk.RiskAssessment
import pl.voxverum.app.risk.RiskLevel

/**
 * Teksty ostrzeżeń. Zgodnie z biznesplanem: konkretne kroki zamiast straszenia,
 * a detektor to sygnał pomocniczy, więc zawsze odsyłamy do weryfikacji „poza głosem”.
 * Bez procentów: wyniki modeli nie są skalibrowanym prawdopodobieństwem (patrz INTEGRATION.md).
 */
object AlertTexts {
    /**
     * [error] to błąd nasłuchu (np. brak uprawnienia do mikrofonu); wtedy nie udajemy, że chronimy.
     * [cloneMissing]: w aplikacji nie ma modelu klonu, więc „brak oznak klonu” byłoby nieprawdą.
     */
    fun overlay(
        risk: RiskAssessment?,
        silenced: Boolean,
        mockModels: Boolean,
        error: String? = null,
        cloneMissing: Boolean = false,
    ): OverlayModel {
        val suffix = if (mockModels) " (modele testowe)" else ""
        // Zatrzaśnięty alarm zostaje na wierzchu, nawet gdy potem nasłuch się wyłożył albo system
        // wyciszył mikrofon: ostrzeżenie dotyczy tej samej rozmowy i nadal jest aktualne.
        if (risk?.overall == RiskLevel.DANGER) return assessment(risk, suffix, cloneMissing)
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
        return assessment(risk, suffix, cloneMissing)
    }

    private fun assessment(risk: RiskAssessment, suffix: String, cloneMissing: Boolean): OverlayModel {
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
                "Zapytaj o Kod Rozmowy albo hasło rodzinne.$suffix",
            )
            cloneMissing -> OverlayModel(
                Tone.NEUTRAL,
                "Wykrywanie klonu niedostępne",
                "Ta wersja aplikacji nie ma modelu klonu głosu. Przy prośbie o pieniądze zapytaj o Kod Rozmowy.$suffix",
            )
            // Nie na zielono: brak oznak klonu nie potwierdza tożsamości, a detektor nie łapie
            // każdego generatora (klon XTTS dostaje wynik bliski 0, patrz docs/testy-klonu.md).
            else -> OverlayModel(
                Tone.NEUTRAL,
                "Brak oznak klonu głosu",
                "To nie potwierdza, kto mówi. Przy prośbie o pieniądze zapytaj o Kod Rozmowy.$suffix",
            )
        }
    }

    fun notificationTitle(risk: RiskAssessment): String = overlay(risk, silenced = false, mockModels = false).title

    fun notificationText(risk: RiskAssessment): String = overlay(risk, silenced = false, mockModels = false).detail
}
